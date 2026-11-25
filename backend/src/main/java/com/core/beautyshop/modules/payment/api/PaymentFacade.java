package com.core.beautyshop.modules.payment.api;

import com.core.beautyshop.modules.payment.api.dto.BankReceipt;
import com.core.beautyshop.modules.payment.api.dto.CodSettlementTransactionDto;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;

public interface PaymentFacade {
    boolean recordCarrierRemittance(String orderNumber, BigDecimal amount, String reference, String reason, Long actor);
    void recordCodCollection(String orderNumber, BigDecimal amount);
    boolean recordCashCollection(String orderNumber, BigDecimal amount, String receiptKey, Long receivedBy);
    boolean recordManualCollection(String orderNumber, BigDecimal amount, String reference, Long actor);
    PaymentInstruction processPayment(PaymentMethod method, PaymentOrderDto orderDto);
    BankReceipt lockBankReceipt(String reference);
    void assignBankReceipt(Long transactionId, String target, String status);

    default Page<CodSettlementTransactionDto> searchCodSettlements(String keyword, Instant from, Instant to, Pageable pageable) {
        return Page.empty(pageable);
    }
}
