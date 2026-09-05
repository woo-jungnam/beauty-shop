package com.core.beautyshop.modules.payment.domain;

import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "reference_code", nullable = false, unique = true, length = 100)
    private String referenceCode;

    @Column(name = "gateway", nullable = false, length = 50)
    @Builder.Default
    private String gateway = "SEPAY";

    @Column(name = "transfer_type", nullable = false, length = 20)
    private String transferType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "accumulated_amount", precision = 14, scale = 2)
    private BigDecimal accumulatedAmount;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "sub_account", length = 50)
    private String subAccount;

    @Column(name = "transaction_date", length = 50)
    private String transactionDate;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "JSON")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
