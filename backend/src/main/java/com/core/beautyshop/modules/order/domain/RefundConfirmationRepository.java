package com.core.beautyshop.modules.order.domain;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RefundConfirmationRepository extends JpaRepository<RefundConfirmation, Long> {
    java.util.Optional<RefundConfirmation> findByReference(String reference);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.amount), 0) from RefundConfirmation r "
            + "where (:from is null or r.confirmedAt >= :from) and (:to is null or r.confirmedAt < :to)")
    java.math.BigDecimal sumConfirmed(java.time.Instant from, java.time.Instant to);
}
