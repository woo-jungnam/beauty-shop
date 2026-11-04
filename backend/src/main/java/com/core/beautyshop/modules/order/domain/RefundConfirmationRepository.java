package com.core.beautyshop.modules.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface RefundConfirmationRepository extends JpaRepository<RefundConfirmation, Long> {
    Optional<RefundConfirmation> findByReference(String reference);

    @Query("select coalesce(sum(r.amount),0) from RefundConfirmation r where r.orderId = :orderId and r.refundKind = 'OVERPAYMENT'")
    BigDecimal sumOverpaymentRefunds(@Param("orderId") Long orderId);

    @Query("select coalesce(sum(r.amount), 0) from RefundConfirmation r "
            + "where (:from is null or r.confirmedAt >= :from) and (:to is null or r.confirmedAt < :to)")
    BigDecimal sumConfirmed(@Param("from") Instant from, @Param("to") Instant to);
}
