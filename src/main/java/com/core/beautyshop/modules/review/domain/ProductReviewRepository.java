package com.core.beautyshop.modules.review.domain;

import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Page<ProductReview> findByProductIdAndStatusAndIsDeletedFalse(Long productId, ReviewStatus status, Pageable pageable);
    Page<ProductReview> findByStatusAndIsDeletedFalse(ReviewStatus status, Pageable pageable);
    Page<ProductReview> findByIsDeletedFalse(Pageable pageable);
    boolean existsByProductIdAndUserIdAndOrderId(Long productId, Long userId, Long orderId);
    @Query("select new com.core.beautyshop.modules.review.domain.ApprovedReviewSummary(avg(r.rating), count(r)) from ProductReview r where r.productId = :productId and r.status = com.core.beautyshop.modules.review.domain.enums.ReviewStatus.APPROVED and r.isDeleted = false and r.isVerifiedPurchase = true and r.rating is not null")
    ApprovedReviewSummary summarizeApproved(@Param("productId") Long productId);

    @Query("select r.productId from ProductReview r where r.id = :id and r.isDeleted = false")
    Optional<Long> findProductIdForReview(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProductReview r where r.id = :id and r.isDeleted = false")
    Optional<ProductReview> findByIdForUpdate(@Param("id") Long id);
}
