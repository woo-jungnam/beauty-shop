package com.core.beautyshop.modules.promotion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlashSaleOrderAllocationRepository extends JpaRepository<FlashSaleOrderAllocation, Long> {

    List<FlashSaleOrderAllocation> findByOrderIdAndReleasedAtIsNull(Long orderId);

    @Query("SELECT COALESCE(SUM(a.quantity), 0) FROM FlashSaleOrderAllocation a " +
            "WHERE a.flashSaleItemId = :itemId AND a.userId = :userId AND a.releasedAt IS NULL")
    int countUserPurchasedQuantity(@Param("itemId") Long itemId, @Param("userId") Long userId);
}
