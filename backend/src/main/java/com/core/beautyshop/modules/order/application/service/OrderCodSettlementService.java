package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderCodSettlementService {
    private final OrderRepository orders;
    private final PaymentFacade payments;
    private final OrderFacade facade;
    private final ApplicationEventPublisher events;

    public record Settlement(BigDecimal amount, String reference, String reason) {}

    public record BatchSettlement(List<Long> orderIds, String reference, String reason) {}

    public record SettledItem(Long orderId, String orderNumber, BigDecimal amount, String referenceCode) {}

    public record BatchSettlementResult(int settledCount, BigDecimal totalSettledAmount,
                                        String batchReference, List<SettledItem> items) {}

    @Transactional
    public BatchSettlementResult confirmBatch(BatchSettlement command) {
        if (command == null || command.orderIds() == null || command.orderIds().isEmpty()
                || command.reference() == null || command.reference().isBlank()
                || command.reference().trim().length() > 50
                || command.reason() == null || command.reason().isBlank()
                || command.reason().trim().length() > 250) {
            throw new BusinessException("Cần chọn đơn hàng, mã chứng từ đối soát (tối đa 50 ký tự) và nội dung xác nhận");
        }
        List<Long> distinctIds = command.orderIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (distinctIds.isEmpty() || distinctIds.size() > 100) {
            throw new BusinessException("Mỗi đợt đối soát chọn từ 1 đến 100 đơn hàng");
        }
        String batchRef = command.reference().trim();
        String reason = command.reason().trim();
        List<SettledItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : distinctIds) {
            var order = orders.findByIdForUpdate(id)
                    .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted()))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + id));
            BigDecimal due = order.unsettledCod();
            if (due.signum() <= 0) {
                throw new BusinessException("Đơn hàng " + order.getOrderNumber() + " không còn tiền COD chờ bàn giao");
            }
            String itemRef = batchRef + "-" + order.getId();
            confirm(order.getId(), new Settlement(due, itemRef, reason));
            items.add(new SettledItem(order.getId(), order.getOrderNumber(), due, "COD-SETTLED-" + itemRef));
            total = total.add(due);
        }
        return new BatchSettlementResult(items.size(), total, batchRef, items);
    }

    @Transactional
    public void confirm(Long id, Settlement command) {
        if (command == null || command.amount() == null || command.amount().signum() <= 0
                || command.amount().stripTrailingZeros().scale() > 0 || command.reference() == null
                || command.reference().isBlank() || command.reference().length() > 80
                || command.reason() == null || command.reason().isBlank() || command.reason().length() > 250) {
            throw new BusinessException("Cần số tiền nguyên VND, mã chứng từ và nội dung xác nhận");
        }
        var order = orders.findByIdForUpdate(id)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        String reference = "COD-SETTLED-" + command.reference().trim();
        boolean shipperCollection = order.hasShipperCodCollection();
        if (order.getPaymentMethod() != PaymentMethod.COD || order.getAppointmentId() != null
                || order.getServicePackageId() != null || (order.getStatus() != OrderStatus.DELIVERED
                && !(shipperCollection && order.getStatus() == OrderStatus.RETURNED))) {
            throw new BusinessException("Chỉ đối soát COD cho đơn hàng vật lý đã giao");
        }
        boolean created = payments.recordCarrierRemittance(order.getOrderNumber(), command.amount(), reference,
                command.reason(), SecurityUtils.getCurrentUserId());
        if (!created) return;
        if (command.amount().compareTo(order.unsettledCod()) > 0) {
            throw new BusinessException("Số tiền bàn giao vượt số COD còn phải bàn giao");
        }
        if (shipperCollection) {
            BigDecimal remitted = order.getShipperCodRemitted();
            order.setShipperCodRemitted((remitted == null ? BigDecimal.ZERO : remitted).add(command.amount()));
            return;
        }
        facade.markOrderAsPaid(order.getOrderNumber(), command.amount(), reference);
        BigDecimal netPaid = order.getPaidAmount().subtract(order.getRefundedAmount())
                .min(order.getTotalAmount()).max(BigDecimal.ZERO);
        events.publishEvent(new OrderEvents.OrderFinancialAdjustmentEvent(id, order.getUserId(), netPaid));
    }
}
