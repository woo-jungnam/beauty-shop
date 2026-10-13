package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface TicketSessionMovementRepository extends JpaRepository<TicketSessionMovement, Long> {
    Optional<TicketSessionMovement> findByIdempotencyKey(String key);
    Page<TicketSessionMovement> findByTicketIdOrderByIdDesc(Long ticketId, Pageable pageable);
}
