package com.core.beautyshop.modules.cart.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cart c where c.userId = :userId")
    Optional<Cart> findByUserIdForUpdate(Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cart c where c.sessionId = :sessionId and c.userId is null")
    Optional<Cart> findGuestBySessionIdForUpdate(String sessionId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cart c where c.id = :id")
    Optional<Cart> findByIdForUpdate(Long id);
    @org.springframework.data.jpa.repository.Query("select c from Cart c where c.sessionId = :sessionId and c.userId is null")
    Optional<Cart> findBySessionId(String sessionId);
}
