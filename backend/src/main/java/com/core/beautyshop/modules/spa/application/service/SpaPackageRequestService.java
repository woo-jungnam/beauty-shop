package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class SpaPackageRequestService {
    private final EntityManager em;
    private final UserServiceTicketRepository tickets;
    private final AdminSpaTicketService administrativeTickets;
    private final SpaAccessService access;
    public record Command(String type, String reason, Integer extensionDays) {}
    public record Decision(boolean approve, String reason) {}

    @PreAuthorize("hasRole('ADMIN')")
    public List<SpaPackageRequest> pending() {
        return em.createQuery("select r from SpaPackageRequest r join UserServiceTicket t on t.id=r.ticketId where r.status in ('PENDING_APPROVAL','AWAITING_SETTLEMENT') and t.status<>com.core.beautyshop.modules.spa.domain.enums.TicketStatus.REVOKED order by r.createdAt",SpaPackageRequest.class)
                .setMaxResults(200).getResultList();
    }

    @PreAuthorize("isAuthenticated()")
    public List<SpaPackageRequest> list(Long ticketId) {
        var ticket = ticket(ticketId); requireOwnerOrAdmin(ticket);
        var result = em.createQuery("select r from SpaPackageRequest r where r.ticketId=:id order by r.id desc", SpaPackageRequest.class)
                .setParameter("id", ticketId).getResultList();
        if (ticket.getStatus()==TicketStatus.REVOKED) result.stream().filter(r -> "AWAITING_SETTLEMENT".equals(r.getStatus())).forEach(r -> r.setStatus("COMPLETED"));
        return result;
    }

    @PreAuthorize("isAuthenticated()")
    public SpaPackageRequest create(Long ticketId, Command command) {
        var ticket=ticket(ticketId);
        if (!Objects.equals(ticket.getUserId(), SecurityUtils.getCurrentUserId())) throw new BusinessException("Chỉ chủ vé được gửi yêu cầu");
        requireOrdinary(ticket);
        if (ticket.getStatus()==TicketStatus.REVOKED) throw new BusinessException("Vé đã đóng hoặc quyết toán");
        if (command==null || !Set.of("EXTEND","CANCEL").contains(Objects.toString(command.type(),""))) throw new BusinessException("Loại yêu cầu không hợp lệ");
        if ("EXTEND".equals(command.type()) && (ticket.getExpiryDate()==null || command.extensionDays()==null || command.extensionDays()<1 || command.extensionDays()>3650)) throw new BusinessException("Gia hạn cần vé có hạn và số ngày từ 1 đến 3650");
        if (!em.createQuery("select r.id from SpaPackageRequest r where r.ticketId=:id and r.status in ('PENDING_APPROVAL','AWAITING_SETTLEMENT')",Long.class).setParameter("id",ticketId).getResultList().isEmpty()) throw new BusinessException("Vé đã có yêu cầu đang xử lý");
        var r=new SpaPackageRequest(); r.setTicketId(ticketId); r.setUserId(ticket.getUserId()); r.setType(command.type()); r.setReason(reason(command.reason()));
        r.setExtensionDays("EXTEND".equals(command.type()) ? command.extensionDays() : null); em.persist(r); return r;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SpaPackageRequest review(Long ticketId, Long requestId, Decision decision) {
        var ticket=ticket(ticketId); requireOrdinary(ticket);
        var r=em.find(SpaPackageRequest.class,requestId,LockModeType.PESSIMISTIC_WRITE);
        if (r==null || !Objects.equals(r.getTicketId(),ticketId)) throw new ResourceNotFoundException("Không tìm thấy yêu cầu");
        if (!"PENDING_APPROVAL".equals(r.getStatus()) || ticket.getStatus()==TicketStatus.REVOKED) throw new BusinessException("Yêu cầu hoặc vé đã được xử lý");
        if (decision==null) throw new BusinessException("Thiếu quyết định xử lý");
        r.setReviewReason(reason(decision.reason())); r.setReviewedBy(SecurityUtils.getCurrentUserId()); r.setReviewedAt(Instant.now());
        if (!decision.approve()) r.setStatus("REJECTED");
        else if ("EXTEND".equals(r.getType())) {
            administrativeTickets.extend(ticketId,r.getExtensionDays(),r.getReviewReason(),"package-request:"+r.getId()); r.setStatus("APPROVED");
        } else r.setStatus("AWAITING_SETTLEMENT");
        return r;
    }
    private UserServiceTicket ticket(Long id) {
        return tickets.findByIdForUpdate(id).filter(t -> !Boolean.TRUE.equals(t.getIsDeleted())).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé"));
    }
    private void requireOwnerOrAdmin(UserServiceTicket ticket) {
        if (!Objects.equals(ticket.getUserId(),SecurityUtils.getCurrentUserId()) && !access.hasRole("ADMIN")) throw new BusinessException("Không có quyền xem yêu cầu của vé");
    }
    private void requireOrdinary(UserServiceTicket ticket) {
        if (ticket.getServicePackage()==null || Boolean.TRUE.equals(ticket.getServicePackage().getTreatmentManaged())) throw new BusinessException("Hãy gửi yêu cầu qua hồ sơ điều trị");
    }
    private String reason(String value) {
        if (value==null || value.isBlank() || value.length()>1000) throw new BusinessException("Lý do bắt buộc, tối đa 1000 ký tự");
        return value.trim();
    }
}
