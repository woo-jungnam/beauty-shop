package com.core.beautyshop.modules.inventory.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="inventory_consumptions") @Getter @Setter @NoArgsConstructor
public class InventoryConsumption extends Base {
    @Column(nullable=false, unique=true, length=100) private String reference;
    @Column(name="variant_id",nullable=false) private Long variantId;
    @Column(name="warehouse_id",nullable=false) private Long warehouseId;
    @Column(nullable=false) private int quantity;
    @Column(name="total_cost",nullable=false,precision=14,scale=2) private BigDecimal totalCost;
    @Column(name="batch_snapshot",nullable=false,columnDefinition="TEXT") private String batchSnapshot;
}
