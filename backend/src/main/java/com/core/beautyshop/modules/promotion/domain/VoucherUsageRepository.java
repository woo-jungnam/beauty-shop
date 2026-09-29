package com.core.beautyshop.modules.promotion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    long countByVoucherIdAndUserIdAndIsDeletedFalse(Long voucherId, Long userId);
    boolean existsByOrderId(Long orderId);
}
