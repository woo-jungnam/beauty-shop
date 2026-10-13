package com.core.beautyshop.modules.order.domain;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import com.core.beautyshop.modules.order.domain.enums.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Order> {

    @Query("select count(o) > 0 from Order o join o.items i, ProductVariant v where o.id = :orderId and o.userId = :userId and o.status = com.core.beautyshop.modules.order.domain.enums.OrderStatus.DELIVERED and v.id = i.productVariantId and v.product.id = :productId")
    boolean existsDeliveredProductPurchase(Long orderId, Long userId, Long productId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByOrderNumber(String orderNumber);

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
    java.util.List<Long> findExpiredPaymentIdsAfter(java.time.Instant now, Long after, Pageable pageable);

    default java.util.List<Long> findExpiredPaymentIds(java.time.Instant now, Pageable pageable) {
        return findExpiredPaymentIdsAfter(now, 0L, pageable);
    }

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select o from Order o where o.userId = :userId and o.isDeleted = false and (o.paymentMethod <> com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK or o.paidAt is not null) order by o.createdAt desc")
    Page<Order> findVisibleCustomerHistoryByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

}

