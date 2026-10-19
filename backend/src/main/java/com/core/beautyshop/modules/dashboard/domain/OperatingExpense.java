package com.core.beautyshop.modules.dashboard.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="operating_expenses") @Getter @Setter @NoArgsConstructor
public class OperatingExpense extends Base {
 @Column(nullable=false,unique=true,length=100)private String reference;
 @Column(nullable=false,length=50)private String category;
 @Column(nullable=false,length=500)private String description;
 @Column(nullable=false,precision=14,scale=2)private BigDecimal amount;
 @Column(name="incurred_at",nullable=false)private Instant incurredAt;
}
