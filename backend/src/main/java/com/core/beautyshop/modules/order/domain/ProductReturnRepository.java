package com.core.beautyshop.modules.order.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductReturnRepository extends JpaRepository<ProductReturn, Long> {
    List<ProductReturn> findByOrderIdOrderByIdDesc(Long orderId);

    List<ProductReturn> findByOrderIdInOrderByIdDesc(Collection<Long> orderIds);

    Optional<ProductReturn> findByRequestKey(String key);

    @Query("select r.orderId from ProductReturn r where r.id=:id")
    Optional<Long> findOrderId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProductReturn r where r.id=:id")
    Optional<ProductReturn> lock(@Param("id") Long id);

    List<ProductReturn> findByStatusIn(Collection<String> statuses);

    @Query("""
            select r from ProductReturn r
            where r.isDeleted = false
              and (:status is null or r.status = :status)
              and (:returnMethod is null or r.returnMethod = :returnMethod)
              and (:pickupShipperId is null or r.pickupShipperId = :pickupShipperId)
            order by r.id desc
            """)
    Page<ProductReturn> searchAdminReturns(
            @Param("status") String status,
            @Param("returnMethod") String returnMethod,
            @Param("pickupShipperId") Long pickupShipperId,
            Pageable pageable);

    @Query("""
            select r from ProductReturn r, Order o
            where r.orderId = o.id
              and r.isDeleted = false
              and o.isDeleted = false
              and r.pickupShipperId = :shipperId
              and r.status in :statuses
            order by case
                when r.status = 'PICKUP_ASSIGNED' then 0
                when r.status = 'PICKED_UP' then 1
                else 2
            end, r.id desc
            """)
    Page<ProductReturn> findShipperPickups(
            @Param("shipperId") Long shipperId,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable);

    @Query("""
            select r from ProductReturn r, Order o
            where r.orderId = o.id
              and r.isDeleted = false
              and o.isDeleted = false
              and r.pickupShipperId = :shipperId
              and r.status in :statuses
              and (lower(o.orderNumber) like :pattern
                   or lower(o.customerName) like :pattern
                   or lower(o.customerPhone) like :pattern
                   or lower(o.shippingAddress) like :pattern
                   or lower(r.reason) like :pattern)
            order by case
                when r.status = 'PICKUP_ASSIGNED' then 0
                when r.status = 'PICKED_UP' then 1
                else 2
            end, r.id desc
            """)
    Page<ProductReturn> findShipperPickupsByKeyword(
            @Param("shipperId") Long shipperId,
            @Param("statuses") Collection<String> statuses,
            @Param("pattern") String pattern,
            Pageable pageable);
}
