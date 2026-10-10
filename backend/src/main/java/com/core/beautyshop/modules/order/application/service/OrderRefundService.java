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
    private final OrderRepository orders;
    private final RefundConfirmationRepository confirmations;

    @com.core.beautyshop.shared.audit.api.annotation.AuditAction(action = "CONFIRM_REFUND", resourceType = "ORDER")
    @Transactional
    public void confirm(Long id, ConfirmRefundRequest request) {
        if (request == null || request.reference() == null || request.reference().isBlank() || request.reference().length() > 100
                || request.amount() == null || request.amount().signum() <= 0 || request.amount().scale() > 2
                || (request.approvalReason() != null && request.approvalReason().length() > 250))
            throw new BusinessException("Invalid refund confirmation");
        Order order = orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        var previous = confirmations.findByReference(request.reference());
        if (previous.isPresent()) {
            if (!id.equals(previous.get().getOrderId()) || request.amount().compareTo(previous.get().getAmount()) != 0) {
                throw new BusinessException("Refund reference was already used for another confirmation");
            }
            return;
        }
        boolean completedVisit = order.getAppointmentId() != null && order.getStatus() == OrderStatus.COMPLETED;
        boolean explicitVisitRefund = completedVisit && (order.getPaymentStatus() == PaymentStatus.PAID
                || order.getPaymentStatus() == PaymentStatus.PENDING || order.getPaymentStatus() == PaymentStatus.REFUND_PENDING);
        if (explicitVisitRefund && (!com.core.beautyshop.shared.security.utils.SecurityUtils.isAdmin()
                || request.approvalReason() == null || request.approvalReason().isBlank()))
            throw new BusinessException("A Spa visit refund requires an administrator and an explicit approval reason");
        if (!explicitVisitRefund && (order.getPaymentStatus() != PaymentStatus.REFUND_PENDING
                || (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.RETURNED))) {
            throw new BusinessException("Order is not awaiting a refund");
        }
        java.math.BigDecimal outstanding = order.getPaidAmount().subtract(order.getRefundedAmount());
        if (request.amount().compareTo(outstanding) != 0) {
            throw new BusinessException("Confirm the exact outstanding refund amount");
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
    }
}
