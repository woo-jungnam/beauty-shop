package com.core.beautyshop.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "product_skin_compatibility",
    uniqueConstraints = @UniqueConstraint(name = "uk_prod_skintype", columnNames = {"product_id", "skin_type_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSkinCompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skin_type_id", nullable = false)
    private SkinTypeEntity skinType;

    @Column(name = "is_recommended", nullable = false)
    @Builder.Default
    private Boolean isRecommended = true;

    @Column(name = "score", precision = 3, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal score = new BigDecimal("0.80");

    @Column(name = "contraindication_reason", columnDefinition = "TEXT")
    private String contraindicationReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
