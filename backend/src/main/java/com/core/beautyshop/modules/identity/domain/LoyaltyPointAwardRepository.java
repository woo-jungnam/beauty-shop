package com.core.beautyshop.modules.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoyaltyPointAwardRepository extends JpaRepository<LoyaltyPointAward, Long> {

    boolean existsByOrderId(Long orderId);
    java.util.Optional<LoyaltyPointAward> findByOrderId(Long orderId);
}
