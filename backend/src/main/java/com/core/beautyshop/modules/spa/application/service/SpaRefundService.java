package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.dto.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.math.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class SpaRefundService {
    private final UserServiceTicketRepository tickets;
    private final SpaPurchaseSnapshotRepository snapshots;
    private final SpaServiceRefundRepository refunds;
    private final AppointmentRepository appointments;
    private final OrderFacade orders;
    private final jakarta.persistence.EntityManager em;
    private final TicketSessionMovementRepository movements;
    @io.swagger.v3.oas.annotations.media.Schema(name="SpaServiceRefundConfirmation")
    public record Confirmation(BigDecimal amount, String reference, String reason) {}
    public record PackageQuote(Long ticketId, BigDecimal purchasedAmount, BigDecimal refundableAmount, int unusedSessions, String blockedReason) {}
    public record LineQuote(Long appointmentItemId, String serviceName, BigDecimal allocatedAmount, BigDecimal refundedAmount, BigDecimal refundableAmount) {}
    public record VisitQuote(Long appointmentId, BigDecimal availableReceivedAmount, List<LineQuote> items) {}

    @PreAuthorize("hasRole('ADMIN')")
    public PackageQuote packageQuote(Long id) {
        var observed = ticket(id); var balance = orders.lockSpaRefundBalance(observed.getOrderId());
        return quote(lockedTicket(id), balance);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SpaServiceRefund settlePackage(Long id, Confirmation c) {
        validate(c, true);
        var observed = ticket(id); var balance = orders.lockSpaRefundBalance(observed.getOrderId());
        var ticket = lockedTicket(id);
        var previous = replay(balance.orderId(), id, null, c); if (previous != null) return previous;
        var q = quote(ticket, balance);
        if (q.blockedReason() != null) throw new BusinessException(q.blockedReason());
        if (q.refundableAmount().compareTo(c.amount()) != 0) throw new BusinessException("Số hoàn phải bằng báo giá quyết toán hiện tại; hãy tải lại");
        if (c.amount().signum() > 0) orders.confirmSpaServiceRefund(balance.orderId(), c.amount(), c.reference(), c.reason());
        ticket.setStatus(TicketStatus.REVOKED);
        var movement = new TicketSessionMovement(); movement.setTicketId(id); movement.setOperation("REFUND_CLOSE");
        movement.setReason(c.reason()); movement.setActorUserId(SecurityUtils.getCurrentUserId());
        movement.setIdempotencyKey("SETTLE:"+id); movement.setUsedAfter(ticket.getUsedSessions());
        movement.setReservedAfter(ticket.getReservedSessions()); movement.setAvailableAfter(0); movements.save(movement);
        return save(balance.orderId(), id, null, c);
    }

    private PackageQuote quote(UserServiceTicket ticket, SpaRefundBalance balance) {
        String blocked = null; BigDecimal amount = BigDecimal.ZERO;
        var snapshot = snapshots.findById(ticket.getOrderId()).orElse(null);
        int remaining = ticket.getEntitlements().values().stream().mapToInt(TicketEntitlement::available).sum();
        BigDecimal purchased = snapshot == null ? null : snapshot.getPurchaseAmount();
        if (!Objects.equals(balance.userId(), ticket.getUserId()) || !Objects.equals(balance.packageId(), ticket.getServicePackage().getId())) blocked = "Vé và đơn mua không khớp, cần đối soát";
        else if (Boolean.TRUE.equals(ticket.getServicePackage().getTreatmentManaged())) blocked = "Hãy quyết toán qua lộ trình điều trị";
        else if (ticket.getStatus() == TicketStatus.REVOKED) blocked = "Vé đã thu hồi hoặc đã quyết toán";
        else if (ticket.getReservedSessions() > 0 || ticket.getEntitlements().values().stream().anyMatch(e -> e.getReserved()>0)) blocked = "Hãy hủy hoặc đối soát các lịch đang giữ lượt trước khi quyết toán";
        else if (balance.paidAmount().signum() <= 0) blocked = "Chưa có tiền thực thu để quyết toán";
        else if (purchased == null || snapshot.getServiceDetails().isEmpty()) blocked = "Gói cũ thiếu giá trị phân bổ lúc mua; cần đối soát chứng từ gốc, không dùng giá danh mục hiện tại";
        else if (balance.refundedAmount().signum() > 0) blocked = "Đơn đã có hoàn tiền ngoài quy trình này; cần đối soát trước khi quyết toán";
        else {
            BigDecimal totalWeight = BigDecimal.ZERO, remainingWeight = BigDecimal.ZERO;
            boolean allFree = snapshot.getServiceDetails().values().stream().allMatch(d -> d.getUnitValue().signum()==0);
            for (var e : snapshot.getEntitlements().entrySet()) {
                var detail = snapshot.getServiceDetails().get(e.getKey()); var entitlement = ticket.getEntitlements().get(e.getKey());
                if (detail == null || entitlement == null || detail.getUnitValue().signum()<0 || entitlement.available()<0) {
                    blocked = "Quyền lợi không nhất quán, cần đối soát"; break;
                }
                BigDecimal unit = allFree ? BigDecimal.ONE : detail.getUnitValue();
                totalWeight = totalWeight.add(unit.multiply(BigDecimal.valueOf(e.getValue())));
                int unused = Math.max(0, e.getValue() - entitlement.getUsed());
                remainingWeight = remainingWeight.add(unit.multiply(BigDecimal.valueOf(unused)));
            }
            if (blocked == null && totalWeight.signum()>0) amount = purchased.multiply(remainingWeight).divide(totalWeight,0,RoundingMode.DOWN)
                    .min(balance.paidAmount().subtract(balance.refundedAmount()).max(BigDecimal.ZERO));
        }
        return new PackageQuote(ticket.getId(), purchased, amount, remaining, blocked);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public VisitQuote visitQuote(Long id) {
        var appointment = appointments.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi hẹn"));
        if (appointment.getOrderId() == null) throw new BusinessException("Buổi hẹn chưa có hóa đơn");
        var balance = orders.lockSpaRefundBalance(appointment.getOrderId());
        return visitQuote(id, balance);
    }
    private VisitQuote visitQuote(Long id, SpaRefundBalance balance) {
        if (!Objects.equals(balance.appointmentId(), id)) throw new BusinessException("Hóa đơn không thuộc buổi hẹn này");
        if (balance.paidAmount().compareTo(balance.totalAmount()) < 0)
            throw new BusinessException("Hãy hoàn tất thu và đối soát hóa đơn trước khi hoàn tiền theo dịch vụ");
        var invoice = orders.getSpaVisitInvoiceForStaff(id);
        var existing = refunds.findByOrderIdOrderByIdDesc(balance.orderId());
        BigDecimal tracked = existing.stream().map(SpaServiceRefund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if (tracked.compareTo(balance.refundedAmount()) != 0) throw new BusinessException("Hóa đơn có hoàn tiền chưa phân bổ theo dịch vụ; cần đối soát");
        List<SpaVisitCharge> charges = invoice.items().stream().sorted(Comparator.comparing(SpaVisitCharge::appointmentItemId)).toList();
        BigDecimal subtotal = charges.stream().map(SpaVisitCharge::unitPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LineQuote> lines = new ArrayList<>(); BigDecimal cumulative = BigDecimal.ZERO, previous = BigDecimal.ZERO;
        for (var item : charges) {
            cumulative = cumulative.add(item.unitPrice());
            BigDecimal cumulativeAllocation = subtotal.signum()==0 ? BigDecimal.ZERO : balance.totalAmount().multiply(cumulative).divide(subtotal,0,RoundingMode.DOWN);
            BigDecimal allocation = cumulativeAllocation.subtract(previous); previous = cumulativeAllocation;
            BigDecimal refunded = existing.stream().filter(r -> Objects.equals(r.getAppointmentItemId(),item.appointmentItemId())).map(SpaServiceRefund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            lines.add(new LineQuote(item.appointmentItemId(),item.serviceName(),allocation,refunded,allocation.subtract(refunded).max(BigDecimal.ZERO)));
        }
        return new VisitQuote(id,balance.paidAmount().subtract(balance.refundedAmount()).max(BigDecimal.ZERO),lines);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public SpaServiceRefund refundVisitLine(Long id, Long itemId, Confirmation c) {
        validate(c,false);
        var appointment = appointments.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi hẹn"));
        if (appointment.getOrderId() == null) throw new BusinessException("Buổi hẹn chưa có hóa đơn");
        var balance = orders.lockSpaRefundBalance(appointment.getOrderId());
        var previous = replay(balance.orderId(),null,itemId,c); if(previous!=null)return previous;
        var quote = visitQuote(id,balance);
        var line = quote.items().stream().filter(i -> Objects.equals(i.appointmentItemId(),itemId)).findFirst()
                .orElseThrow(() -> new BusinessException("Dịch vụ không thuộc hóa đơn hoặc được thanh toán bằng vé"));
        if (c.amount().compareTo(line.refundableAmount())>0 || c.amount().compareTo(quote.availableReceivedAmount())>0)
            throw new BusinessException("Số hoàn vượt giá trị còn lại của dịch vụ hoặc tiền thực thu");
        orders.confirmSpaServiceRefund(balance.orderId(),c.amount(),c.reference(),c.reason());
        return save(balance.orderId(),null,itemId,c);
    }
    private UserServiceTicket ticket(Long id) {
        return tickets.findById(id).filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()) && t.getOrderId()!=null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé đã mua"));
    }
    private UserServiceTicket lockedTicket(Long id) {
        var ticket = tickets.findByIdForUpdate(id).orElseThrow(); em.refresh(ticket, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); return ticket;
    }
    private void validate(Confirmation c, boolean allowZero) {
        if (c==null || c.amount()==null || c.amount().signum()<(allowZero?0:1) || c.amount().stripTrailingZeros().scale()>0
                || c.reference()==null || c.reference().isBlank() || c.reference().length()>100
                || c.reason()==null || c.reason().isBlank() || c.reason().length()>250)
            throw new BusinessException("Cần số tiền nguyên VND hợp lệ, chứng từ và lý do (tối đa 250 ký tự)");
    }
    private SpaServiceRefund replay(Long orderId,Long ticketId,Long itemId,Confirmation c) {
        var old=refunds.findByReference(c.reference()).orElse(null); if(old==null)return null;
        if(!Objects.equals(old.getOrderId(),orderId) || !Objects.equals(old.getTicketId(),ticketId)
                || !Objects.equals(old.getAppointmentItemId(),itemId) || old.getAmount().compareTo(c.amount())!=0 || !old.getReason().equals(c.reason()))
            throw new BusinessException("Chứng từ đã được dùng với nội dung khác");
        return old;
    }
    private SpaServiceRefund save(Long orderId,Long ticketId,Long itemId,Confirmation c) {
        var row=new SpaServiceRefund();row.setOrderId(orderId);row.setTicketId(ticketId);row.setAppointmentItemId(itemId);
        row.setAmount(c.amount());row.setReference(c.reference());row.setReason(c.reason());row.setActorUserId(SecurityUtils.getCurrentUserId());
        return refunds.saveAndFlush(row);
    }
}
