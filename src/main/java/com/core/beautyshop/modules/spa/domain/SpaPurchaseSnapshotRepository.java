package com.core.beautyshop.modules.spa.domain;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SpaPurchaseSnapshotRepository extends JpaRepository<SpaPurchaseSnapshot, Long> {
    // One SQL statement observes one committed snapshot while an order becomes a ticket.
    @org.springframework.data.jpa.repository.Query(value = "select "
            + "(select count(*) from user_service_tickets t join ticket_entitlements e on e.ticket_id = t.id "
            + "where e.service_id = :serviceId and e.total_sessions > e.used_sessions and t.is_deleted = false "
            + "and t.status not in ('REVOKED','EXPIRED') and t.order_id is not null "
            + "and (t.expiry_date is null or t.expiry_date > :now)) "
            + "+ (select count(*) from spa_purchase_entitlements e join orders o on o.id = e.order_id "
            + "where e.service_id = :serviceId and o.is_deleted = false and o.status <> 'CANCELLED' "
            + "and o.payment_status <> 'REFUNDED' "
            + "and not exists (select 1 from user_service_tickets t where t.order_id = o.id))", nativeQuery = true)
    long countServiceOrderObligations(@org.springframework.data.repository.query.Param("serviceId") Long serviceId,
                                     @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    @org.springframework.data.jpa.repository.Query(value = "select count(*) from spa_purchase_entitlements e "
            + "join orders o on o.id = e.order_id where e.service_id = :serviceId and o.is_deleted = false "
            + "and o.status <> 'CANCELLED' and o.payment_status <> 'REFUNDED' "
            + "and not exists (select 1 from user_service_tickets t where t.order_id = o.id)", nativeQuery = true)
    long countUnissuedServiceOrders(@org.springframework.data.repository.query.Param("serviceId") Long serviceId);
}
