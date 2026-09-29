package com.core.beautyshop.shared.outbox.application.service;
import com.core.beautyshop.shared.outbox.domain.*;
import com.core.beautyshop.shared.outbox.domain.enums.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service @RequiredArgsConstructor
public class OutboxClaimService {
    private final OutboxMessageRepository messages;
    private final jakarta.persistence.EntityManager entityManager;
    @Transactional
    public List<OutboxMessage> claimBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxMessage> claimed = new ArrayList<>();
        for (OutboxMessage message : messages.findClaimable(now, PageRequest.of(0, 50))) {
            String token = UUID.randomUUID().toString();
            if (messages.claim(message.getId(), token, now.plusMinutes(5), now) == 1) {
                entityManager.detach(message);
                message.setClaimToken(token);
                claimed.add(message);
            }
        }
        return claimed;
    }
    @Transactional
    public void success(OutboxMessage message) {
        messages.completeClaim(message.getId(), message.getClaimToken(), LocalDateTime.now());
    }
    @Transactional
    public void failure(OutboxMessage message, Exception exception) {
        messages.failClaim(message.getId(), message.getClaimToken(),
                message.getRetryCount() >= 4 ? OutboxStatus.FAILED : OutboxStatus.PENDING,
                exception.getClass().getSimpleName());
    }

    @Transactional
    public int cleanupPublishedMessages(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = messages.deletePublishedBefore(cutoff);
        log.info("Đã dọn dẹp {} outbox messages cũ hơn {} ngày", deleted, retentionDays);
        return deleted;
    }
}
