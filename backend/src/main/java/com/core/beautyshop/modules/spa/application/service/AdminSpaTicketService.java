package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import com.core.beautyshop.modules.order.api.OrderFacade;

@Service
@RequiredArgsConstructor
public class AdminSpaTicketService {
    private final UserServiceTicketRepository repository;
    private final OrderFacade orderFacade;
    private final BeautyServiceRepository services;
    private final TicketSessionMovementRepository movements;

    @Transactional(readOnly = true)
    public Page<UserServiceTicketResponse> find(Long userId, TicketStatus status, Pageable pageable) {
        Page<UserServiceTicket> page = userId != null && status != null
                ? repository.findByUserIdAndStatusAndIsDeletedFalse(userId, status, pageable)
                : userId != null ? repository.findByUserIdAndIsDeletedFalse(userId, pageable)
                : status != null ? repository.findByStatusAndIsDeletedFalse(status, pageable)
                : repository.findByIsDeletedFalse(pageable);
        return page.map(UserServiceTicketResponse::of);
    }

    @Transactional(readOnly = true)
    public UserServiceTicketResponse get(Long id) {
        return repository.findById(id)
                .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .map(UserServiceTicketResponse::of)
                .orElseThrow(() -> new ResourceNotFoundException("Spa ticket not found: " + id));
    }

    @Transactional
    public UserServiceTicketResponse extend(Long id, int days) {
        return extend(id, days, "Legacy administrative extension", java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public UserServiceTicketResponse extend(Long id, int days, String reason, String key) {
        if (days <= 0 || days > 3650) throw new BusinessException("Số ngày gia hạn phải từ 1 đến 3650 ngày");
        UserServiceTicket ticket = lock(id);
        if (ticket.getServicePackage() != null && Boolean.TRUE.equals(ticket.getServicePackage().getTreatmentManaged()))
            throw new BusinessException("Please extend treatment validity through its course extension request");
        String fingerprint = fingerprint("EXTEND:" + days + ":" + reason);
        if (replayed(ticket, "EXTEND", reason, key, fingerprint)) return UserServiceTicketResponse.of(ticket);
        requirePaidTicket(ticket);
        Instant oldExpiry = ticket.getExpiryDate();
        if (ticket.getExpiryDate() == null) throw new BusinessException("Vé không giới hạn thời gian không cần gia hạn hạn dùng");
        if (ticket.getUsedSessions() < ticket.getTotalSessions()) {
            if (ticket.getEntitlements().isEmpty()) throw new BusinessException("Thiếu quyền lợi trong vé; cần đối soát lại vé");
            ticket.getEntitlements().entrySet().stream().filter(entry -> entry.getValue().getTotal() > entry.getValue().getUsed())
                    .map(java.util.Map.Entry::getKey).sorted().forEach(this::requireAvailableService);
        }
        Instant base = ticket.getExpiryDate().isBefore(Instant.now()) ? Instant.now() : ticket.getExpiryDate();
        ticket.setExpiryDate(base.plusSeconds(Math.multiplyExact((long) days, 86400L)));
        if (ticket.getUsedSessions() < ticket.getTotalSessions()) ticket.setStatus(TicketStatus.ACTIVE);
        record(ticket, null, "EXTEND", reason, key, fingerprint, null, oldExpiry);
        return UserServiceTicketResponse.of(ticket);
    }

    @Transactional
    public UserServiceTicketResponse compensate(Long id, Long serviceId, int sessions) {
        return compensate(id, serviceId, sessions, "Legacy administrative compensation", java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public UserServiceTicketResponse compensate(Long id, Long serviceId, int sessions, String reason, String key) {
        if (serviceId == null || sessions <= 0 || sessions > 100) throw new BusinessException("Số buổi đền bù/cộng thêm không hợp lệ (phải từ 1 đến 100)");
        UserServiceTicket ticket = lock(id);
        String fingerprint = fingerprint("COMPENSATE:" + serviceId + ":" + sessions + ":" + reason);
        if (replayed(ticket, "COMPENSATE", reason, key, fingerprint)) return UserServiceTicketResponse.of(ticket);
        requirePaidTicket(ticket);
        TicketEntitlement entitlement = ticket.getEntitlements().get(serviceId);
        if (entitlement == null) throw new BusinessException("Dịch vụ không nằm trong gói vé liệu trình này");
        requireAvailableService(serviceId);
        entitlement.setTotal(Math.addExact(entitlement.getTotal(), sessions));
        ticket.setTotalSessions(Math.addExact(ticket.getTotalSessions(), sessions));
        if (ticket.getExpiryDate() == null || ticket.getExpiryDate().isAfter(Instant.now())) {
            ticket.setStatus(TicketStatus.ACTIVE);
        }
        record(ticket, serviceId, "COMPENSATE", reason, key, fingerprint, sessions, ticket.getExpiryDate());
        return UserServiceTicketResponse.of(ticket);
    }

    private String fingerprint(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private boolean replayed(UserServiceTicket ticket, String operation, String reason, String key, String fingerprint) {
        if (reason == null || reason.isBlank() || reason.length() > 1000 || key == null || !key.matches("[A-Za-z0-9:._-]{1,80}"))
            throw new BusinessException("Điều chỉnh vé cần có lý do và mã idempotency hợp lệ");
        var prior = movements.findByIdempotencyKey("ADMIN:" + key);
        if (prior.isEmpty()) return false;
        var m = prior.get();
        if (!ticket.getId().equals(m.getTicketId()) || !operation.equals(m.getOperation()) || !fingerprint.equals(m.getCommandFingerprint()))
            throw new BusinessException("Mã idempotency đã được dùng cho một điều chỉnh khác");
        return true;
    }
    private void record(UserServiceTicket ticket, Long serviceId, String operation, String reason, String key, String fingerprint, Integer adjustment, Instant oldExpiry) {
        var m = new TicketSessionMovement(); m.setTicketId(ticket.getId()); m.setServiceId(serviceId);
        m.setOperation(operation); m.setReason(reason); m.setIdempotencyKey("ADMIN:" + key); m.setCommandFingerprint(fingerprint);
        m.setSessionAdjustment(adjustment); m.setExpiryBefore(oldExpiry); m.setExpiryAfter(ticket.getExpiryDate());
        m.setUsedAfter(ticket.getUsedSessions()); m.setReservedAfter(ticket.getReservedSessions());
        m.setAvailableAfter(ticket.getTotalSessions() - ticket.getUsedSessions() - ticket.getReservedSessions());
        m.setActorUserId(com.core.beautyshop.shared.security.utils.SecurityUtils.getCurrentUserIdOptional().orElse(null)); movements.save(m);
    }

    private void requireAvailableService(Long id) {
        var service = services.findAvailableByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("Không thể cấp quyền lợi cho dịch vụ đã ngưng hoạt động; cần đối soát"));
        if ("TREATMENT_COURSE".equals(service.getServiceType()))
            throw new BusinessException("Dịch vụ đã chuyển sang điều trị theo lộ trình; không gia hạn hoặc bù lượt vào vé thông thường, cần đối soát quyền lợi cũ");
    }

    private UserServiceTicket lock(Long id) { return repository.findByIdForUpdate(id)
            .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé Spa: " + id)); }

    private void requirePaidTicket(UserServiceTicket ticket) {
        if (ticket.getStatus() == TicketStatus.REVOKED || ticket.getOrderId() == null
                || !orderFacade.isPaidOrderForUser(ticket.getOrderId(), ticket.getUserId())) {
            throw new BusinessException("Không thể điều chỉnh vé đã bị thu hồi hoặc chưa thanh toán");
        }
    }
}
