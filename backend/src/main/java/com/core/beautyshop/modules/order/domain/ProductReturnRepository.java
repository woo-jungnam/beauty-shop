package com.core.beautyshop.modules.order.domain;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ProductReturnRepository extends JpaRepository<ProductReturn,Long> {
    List<ProductReturn> findByOrderIdOrderByIdDesc(Long orderId);
    Optional<ProductReturn> findByRequestKey(String key);
    @Query("select r.orderId from ProductReturn r where r.id=:id") Optional<Long> findOrderId(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ProductReturn r where r.id=:id") Optional<ProductReturn> lock(Long id);
}
