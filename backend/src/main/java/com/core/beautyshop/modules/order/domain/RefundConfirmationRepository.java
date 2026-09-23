package com.core.beautyshop.modules.order.domain;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RefundConfirmationRepository extends JpaRepository<RefundConfirmation, Long> {
    java.util.Optional<RefundConfirmation> findByReference(String reference);
}
