package com.core.beautyshop.modules.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.userId = :userId and o.isDeleted = false "
            + "and o.servicePackageId is not null and o.paymentStatus = com.core.beautyshop.modules.order.domain.enums.PaymentStatus.PAID "
            + "and o.paidAt is not null and o.paidAmount >= o.totalAmount order by o.id")
    List<Order> findPaidSpaPackageOrdersForUpdate(@Param("userId") Long userId);

    @Query("select count(o) > 0 from Order o join o.items i, ProductVariant v where o.id = :orderId and o.userId = :userId and o.status = com.core.beautyshop.modules.order.domain.enums.OrderStatus.DELIVERED and v.id = i.productVariantId and v.product.id = :productId")
    boolean existsDeliveredProductPurchase(Long orderId, Long userId, Long productId);

    @Query("select o.id from Order o join o.items i, ProductVariant v where o.userId = :userId and o.status = com.core.beautyshop.modules.order.domain.enums.OrderStatus.DELIVERED and v.id = i.productVariantId and v.product.id = :productId order by o.createdAt desc")
    List<Long> findDeliveredOrderIdsForProductPurchase(@Param("userId") Long userId, @Param("productId") Long productId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items"})
    @Query("select o from Order o where o.orderNumber = :orderNumber and (o.customerPhone = :phone or replace(o.customerPhone, '+84', '0') = replace(:phone, '+84', '0')) and o.isDeleted = false")
    Optional<Order> findByOrderNumberAndPhone(String orderNumber, String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(String orderNumber);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(Long id);

    Optional<Order> findByCheckoutKey(String checkoutKey);

    @EntityGraph(attributePaths = {"spaVisitItems"})
    Optional<Order> findByAppointmentId(Long appointmentId);

    @Query("select o.id from Order o where o.appointmentId = :appointmentId and o.isDeleted = false")
    Optional<Long> findIdByAppointmentId(Long appointmentId);

    @Query("select o.id from Order o where o.status in (com.core.beautyshop.modules.order.domain.enums.OrderStatus.PENDING, com.core.beautyshop.modules.order.domain.enums.OrderStatus.CONFIRMED) "
            + "and o.paymentMethod = com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK "
            + "and o.paymentStatus <> com.core.beautyshop.modules.order.domain.enums.PaymentStatus.PAID "
            + "and o.appointmentId is null and o.isDeleted = false and o.id > :after and o.paymentDeadline <= :now order by o.id")
    List<Long> findExpiredPaymentIdsAfter(Instant now, Long after, Pageable pageable);

    default List<Long> findExpiredPaymentIds(Instant now, Pageable pageable) {
        return findExpiredPaymentIdsAfter(now, 0L, pageable);
    }

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select o from Order o where o.userId = :userId and o.isDeleted = false order by o.createdAt desc")
    Page<Order> findVisibleCustomerHistoryByUserId(Long userId, Pageable pageable);

    @Query("select o from Order o where o.userId = :userId and (:status is null or o.status = :status) and o.isDeleted = false order by o.createdAt desc")
    Page<Order> findVisibleCustomerHistoryByUserIdAndStatus(@Param("userId") Long userId, @Param("status") OrderStatus status, Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @Query("select o from Order o where o.isDeleted = false and o.shipperId = :shipperId and (:status is null or o.status = :status) and o.status in (com.core.beautyshop.modules.order.domain.enums.OrderStatus.SHIPPED, com.core.beautyshop.modules.order.domain.enums.OrderStatus.DELIVERED, com.core.beautyshop.modules.order.domain.enums.OrderStatus.DELIVERY_FAILED, com.core.beautyshop.modules.order.domain.enums.OrderStatus.RETURNING_TO_SENDER) order by o.createdAt desc, o.id desc")
    Page<Order> findShipperDeliveries(@Param("shipperId") Long shipperId, @Param("status") OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    String SHIPPER_HISTORY = """
            from Order o where o.isDeleted = false and o.shipperId = :shipperId
            and (o.deliveredAt is not null or o.shipperAssignedAt is not null or o.status in (SHIPPED, DELIVERED, DELIVERY_FAILED))
            and coalesce(o.deliveredAt, o.shipperAssignedAt, o.createdAt) >= :from
            and coalesce(o.deliveredAt, o.shipperAssignedAt, o.createdAt) < :to
            """;

    String SHIPPER_HISTORY_WITH_FAILURES = """
            from Order o left join o.statusHistories h on h.shipperId = :shipperId
                and (o.shipperId is null or o.shipperId <> :shipperId)
                and h.isDeleted = false and h.status = com.core.beautyshop.modules.order.domain.enums.OrderStatus.DELIVERY_FAILED and h.createdAt >= :from and h.createdAt < :to
            where o.isDeleted = false and (
                (o.shipperId = :shipperId
                    and (o.deliveredAt is not null or o.shipperAssignedAt is not null
                        or o.status in (com.core.beautyshop.modules.order.domain.enums.OrderStatus.SHIPPED,
                            com.core.beautyshop.modules.order.domain.enums.OrderStatus.DELIVERED,
                            com.core.beautyshop.modules.order.domain.enums.OrderStatus.DELIVERY_FAILED,
                            com.core.beautyshop.modules.order.domain.enums.OrderStatus.RETURNING_TO_SENDER))
                    and coalesce(o.deliveredAt, o.shipperAssignedAt, o.createdAt) >= :from
                    and coalesce(o.deliveredAt, o.shipperAssignedAt, o.createdAt) < :to
                    and (:status is null or o.status = :status))
                or (h.id is not null
                    and (:status is null or h.status = :status)))
            """;

    interface ShipperHistoryEntry {
        Order getDelivery();
        OrderStatusHistory getPreviousFailure();
    }

    @Query(value = "select o as delivery, h as previousFailure " + SHIPPER_HISTORY_WITH_FAILURES + """
            order by coalesce(h.createdAt, o.deliveredAt, o.shipperAssignedAt, o.createdAt) desc, o.id desc, h.id desc
            """, countQuery = "select count(o) " + SHIPPER_HISTORY_WITH_FAILURES)
    Page<ShipperHistoryEntry> findShipperHistory(Long shipperId, Instant from, Instant to,
                                                OrderStatus status, Pageable pageable);

    interface DeliveryTotals {
        long getTotalOrders();
        long getDeliveredOrders();
        BigDecimal getTotalRevenue();
        BigDecimal getTotalCodCollected();
        BigDecimal getTotalDeliveryFees();
    }

    @Query("""
            select count(o) as totalOrders,
                   count(case when o.deliveredAt is not null then 1 end) as deliveredOrders,
                   coalesce(sum(case when o.deliveredAt is not null then o.totalAmount else 0 end), 0) as totalRevenue,
                   coalesce(sum(case when o.deliveredAt is not null then o.shipperCodCollected else 0 end), 0) as totalCodCollected,
                   coalesce(sum(case when o.deliveredAt is not null then o.shippingFee else 0 end), 0) as totalDeliveryFees
            """ + SHIPPER_HISTORY)
    DeliveryTotals getShipperDeliveryTotals(Long shipperId, Instant from, Instant to);

    @Query("""
            select o.deliveredAt from Order o where o.isDeleted = false and o.shipperId = :shipperId
            and o.deliveredAt >= :from and o.deliveredAt < :to
            """)
    List<Instant> findShipperDeliveryTimes(Long shipperId, Instant from, Instant to);
}
