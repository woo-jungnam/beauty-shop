package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.api.dto.SpaRefundBalance;
import com.core.beautyshop.modules.order.api.event.OrderEvents.OrderFinancialAdjustmentEvent;
import com.core.beautyshop.modules.order.application.dto.request.ConfirmRefundRequest;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.OrderStatusHistory;
import com.core.beautyshop.modules.order.domain.RefundConfirmation;
import com.core.beautyshop.modules.order.domain.RefundConfirmationRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.spa.api.TreatmentOrderPolicy;
import com.core.beautyshop.shared.audit.api.annotation.AuditAction;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderRefundService {
    private final OrderRepository orders;
    private final RefundConfirmationRepository confirmations;
    private final TreatmentOrderPolicy treatmentOrderPolicy;

    @Autowired
    private ApplicationEventPublisher events;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SpaRefundBalance lockSpaBalance(Long id) {
        Order order = orders.findByIdForUpdate(id).filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn Spa"));
        if (order.getAppointmentId() == null && order.getServicePackageId() == null)
            throw new BusinessException("Chỉ áp dụng cho đơn dịch vụ Spa");
        if (order.getServicePackageId() != null && treatmentOrderPolicy.requiresTreatmentWorkflow(id))
            throw new BusinessException("Lộ trình điều trị phải quyết toán qua hồ sơ điều trị");
        return new SpaRefundBalance(id, order.getUserId(), order.getAppointmentId(),
                order.getServicePackageId(), order.getTotalAmount(), order.getPaidAmount(), order.getRefundedAmount());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void confirmSpaServiceRefund(Long id, BigDecimal amount, String reference, String reason) {
        var balance = lockSpaBalance(id);
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
                || reference == null || reference.isBlank() || reference.length() > 100
                || reason == null || reason.isBlank() || reason.length() > 250)
            throw new BusinessException("Số tiền nguyên VND, chứng từ và lý do hoàn tiền là bắt buộc");
        var previous = confirmations.findByReference(reference);
        if (previous.isPresent()) throw new BusinessException("Chứng từ đã được sử dụng; kiểm tra giao dịch hoàn đã ghi nhận");
        if (amount.compareTo(balance.paidAmount().subtract(balance.refundedAmount())) > 0)
            throw new BusinessException("Số hoàn vượt tiền thực thu chưa hoàn");
        Order order = orders.findByIdForUpdate(id).orElseThrow();
        var row = new RefundConfirmation();
        row.setOrderId(id);
        row.setAmount(amount);
        row.setReference(reference);
        row.setApprovalReason(reason);
        row.setConfirmedByUserId(SecurityUtils.getCurrentUserId());
        confirmations.saveAndFlush(row);
        order.setRefundedAmount(order.getRefundedAmount().add(amount));
        order.setRefundReference(reference);
        if (order.getRefundedAmount().compareTo(order.getPaidAmount()) == 0) order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.getStatusHistories().add(OrderStatusHistory.builder().order(order).status(order.getStatus())
                .notes("Spa service refund: " + reference + "; amount=" + amount + "; reason=" + reason).build());
        orders.save(order);
        publishFinancialAdjustment(order);
    }

    @Transactional
    public void confirmTreatmentRefund(Long orderId, BigDecimal amount, String reference, String reason) {
        if (!SecurityUtils.isAdmin())
            throw new AccessDeniedException("Only administrators confirm refunds");
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || reference == null || reference.isBlank()
                || reference.length() > 100 || reason == null || reason.isBlank() || reason.length() > 250)
            throw new BusinessException("Invalid treatment refund confirmation");
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        var previous = confirmations.findByReference(reference);
        if (previous.isPresent()) {
            if (!orderId.equals(previous.get().getOrderId()) || amount.compareTo(previous.get().getAmount()) != 0)
                throw new BusinessException("Refund reference already belongs to another confirmation");
            return;
        }
        if (order.getServicePackageId() == null || order.getPaidAt() == null
                || amount.compareTo(order.getPaidAmount().subtract(order.getRefundedAmount())) > 0)
            throw new BusinessException("Refund exceeds the remaining amount actually received");
        var row = new RefundConfirmation();
        row.setOrderId(orderId);
        row.setAmount(amount);
        row.setReference(reference);
        row.setApprovalReason(reason);
        row.setConfirmedByUserId(SecurityUtils.getCurrentUserId());
        confirmations.saveAndFlush(row);
        order.setRefundedAmount(order.getRefundedAmount().add(amount));
        order.setRefundReference(reference);
        if (order.getRefundedAmount().compareTo(order.getPaidAmount()) == 0) order.setPaymentStatus(PaymentStatus.REFUNDED);
        orders.save(order);
        publishFinancialAdjustment(order);
    }

    @Transactional
    public void confirmTreatmentRefund(List<Long> orderIds, BigDecimal amount, String reference, String reason) {
        if (!SecurityUtils.isAdmin())
            throw new AccessDeniedException("Only administrators confirm refunds");
        if (orderIds == null || orderIds.isEmpty() || amount == null || amount.signum() <= 0 || amount.scale() > 2
                || reference == null || reference.isBlank() || reference.length() > 100 || reason == null || reason.isBlank() || reason.length() > 250)
            throw new BusinessException("Invalid treatment refund confirmation");
        List<Order> sources = orderIds.stream().distinct().sorted().map(id -> orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund source order not found"))).toList();
        var previous = confirmations.findByReference(reference);
        if (previous.isPresent()) throw new BusinessException("Refund reference already belongs to an earlier confirmation");
        BigDecimal available = sources.stream().filter(o -> o.getServicePackageId() != null && o.getPaidAt() != null)
                .map(o -> o.getPaidAmount().subtract(o.getRefundedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(available) > 0) throw new BusinessException("Refund exceeds the remaining amount actually received");
        BigDecimal remaining = amount;
        int index = 0;
        for (Order source : sources) {
            if (remaining.signum() == 0) break;
            BigDecimal part = remaining.min(source.getPaidAmount().subtract(source.getRefundedAmount()));
            if (part.signum() <= 0) continue;
            String partReference = index++ == 0 ? reference : refundPartReference(reference, source.getId());
            confirmTreatmentRefund(source.getId(), part, partReference, reason);
            remaining = remaining.subtract(part);
        }
        if (remaining.signum() != 0) throw new BusinessException("Refund source amounts do not reconcile");
    }

    private String refundPartReference(String reference, Long orderId) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest((reference + ":" + orderId).getBytes(StandardCharsets.UTF_8));
            return "TREATMENT-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @AuditAction(action = "CONFIRM_REFUND", resourceType = "ORDER")
    @Transactional
    public void confirm(Long id, ConfirmRefundRequest request) {
        if (request == null || request.reference() == null || request.reference().isBlank() || request.reference().length() > 100
                || request.amount() == null || request.amount().signum() <= 0 || request.amount().scale() > 2
                || (request.approvalReason() != null && request.approvalReason().length() > 250))
            throw new BusinessException("Thông tin xác nhận hoàn tiền không hợp lệ");
        Order order = orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        if (order.getServicePackageId() != null && order.getPaidAt() != null && treatmentOrderPolicy.requiresTreatmentWorkflow(id))
            throw new BusinessException("Please confirm this treatment refund through its approved course request");
        var previous = confirmations.findByReference(request.reference());
        if (previous.isPresent()) {
            if (!id.equals(previous.get().getOrderId()) || request.amount().compareTo(previous.get().getAmount()) != 0) {
                throw new BusinessException("Mã tham chiếu hoàn tiền đã được sử dụng cho một xác nhận khác");
            }
            return;
        }
        boolean completedVisit = order.getAppointmentId() != null && order.getStatus() == OrderStatus.COMPLETED;
        boolean explicitVisitRefund = completedVisit && (order.getPaymentStatus() == PaymentStatus.PAID
                || order.getPaymentStatus() == PaymentStatus.PENDING || order.getPaymentStatus() == PaymentStatus.REFUND_PENDING);
        if (explicitVisitRefund && (!SecurityUtils.isAdmin()
                || request.approvalReason() == null || request.approvalReason().isBlank()))
            throw new BusinessException("Hoàn tiền dịch vụ Spa yêu cầu người phê duyệt là quản trị viên và phải có lý do cụ thể");
        if (!explicitVisitRefund && (order.getPaymentStatus() != PaymentStatus.REFUND_PENDING
                || (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.RETURNED
                    && order.getStatus() != OrderStatus.RETURN_TO_SENDER))) {
            throw new BusinessException("Đơn hàng không ở trạng thái chờ hoàn tiền");
        }
        BigDecimal outstanding = order.getPaidAmount().subtract(order.getRefundedAmount());
        if (request.amount().compareTo(outstanding) != 0) {
            throw new BusinessException("Số tiền xác nhận hoàn trả phải khớp chính xác với số tiền cần hoàn");
        }
        var confirmation = new RefundConfirmation();
        confirmation.setOrderId(id);
        confirmation.setReference(request.reference());
        confirmation.setAmount(request.amount());
        confirmation.setConfirmedByUserId(SecurityUtils.getCurrentUserIdOptional().orElse(null));
        confirmation.setApprovalReason(request.approvalReason());
        confirmations.saveAndFlush(confirmation);
        order.setRefundedAmount(order.getPaidAmount());
        order.setRefundReference(request.reference());
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.getStatusHistories().add(OrderStatusHistory.builder().order(order).status(order.getStatus())
                .notes("Refund confirmed: " + request.reference() + "; amount=" + request.amount()
                        + (request.approvalReason() == null ? "" : "; approval=" + request.approvalReason())).build());
        orders.save(order);
        publishFinancialAdjustment(order);
    }

    private void publishFinancialAdjustment(Order order) {
        if (events != null && order != null && order.getUserId() != null) {
            BigDecimal netEligible = order.getTotalAmount()
                    .subtract(order.getRefundedAmount())
                    .max(BigDecimal.ZERO)
                    .min(order.getPaidAmount().subtract(order.getRefundedAmount()).max(BigDecimal.ZERO));
            events.publishEvent(new OrderFinancialAdjustmentEvent(
                    order.getId(), order.getUserId(), netEligible));
        }
    }
}
