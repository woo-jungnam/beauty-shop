package com.core.beautyshop.modules.payment.domain;

import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class PaymentSummaryRepositoryTest {
    @Autowired PaymentTransactionRepository transactions;
    @Autowired EntityManager entities;

    PaymentTransaction save(String reference, String gateway, String direction, TransactionStatus status, int amount) {
        return transactions.saveAndFlush(PaymentTransaction.builder().orderNumber("ORD-TEST").referenceCode(reference)
                .gateway(gateway).transferType(direction).status(status).amount(BigDecimal.valueOf(amount)).rawPayload("{}").build());
    }

    @Test
    void aggregateSeparatesCodAndBankAndExcludesOutgoingTransfersFromIncomingAmount() {
        save("BANK", "SEPAY", "in", TransactionStatus.SUCCESS, 100);
        save("COD", "COD", "in", TransactionStatus.SUCCESS, 60);
        save("UNIDENTIFIED", "SEPAY", "in", TransactionStatus.IGNORED, 50);
        save("OUT", "SEPAY", "out", TransactionStatus.IGNORED, 500);
        var buckets = transactions.summarize(null, null);
        var ignored = buckets.stream().filter(b -> b.getStatus() == TransactionStatus.IGNORED).findFirst().orElseThrow();
        assertEquals(2, ignored.getTransactionCount()); assertEquals(0, BigDecimal.valueOf(50).compareTo(ignored.getReceivedAmount()));
        var cod = buckets.stream().filter(b -> "COD".equals(b.getGateway())).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(60).compareTo(cod.getReceivedAmount()));
        assertEquals(3, buckets.size());
    }

    @Test
    void dateRangeUsesInclusiveStartAndExclusiveEndInUtc() {
        var early = save("EARLY", "SEPAY", "in", TransactionStatus.SUCCESS, 10);
        var late = save("LATE", "SEPAY", "in", TransactionStatus.SUCCESS, 20);
        Instant from = Instant.parse("2026-10-01T00:00:00Z"), to = from.plusSeconds(86400);
        entities.createQuery("update PaymentTransaction t set t.createdAt = :time where t.id = :id")
                .setParameter("time", from).setParameter("id", early.getId()).executeUpdate();
        entities.createQuery("update PaymentTransaction t set t.createdAt = :time where t.id = :id")
                .setParameter("time", to).setParameter("id", late.getId()).executeUpdate();
        entities.clear();
        var buckets = transactions.summarize(from, to);
        assertEquals(1, buckets.size()); assertEquals(1, buckets.getFirst().getTransactionCount());
        assertEquals(0, BigDecimal.TEN.compareTo(buckets.getFirst().getReceivedAmount()));
    }
}
