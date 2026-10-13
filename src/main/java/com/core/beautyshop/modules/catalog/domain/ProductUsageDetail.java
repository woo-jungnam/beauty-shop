package com.core.beautyshop.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "product_usage_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductUsageDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "when_to_use", columnDefinition = "json", nullable = false)
    private List<String> whenToUse;

    @Column(name = "frequency", length = 100, nullable = false)
    private String frequency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "instructions", columnDefinition = "json", nullable = false)
    private List<String> instructions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "json", nullable = false)
    private List<String> warnings;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
