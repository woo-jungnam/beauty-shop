package com.core.beautyshop.modules.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_product_interactions",
    indexes = {
        @Index(name = "idx_upi_user_action", columnList = "user_id, action_type"),
        @Index(name = "idx_upi_session_action", columnList = "session_id, action_type"),
        @Index(name = "idx_upi_product", columnList = "product_id"),
        @Index(name = "idx_upi_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProductInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
