package com.core.beautyshop.modules.procurement.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="procurement_movements") @Getter @Setter @NoArgsConstructor
public class ProcurementMovement extends Base {
 @Column(name="purchase_order_id",nullable=false)private Long purchaseOrderId;
 @Column(nullable=false,unique=true,length=100)private String reference;
 @Column(nullable=false,length=20)private String kind;
 @Column(nullable=false,precision=14,scale=2)private BigDecimal amount;
 @Column(nullable=false,length=500)private String reason;
 @Column(name="lines_json",nullable=false,columnDefinition="TEXT")private String linesJson;
}
