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
    name = "product_skin_concerns",
    uniqueConstraints = @UniqueConstraint(name = "uk_prod_concern", columnNames = {"product_id", "concern_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSkinConcern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concern_id", nullable = false)
    private SkinConcern concern;

    @Column(name = "score", precision = 3, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal score = new BigDecimal("1.00");

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
