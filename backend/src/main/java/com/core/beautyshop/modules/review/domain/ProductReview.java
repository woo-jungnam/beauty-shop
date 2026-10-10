package com.core.beautyshop.modules.review.domain;

import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "product_reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductReview extends Base {
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "order_id")
    private Long orderId;
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column
    private Integer rating;
    @Column(name = "is_verified_purchase", nullable = false)
    @Builder.Default
    private Boolean isVerifiedPurchase = false;
    @Column(length = 150)
    private String title;
    @Column(nullable = false, length = 2000)
    private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;
    @Column(name = "admin_reply", length = 2000)
    private String adminReply;
    @Column(name = "replied_at")
    private Instant repliedAt;
    @Column(name = "approved_at")
    private Instant approvedAt;
}
