package com.core.beautyshop.modules.review.domain;

import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Page<ProductReview> findByProductIdAndStatusAndIsDeletedFalse(Long productId, ReviewStatus status, Pageable pageable);
    Page<ProductReview> findByStatusAndIsDeletedFalse(ReviewStatus status, Pageable pageable);
    Page<ProductReview> findByIsDeletedFalse(Pageable pageable);
    boolean existsByProductIdAndUserIdAndOrderId(Long productId, Long userId, Long orderId);
    @Query("select coalesce(avg(r.rating), 0), count(r) from ProductReview r where r.productId = :productId and r.status = com.core.beautyshop.modules.review.domain.enums.ReviewStatus.APPROVED and r.isDeleted = false")
    Object[] summarizeApproved(@Param("productId") Long productId);
}
