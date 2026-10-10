package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;

/** Caller holds the appointment and ticket write locks. State and ledger change in the same transaction. */
@Service @RequiredArgsConstructor
public class TicketUsageService {
    private final TicketSessionMovementRepository movements;
    public void requireCanReserve(AppointmentItem item) {
        UserServiceTicket ticket = item.getTicket();
        if (ticket == null) return;
        TicketEntitlement entitlement = entitlement(item);
        if (item.getTicketUsageState() != TicketUsageState.NONE) throw new BusinessException("Ticket item has already been reserved");
        if (entitlement.available() <= 0 || ticket.getTotalSessions() - ticket.getUsedSessions() - ticket.getReservedSessions() <= 0)
            throw new BusinessException("No available ticket sessions for this service");
    }
    public void reserve(AppointmentItem item) {
        requireCanReserve(item);
        UserServiceTicket ticket = item.getTicket(); if (ticket == null) return;
        TicketEntitlement entitlement = entitlement(item);
        entitlement.setReserved(entitlement.getReserved() + 1);
        ticket.setReservedSessions(ticket.getReservedSessions() + 1);
        item.setTicketUsageState(TicketUsageState.RESERVED);
    }
    public void recordReservation(AppointmentItem item) {
        if (item.getTicket() != null) record(item, "RESERVE", "Appointment reservation");
    }
    public void consume(AppointmentItem item, boolean forfeited, String reason) {
        if (item.getTicket() == null) return;
        TicketUsageState target = forfeited ? TicketUsageState.FORFEITED : TicketUsageState.REDEEMED;
        if (item.getTicketUsageState() == target) return;
        requireReserved(item);
        UserServiceTicket ticket = item.getTicket();
        TicketEntitlement e = entitlement(item);
        e.setReserved(e.getReserved() - 1); e.setUsed(e.getUsed() + 1);
        ticket.setReservedSessions(ticket.getReservedSessions() - 1);
        ticket.setUsedSessions(ticket.getUsedSessions() + 1);
        item.setTicketUsageState(target);
        refreshStatus(ticket);
        record(item, forfeited ? "FORFEIT" : "REDEEM", reason);
    }
    public void release(AppointmentItem item, String reason) {
        if (item.getTicket() == null || item.getTicketUsageState() == TicketUsageState.RELEASED) return;
        requireReserved(item);
        UserServiceTicket ticket = item.getTicket();
        TicketEntitlement e = entitlement(item);
        e.setReserved(e.getReserved() - 1);
        ticket.setReservedSessions(ticket.getReservedSessions() - 1);
        item.setTicketUsageState(TicketUsageState.RELEASED);
        refreshStatus(ticket);
        record(item, "RELEASE", reason);
    }
    private TicketEntitlement entitlement(AppointmentItem item) {
        TicketEntitlement e = item.getTicket().getEntitlements().get(item.getService().getId());
        if (e == null || e.getUsed() < 0 || e.getReserved() < 0 || e.available() < 0)
            throw new BusinessException("Ticket quota is inconsistent; reconciliation required");
        return e;
    }
    private void requireReserved(AppointmentItem item) {
        if (item.getTicketUsageState() != TicketUsageState.RESERVED || entitlement(item).getReserved() <= 0
                || item.getTicket().getReservedSessions() <= 0)
            throw new BusinessException("Ticket reservation is inconsistent; reconciliation required");
    }
    private void refreshStatus(UserServiceTicket ticket) {
        if (ticket.getStatus() == TicketStatus.REVOKED) return;
        ticket.setStatus(ticket.getUsedSessions() >= ticket.getTotalSessions() ? TicketStatus.COMPLETED
                : ticket.getExpiryDate() != null && !ticket.getExpiryDate().isAfter(Instant.now()) ? TicketStatus.EXPIRED : TicketStatus.ACTIVE);
    }
    private void record(AppointmentItem item, String operation, String reason) {
        if (item.getId() == null) throw new BusinessException("Save appointment item before recording quota movement");
        String key = "ITEM:" + item.getId() + ":" + operation;
        if (movements.findByIdempotencyKey(key).isPresent()) return;
        UserServiceTicket ticket = item.getTicket();
        TicketSessionMovement m = new TicketSessionMovement();
        m.setTicketId(ticket.getId()); m.setServiceId(item.getService().getId()); m.setAppointmentItemId(item.getId());
        m.setOperation(operation); m.setReason(reason); m.setIdempotencyKey(key);
        m.setUsedAfter(ticket.getUsedSessions()); m.setReservedAfter(ticket.getReservedSessions());
        m.setAvailableAfter(ticket.getTotalSessions() - ticket.getUsedSessions() - ticket.getReservedSessions());
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) m.setActorUserId(SecurityUtils.getCurrentUserId());
        movements.save(m);
    }
}
