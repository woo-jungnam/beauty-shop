package com.core.beautyshop.modules.payment.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentResolutionRepository extends JpaRepository<PaymentResolution,Long>{
 Optional<PaymentResolution> findByTransactionId(Long id);
 @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.amount),0) from PaymentResolution r where r.kind='REFUND' and (:from is null or r.createdAt>=:from) and (:to is null or r.createdAt<:to)")
 java.math.BigDecimal refundAmount(java.time.Instant from,java.time.Instant to);
}
