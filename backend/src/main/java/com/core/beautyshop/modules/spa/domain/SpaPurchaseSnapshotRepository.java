package com.core.beautyshop.modules.spa.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SpaPurchaseSnapshotRepository extends JpaRepository<SpaPurchaseSnapshot, Long> {
    @Query(value = "select "
            + "(select count(*) from user_service_tickets t join ticket_entitlements e on e.ticket_id = t.id "
            + "where e.service_id = :serviceId and e.total_sessions > e.used_sessions and t.is_deleted = false "
            + "and t.status not in ('REVOKED','EXPIRED') and t.order_id is not null "
            + "and (t.expiry_date is null or t.expiry_date > :now)) "
            + "+ (select count(*) from spa_purchase_entitlements e join orders o on o.id = e.order_id "
            + "where e.service_id = :serviceId and o.is_deleted = false and o.status <> 'CANCELLED' "
            + "and o.payment_status <> 'REFUNDED' "
            + "and not exists (select 1 from user_service_tickets t where t.order_id = o.id))", nativeQuery = true)
    long countServiceOrderObligations(@Param("serviceId") Long serviceId,
                                     @Param("now") Instant now);
    @Query(value = "select count(*) from spa_purchase_entitlements e "
            + "join orders o on o.id = e.order_id where e.service_id = :serviceId and o.is_deleted = false "
            + "and o.status <> 'CANCELLED' and o.payment_status <> 'REFUNDED' "
            + "and not exists (select 1 from user_service_tickets t where t.order_id = o.id)", nativeQuery = true)
    long countUnissuedServiceOrders(@Param("serviceId") Long serviceId);
}
