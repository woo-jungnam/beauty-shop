package com.core.beautyshop.modules.promotion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    long countByVoucherIdAndUserIdAndIsDeletedFalse(Long voucherId, Long userId);
    boolean existsByOrderIdAndIsDeletedFalse(Long orderId);
    Optional<VoucherUsage> findByOrderIdAndIsDeletedFalse(Long orderId);
}
