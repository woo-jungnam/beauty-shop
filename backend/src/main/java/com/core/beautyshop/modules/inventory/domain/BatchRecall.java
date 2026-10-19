package com.core.beautyshop.modules.inventory.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="batch_recalls") @Getter @Setter @NoArgsConstructor
public class BatchRecall extends Base {
 @Column(nullable=false,unique=true,length=100)private String reference;
 @Column(name="variant_id",nullable=false)private Long variantId;
 @Column(name="batch_code",nullable=false,length=100)private String batchCode;
 @Column(nullable=false,length=500)private String reason;
 @Column(nullable=false)private boolean active=true;
 @Column(name="closure_reason",length=500)private String closureReason;
}
