package com.core.beautyshop.modules.order.application.service;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.order.application.dto.request.ConfirmRefundRequest;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderRefundService {
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public com.core.beautyshop.modules.order.api.dto.SpaRefundBalance lockSpaBalance(Long id) {
        Order order = orders.findByIdForUpdate(id).filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn Spa"));
        if (order.getAppointmentId() == null && order.getServicePackageId() == null)
            throw new BusinessException("Chỉ áp dụng cho đơn dịch vụ Spa");
        if (order.getServicePackageId() != null && treatmentOrderPolicy.requiresTreatmentWorkflow(id))
            throw new BusinessException("Lộ trình điều trị phải quyết toán qua hồ sơ điều trị");
        return new com.core.beautyshop.modules.order.api.dto.SpaRefundBalance(id, order.getUserId(), order.getAppointmentId(),
                order.getServicePackageId(), order.getTotalAmount(), order.getPaidAmount(), order.getRefundedAmount());
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void confirmSpaServiceRefund(Long id, java.math.BigDecimal amount, String reference, String reason) {
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
        var row = new RefundConfirmation(); row.setOrderId(id); row.setAmount(amount); row.setReference(reference);
        row.setApprovalReason(reason); row.setConfirmedByUserId(com.core.beautyshop.shared.security.utils.SecurityUtils.getCurrentUserId());
        confirmations.saveAndFlush(row);
        order.setRefundedAmount(order.getRefundedAmount().add(amount)); order.setRefundReference(reference);
        if (order.getRefundedAmount().compareTo(order.getPaidAmount()) == 0) order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.getStatusHistories().add(OrderStatusHistory.builder().order(order).status(order.getStatus())
                .notes("Spa service refund: " + reference + "; amount=" + amount + "; reason=" + reason).build());
        orders.save(order); publishFinancialAdjustment(order);
    }
    private final OrderRepository orders;
    private final RefundConfirmationRepository confirmations;
    private final com.core.beautyshop.modules.spa.api.TreatmentOrderPolicy treatmentOrderPolicy;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;

    /** Partial refunds approved by treatment workflow; confirms a real manual transfer in the shared ledger. */
    @Transactional
    public void confirmTreatmentRefund(Long orderId, java.math.BigDecimal amount, String reference, String reason) {
        if (!com.core.beautyshop.shared.security.utils.SecurityUtils.isAdmin())
            throw new org.springframework.security.access.AccessDeniedException("Only administrators confirm refunds");
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
        var row = new RefundConfirmation(); row.setOrderId(orderId); row.setAmount(amount); row.setReference(reference);
        row.setApprovalReason(reason); row.setConfirmedByUserId(com.core.beautyshop.shared.security.utils.SecurityUtils.getCurrentUserId());
        confirmations.saveAndFlush(row);
        order.setRefundedAmount(order.getRefundedAmount().add(amount)); order.setRefundReference(reference);
        if (order.getRefundedAmount().compareTo(order.getPaidAmount()) == 0) order.setPaymentStatus(PaymentStatus.REFUNDED);
        orders.save(order);
        publishFinancialAdjustment(order);
    }

    /** A single approved transfer can refund the original purchase plus settled plan adjustments. */
    @Transactional
    public void confirmTreatmentRefund(java.util.List<Long> orderIds, java.math.BigDecimal amount, String reference, String reason) {
        if (!com.core.beautyshop.shared.security.utils.SecurityUtils.isAdmin())
            throw new org.springframework.security.access.AccessDeniedException("Only administrators confirm refunds");
        if (orderIds == null || orderIds.isEmpty() || amount == null || amount.signum() <= 0 || amount.scale() > 2
                || reference == null || reference.isBlank() || reference.length() > 100 || reason == null || reason.isBlank() || reason.length() > 250)
            throw new BusinessException("Invalid treatment refund confirmation");
        java.util.List<Order> sources = orderIds.stream().distinct().sorted().map(id -> orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund source order not found"))).toList();
        var previous = confirmations.findByReference(reference);
        if (previous.isPresent()) throw new BusinessException("Refund reference already belongs to an earlier confirmation");
        java.math.BigDecimal available = sources.stream().filter(o -> o.getServicePackageId() != null && o.getPaidAt() != null)
                .map(o -> o.getPaidAmount().subtract(o.getRefundedAmount())).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (amount.compareTo(available) > 0) throw new BusinessException("Refund exceeds the remaining amount actually received");
        java.math.BigDecimal remaining = amount; int index = 0;
        for (Order source : sources) {
            if (remaining.signum() == 0) break;
            java.math.BigDecimal part = remaining.min(source.getPaidAmount().subtract(source.getRefundedAmount()));
            if (part.signum() <= 0) continue;
            // Additional source rows have deterministic references; all are committed with the workflow in one transaction.
            String partReference = index++ == 0 ? reference : refundPartReference(reference, source.getId());
            confirmTreatmentRefund(source.getId(), part, partReference, reason); remaining = remaining.subtract(part);
        }
        if (remaining.signum() != 0) throw new BusinessException("Refund source amounts do not reconcile");
    }

    private String refundPartReference(String reference, Long orderId) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256").digest((reference + ":" + orderId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "TREATMENT-" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "CONFIRM_REFUND", resourceType = "ORDER")
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
        if (explicitVisitRefund && (!com.core.beautyshop.shared.security.utils.SecurityUtils.isAdmin()
                || request.approvalReason() == null || request.approvalReason().isBlank()))
            throw new BusinessException("Hoàn tiền dịch vụ Spa yêu cầu người phê duyệt là quản trị viên và phải có lý do cụ thể");
        if (!explicitVisitRefund && (order.getPaymentStatus() != PaymentStatus.REFUND_PENDING
                || (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.RETURNED
                    && order.getStatus() != OrderStatus.RETURN_TO_SENDER))) {
            throw new BusinessException("Đơn hàng không ở trạng thái chờ hoàn tiền");
        }
        java.math.BigDecimal outstanding = order.getPaidAmount().subtract(order.getRefundedAmount());
        if (request.amount().compareTo(outstanding) != 0) {
            throw new BusinessException("Số tiền xác nhận hoàn trả phải khớp chính xác với số tiền cần hoàn");
        }
        var confirmation = new RefundConfirmation();
        confirmation.setOrderId(id); confirmation.setReference(request.reference()); confirmation.setAmount(request.amount());
        confirmation.setConfirmedByUserId(com.core.beautyshop.shared.security.utils.SecurityUtils.getCurrentUserIdOptional().orElse(null));
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
            java.math.BigDecimal netEligible = order.getTotalAmount()
                    .subtract(order.getRefundedAmount())
                    .max(java.math.BigDecimal.ZERO)
                    .min(order.getPaidAmount().subtract(order.getRefundedAmount()).max(java.math.BigDecimal.ZERO));
            events.publishEvent(new com.core.beautyshop.modules.order.api.event.OrderEvents.OrderFinancialAdjustmentEvent(
                    order.getId(), order.getUserId(), netEligible));
        }
    }
}
