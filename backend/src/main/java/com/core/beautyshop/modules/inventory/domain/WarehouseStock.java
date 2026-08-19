package com.core.beautyshop.modules.inventory.domain;

import com.core.beautyshop.shared.domain.Base;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "warehouse_stocks",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_warehouse_variant",
        columnNames = {"warehouse_id", "product_variant_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseStock extends Base {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "product_variant_id", nullable = false)
    private Long productVariantId;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Column(name = "min_quantity")
    @Builder.Default
    private Integer minQuantity = 0;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "location", length = 100)
    private String location;
}
