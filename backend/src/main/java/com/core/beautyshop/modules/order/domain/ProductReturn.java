package com.core.beautyshop.modules.order.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_returns")
@Getter
@Setter
@NoArgsConstructor
public class ProductReturn extends Base {
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 20)
    private String status = "REQUESTED";

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "lines_json", nullable = false, columnDefinition = "TEXT")
    private String linesJson;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "receipt_reference", length = 100)
    private String receiptReference;

    @Column(name = "refund_reference", length = 100)
    private String refundReference;

    @Column(name = "request_key", length = 100, unique = true)
    private String requestKey;

    @Column(name = "replacement_order_id")
    private Long replacementOrderId;

    @Column(name = "replacement_reason", length = 500)
    private String replacementReason;

    @Column(name = "replacement_linked_at")
    private Instant replacementLinkedAt;

    @Column(name = "replacement_linked_by")
    private Long replacementLinkedBy;

    @Column(name = "refund_method", nullable = false, length = 30)
    private String refundMethod = "BANK_TRANSFER";

    @Column(name = "refund_bank_bin", length = 20)
    private String refundBankBin;

    @Column(name = "refund_bank_name", length = 100)
    private String refundBankName;

    @Column(name = "refund_account_number", length = 50)
    private String refundAccountNumber;

    @Column(name = "refund_account_name", length = 120)
    private String refundAccountName;

    @Column(name = "refund_voucher_code", length = 50)
    private String refundVoucherCode;

    @Column(name = "return_method", nullable = false, length = 30)
    private String returnMethod = "SELF_SHIP";

    @Column(name = "pickup_shipper_id")
    private Long pickupShipperId;

    @Column(name = "pickup_shipper_name", length = 100)
    private String pickupShipperName;

    @Column(name = "pickup_shipper_phone", length = 20)
    private String pickupShipperPhone;

    @Column(name = "pickup_assigned_at")
    private Instant pickupAssignedAt;

    @Column(name = "pickup_started_at")
    private Instant pickupStartedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "pickup_proof_url", length = 500)
    private String pickupProofUrl;

    @Column(name = "pickup_notes", length = 500)
    private String pickupNotes;

    @Column(name = "pickup_failure_reason", length = 500)
    private String pickupFailureReason;

    @Column(name = "pickup_attempt_count", nullable = false)
    private int pickupAttemptCount = 0;

    @Column(name = "handover_requested_at")
    private Instant handoverRequestedAt;

    @Column(name = "handover_notes", length = 500)
    private String handoverNotes;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "received_by")
    private Long receivedBy;
}
