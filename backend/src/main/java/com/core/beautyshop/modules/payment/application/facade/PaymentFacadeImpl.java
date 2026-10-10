package com.core.beautyshop.modules.payment.application.facade;

import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategy;
import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategyFactory;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentFacadeImpl implements PaymentFacade {

    private final PaymentStrategyFactory paymentStrategyFactory;
    private final com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository transactions;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean recordCashCollection(String orderNumber, java.math.BigDecimal amount, String receiptKey, Long receivedBy) {
        if (orderNumber == null || amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0 || receivedBy == null
                || receiptKey == null || receiptKey.isBlank() || receiptKey.length() > 128) {
            throw new com.core.beautyshop.shared.exception.BusinessException("A positive whole-VND amount, receipt key and cashier are required");
        }
        String digest;
        try {
            digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(receiptKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
        String reference = "CASH-" + orderNumber + "-" + digest.substring(0, 32);
        var previous = transactions.findByReferenceCode(reference);
        if (previous.isPresent()) {
            if (!orderNumber.equals(previous.get().getOrderNumber()) || amount.compareTo(previous.get().getAmount()) != 0
                    || !"CASH".equals(previous.get().getGateway())) {
                throw new com.core.beautyshop.shared.exception.BusinessException("Receipt key was already used for a different cash collection");
            }
            return false;
        }
        transactions.save(com.core.beautyshop.modules.payment.domain.PaymentTransaction.builder()
                .orderNumber(orderNumber).referenceCode(reference).gateway("CASH").transferType("in").amount(amount)
                .rawPayload("{\"source\":\"SPA_VISIT_CHECKOUT\",\"receivedBy\":" + receivedBy + "}")
                .content("Cash collected at Spa reception")
                .status(com.core.beautyshop.modules.payment.domain.enums.TransactionStatus.SUCCESS).build());
        return true;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void recordCodCollection(String orderNumber, java.math.BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw new com.core.beautyshop.shared.exception.BusinessException("Invalid COD amount");
        if (amount.signum() == 0) return;
        String reference = "COD-" + orderNumber;
        var previous = transactions.findByReferenceCode(reference);
        if (previous.isPresent()) {
            if (!orderNumber.equals(previous.get().getOrderNumber()) || amount.compareTo(previous.get().getAmount()) != 0)
                throw new com.core.beautyshop.shared.exception.BusinessException("Conflicting COD collection reference");
            return;
        }
        transactions.save(com.core.beautyshop.modules.payment.domain.PaymentTransaction.builder()
                .orderNumber(orderNumber).referenceCode(reference).gateway("COD").transferType("in").amount(amount)
                .rawPayload("{\"source\":\"DELIVERY_CONFIRMATION\"}")
                .content("COD balance collected on delivery")
                .status(com.core.beautyshop.modules.payment.domain.enums.TransactionStatus.SUCCESS).build());
    }

    @Override
    public PaymentInstruction processPayment(PaymentMethod method, PaymentOrderDto orderDto) {
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(method);
        return strategy.processPayment(orderDto);
    }
}
