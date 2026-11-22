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

@Service
@RequiredArgsConstructor
public class OrderCodSettlementService {
    private final OrderRepository orders;
    private final PaymentFacade payments;
    private final OrderFacade facade;
    private final ApplicationEventPublisher events;

    public record Settlement(BigDecimal amount, String reference, String reason) {}

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
