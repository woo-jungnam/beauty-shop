package com.core.beautyshop.modules.order.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="product_returns") @Getter @Setter @NoArgsConstructor
public class ProductReturn extends Base {
    @Column(name="order_id",nullable=false) private Long orderId;
    @Column(nullable=false,length=20) private String status="REQUESTED";
    @Column(nullable=false,length=1000) private String reason;
    @Column(name="decision_reason",length=1000) private String decisionReason;
    @Column(name="refund_amount",nullable=false,precision=12,scale=2) private BigDecimal refundAmount;
    @Column(name="lines_json",nullable=false,columnDefinition="TEXT") private String linesJson;
    @Column(name="evidence_json",nullable=false,columnDefinition="TEXT") private String evidenceJson;
    @Column(name="receipt_reference",length=100) private String receiptReference;
    @Column(name="refund_reference",length=100) private String refundReference;
    @Column(name="request_key",length=100,unique=true) private String requestKey;
    @Column(name="replacement_order_id") private Long replacementOrderId;
    @Column(name="replacement_reason",length=500) private String replacementReason;
    @Column(name="replacement_linked_at") private java.time.Instant replacementLinkedAt;
    @Column(name="replacement_linked_by") private Long replacementLinkedBy;
}
