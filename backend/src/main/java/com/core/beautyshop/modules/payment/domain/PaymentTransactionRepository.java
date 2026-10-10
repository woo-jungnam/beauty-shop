package com.core.beautyshop.modules.payment.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<PaymentTransaction> {

    boolean existsByReferenceCode(String referenceCode);

    Optional<PaymentTransaction> findByReferenceCode(String referenceCode);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from PaymentTransaction t where t.referenceCode = :reference")
    Optional<PaymentTransaction> findByReferenceCodeForUpdate(@org.springframework.data.repository.query.Param("reference") String reference);

    Page<PaymentTransaction> findByOrderNumberOrderByCreatedAtDesc(String orderNumber, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select t.status as status, t.gateway as gateway, count(t) as transactionCount, "
            + "coalesce(sum(case when lower(t.transferType) = 'in' and t.amount > 0 then t.amount else 0 end), 0) as receivedAmount "
            + "from PaymentTransaction t where (:from is null or t.createdAt >= :from) "
            + "and (:to is null or t.createdAt < :to) group by t.status, t.gateway")
    java.util.List<SummaryBucket> summarize(java.time.Instant from, java.time.Instant to);

    interface SummaryBucket {
        com.core.beautyshop.modules.payment.domain.enums.TransactionStatus getStatus();
        String getGateway();
        long getTransactionCount();
        java.math.BigDecimal getReceivedAmount();
    }
}
