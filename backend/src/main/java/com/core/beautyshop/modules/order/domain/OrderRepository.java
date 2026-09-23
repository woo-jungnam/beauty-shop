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
public interface OrderRepository extends JpaRepository<Order, Long> {

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

    @Query("select o.id from Order o where o.status in (com.core.beautyshop.modules.order.domain.enums.OrderStatus.PENDING, com.core.beautyshop.modules.order.domain.enums.OrderStatus.CONFIRMED) "
            + "and o.paymentMethod = com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK "
            + "and o.paymentDeadline <= :now order by o.id")
    java.util.List<Long> findExpiredPaymentIds(java.time.Instant now, Pageable pageable);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

