package com.core.beautyshop.modules.review.domain;

import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import com.core.beautyshop.modules.review.domain.enums.ReviewSentiment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Page<ProductReview> findByProductIdAndStatusAndIsDeletedFalse(Long productId, ReviewStatus status, Pageable pageable);
    Page<ProductReview> findByStatusAndIsDeletedFalse(ReviewStatus status, Pageable pageable);
    Page<ProductReview> findByIsDeletedFalse(Pageable pageable);
    @Query("""
            select r from ProductReview r where r.isDeleted = false
                and (:status is null or r.status = :status)
                and (:sentiment is null or r.sentimentState = :sentiment)
            """)
    Page<ProductReview> findAdminReviews(@Param("status") ReviewStatus status,
            @Param("sentiment") ReviewSentiment sentiment, Pageable pageable);

    @Query("""
            select r from ProductReview r where r.isDeleted = false and r.sentimentState = :pending
                and (r.sentimentNextAttemptAt is null or r.sentimentNextAttemptAt <= :now)
            order by r.sentimentNextAttemptAt, r.id
            """)
    List<ProductReview> findSentimentBatch(@Param("pending") ReviewSentiment pending,
            @Param("now") Instant now, Pageable limit);

    @Modifying
    @Transactional
    @Query("""
            update ProductReview r set r.sentimentState = :state,
                r.sentimentAttempts = :attempt + 1, r.sentimentNextAttemptAt = :nextAttempt,
                r.sentimentModelVersion = :version, r.sentimentAnalyzedAt = :analyzedAt
            where r.id = :id and r.isDeleted = false
                and r.sentimentState = :pending and r.sentimentAttempts = :attempt
            """)
    int completeSentimentAttempt(@Param("id") Long id, @Param("attempt") int attempt,
            @Param("pending") ReviewSentiment pending, @Param("state") ReviewSentiment state,
            @Param("nextAttempt") Instant nextAttempt, @Param("version") String version,
            @Param("analyzedAt") Instant analyzedAt);

    boolean existsByProductIdAndUserIdAndOrderId(Long productId, Long userId, Long orderId);
    @Query("select new com.core.beautyshop.modules.review.domain.ApprovedReviewSummary(avg(r.rating), count(r)) from ProductReview r where r.productId = :productId and r.status = com.core.beautyshop.modules.review.domain.enums.ReviewStatus.APPROVED and r.isDeleted = false and r.isVerifiedPurchase = true and r.rating is not null")
    ApprovedReviewSummary summarizeApproved(@Param("productId") Long productId);

    @Query("select r.productId from ProductReview r where r.id = :id and r.isDeleted = false")
    Optional<Long> findProductIdForReview(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProductReview r where r.id = :id and r.isDeleted = false")
    Optional<ProductReview> findByIdForUpdate(@Param("id") Long id);
}
