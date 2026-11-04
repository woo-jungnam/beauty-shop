package com.core.beautyshop.modules.payment.domain;

import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long>, JpaSpecificationExecutor<PaymentTransaction> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);

    boolean existsByReferenceCode(String referenceCode);

    Optional<PaymentTransaction> findByReferenceCode(String referenceCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.referenceCode = :reference")
    Optional<PaymentTransaction> findByReferenceCodeForUpdate(@Param("reference") String reference);

    Page<PaymentTransaction> findByOrderNumberOrderByCreatedAtDesc(String orderNumber, Pageable pageable);

    @Query("select t.status as status, t.gateway as gateway, count(t) as transactionCount, "
            + "coalesce(sum(case when lower(t.transferType) = 'in' and t.amount > 0 then t.amount else 0 end), 0) as receivedAmount "
            + "from PaymentTransaction t where (:from is null or t.createdAt >= :from) "
            + "and (:to is null or t.createdAt < :to) group by t.status, t.gateway")
    List<SummaryBucket> summarize(@Param("from") Instant from, @Param("to") Instant to);

    interface SummaryBucket {
        TransactionStatus getStatus();
        String getGateway();
        long getTransactionCount();
        BigDecimal getReceivedAmount();
    }
}
