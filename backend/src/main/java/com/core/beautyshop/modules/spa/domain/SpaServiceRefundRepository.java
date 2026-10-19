package com.core.beautyshop.modules.spa.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SpaServiceRefundRepository extends JpaRepository<SpaServiceRefund,Long> {
    Optional<SpaServiceRefund> findByReference(String reference);
    List<SpaServiceRefund> findByOrderIdOrderByIdDesc(Long orderId);
    List<SpaServiceRefund> findByTicketIdOrderByIdDesc(Long ticketId);
}
