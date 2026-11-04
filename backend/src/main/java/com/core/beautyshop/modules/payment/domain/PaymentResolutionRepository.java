package com.core.beautyshop.modules.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface PaymentResolutionRepository extends JpaRepository<PaymentResolution, Long> {
    Optional<PaymentResolution> findByTransactionId(Long id);

    @Query("select coalesce(sum(r.amount),0) from PaymentResolution r where r.kind='REFUND' and (:from is null or r.createdAt >= :from) and (:to is null or r.createdAt < :to)")
    BigDecimal refundAmount(@Param("from") Instant from, @Param("to") Instant to);
}
