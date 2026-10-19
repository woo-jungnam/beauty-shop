package com.core.beautyshop.modules.payment.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="payment_resolutions") @Getter @Setter @NoArgsConstructor
public class PaymentResolution extends Base {
 @Column(name="transaction_id",nullable=false,unique=true)private Long transactionId;
 @Column(nullable=false,length=20)private String kind;
 @Column(name="order_number",length=50)private String orderNumber;
 @Column(length=100,unique=true)private String reference;
 @Column(nullable=false,length=500)private String reason;
 @Column(nullable=false,precision=14,scale=2)private BigDecimal amount;
}
