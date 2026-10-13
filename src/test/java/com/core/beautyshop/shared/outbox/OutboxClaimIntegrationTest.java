package com.core.beautyshop.shared.outbox;

import com.core.beautyshop.shared.outbox.application.service.OutboxClaimService;
import com.core.beautyshop.shared.outbox.domain.*;
import com.core.beautyshop.shared.outbox.domain.enums.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OutboxClaimIntegrationTest {
    @Autowired OutboxMessageRepository messages;
    @Autowired OutboxClaimService claims;

    @Test
    void preservesAggregateOrderAndIgnoresCompletionFromAnExpiredLease() {
        String aggregate = UUID.randomUUID().toString();
        OutboxMessage first = messages.save(message(aggregate));
        OutboxMessage second = messages.save(message(aggregate));
        OutboxMessage originalClaim = claims.claimBatch().stream().filter(m -> m.getId().equals(first.getId())).findFirst().orElseThrow();
        OutboxMessage stored = messages.findById(first.getId()).orElseThrow();
        assertEquals(OutboxStatus.PROCESSING, stored.getStatus());
        assertNotNull(stored.getLeaseUntil());
        assertFalse(claims.claimBatch().stream().anyMatch(m -> m.getId().equals(second.getId())));

        stored.setLeaseUntil(LocalDateTime.now().minusSeconds(1)); messages.save(stored);
        OutboxMessage newClaim = claims.claimBatch().stream().filter(m -> m.getId().equals(first.getId())).findFirst().orElseThrow();
        assertNotEquals(originalClaim.getClaimToken(), newClaim.getClaimToken());
        claims.success(originalClaim);
        assertEquals(OutboxStatus.PROCESSING, messages.findById(first.getId()).orElseThrow().getStatus());
        claims.success(newClaim);
        assertEquals(OutboxStatus.PUBLISHED, messages.findById(first.getId()).orElseThrow().getStatus());
        assertTrue(claims.claimBatch().stream().anyMatch(m -> m.getId().equals(second.getId())));
    }

    private OutboxMessage message(String aggregate) {
        return OutboxMessage.builder().aggregateType("CLAIM_TEST").aggregateId(aggregate)
                .topic("test-events").messageKey(aggregate).payload("{}").build();
    }
}
