package com.core.beautyshop.modules.payment.application.facade;

import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.BankReceipt;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategy;
import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategyFactory;
import com.core.beautyshop.modules.payment.domain.PaymentTransaction;
import com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PaymentFacadeImpl implements PaymentFacade {

    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentTransactionRepository transactions;

    @Value("${sepay.bank.account-number}")
    private String bankAccountNumber;

    @Override
    @Transactional
    public boolean recordCarrierRemittance(String orderNumber, BigDecimal amount, String reference, String reason, Long actor) {
        var prior = transactions.findByReferenceCode(reference);
        if (prior.isPresent()) {
            if (!prior.get().getOrderNumber().equals(orderNumber) || prior.get().getAmount().compareTo(amount) != 0 || !"COD".equals(prior.get().getGateway())) {
                throw new BusinessException("Settlement reference conflicts");
            }
            return false;
        }
        transactions.saveAndFlush(PaymentTransaction.builder()
                .orderNumber(orderNumber)
                .referenceCode(reference)
                .gateway("COD")
                .transferType("in")
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .rawPayload("{\"source\":\"CARRIER_REMITTANCE\",\"receivedBy\":" + actor + "}")
                .content(reason)
                .build());
        return true;
    }

    @Override
    @Transactional
    public boolean recordCashCollection(String orderNumber, BigDecimal amount, String receiptKey, Long receivedBy) {
        if (orderNumber == null || amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0 || receivedBy == null
                || receiptKey == null || receiptKey.isBlank() || receiptKey.length() > 128) {
            throw new BusinessException("A positive whole-VND amount, receipt key and cashier are required");
        }
        String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(receiptKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
        String reference = "CASH-" + orderNumber + "-" + digest.substring(0, 32);
        var previous = transactions.findByReferenceCode(reference);
        if (previous.isPresent()) {
            if (!orderNumber.equals(previous.get().getOrderNumber()) || amount.compareTo(previous.get().getAmount()) != 0
                    || !"CASH".equals(previous.get().getGateway())) {
                throw new BusinessException("Receipt key was already used for a different cash collection");
            }
            return false;
        }
        transactions.save(PaymentTransaction.builder()
                .orderNumber(orderNumber)
                .referenceCode(reference)
                .gateway("CASH")
                .transferType("in")
                .amount(amount)
                .rawPayload("{\"source\":\"SPA_VISIT_CHECKOUT\",\"receivedBy\":" + receivedBy + "}")
                .content("Cash collected at Spa reception")
                .status(TransactionStatus.SUCCESS)
                .build());
        return true;
    }

    @Override
    @Transactional
    public void recordCodCollection(String orderNumber, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw new BusinessException("Invalid COD amount");
        if (amount.signum() == 0) return;
        String reference = "COD-" + orderNumber;
        var previous = transactions.findByReferenceCode(reference);
        if (previous.isPresent()) {
            if (!orderNumber.equals(previous.get().getOrderNumber()) || amount.compareTo(previous.get().getAmount()) != 0) {
                throw new BusinessException("Conflicting COD collection reference");
            }
            return;
        }
        transactions.save(PaymentTransaction.builder()
                .orderNumber(orderNumber)
                .referenceCode(reference)
                .gateway("COD")
                .transferType("in")
                .amount(amount)
                .rawPayload("{\"source\":\"DELIVERY_CONFIRMATION\"}")
                .content("COD balance collected on delivery")
                .status(TransactionStatus.SUCCESS)
                .build());
    }

    @Override
    @Transactional
    public BankReceipt lockBankReceipt(String reference) {
        var tx = transactions.findByReferenceCodeForUpdate(reference).orElseThrow(() ->
                new BusinessException("Chưa có giao dịch SePay để đối chiếu"));
        if (!"SEPAY".equals(tx.getGateway()) || !"in".equalsIgnoreCase(tx.getTransferType())
                || tx.getAmount().signum() <= 0 || tx.getAmount().stripTrailingZeros().scale() > 0
                || tx.getAmount().compareTo(new BigDecimal("99999999")) > 0
                || bankAccountNumber == null || !bankAccountNumber.equals(tx.getAccountNumber())
                || tx.getStatus() == TransactionStatus.FAILED) {
            throw new BusinessException("Giao dịch không phải tiền vào SePay hợp lệ");
        }
        return new BankReceipt(tx.getId(), tx.getReferenceCode(), tx.getAmount(), tx.getOrderNumber());
    }

    @Override
    @Transactional
    public void assignBankReceipt(Long transactionId, String target, String status) {
        var tx = transactions.findByIdForUpdate(transactionId).orElseThrow();
        if (!"UNKNOWN".equals(tx.getOrderNumber()) && !target.equals(tx.getOrderNumber())) {
            throw new BusinessException("Giao dịch đã gắn với mã thanh toán khác");
        }
        tx.setOrderNumber(target);
        tx.setStatus(TransactionStatus.valueOf(status));
    }

    @Override
    public PaymentInstruction processPayment(PaymentMethod method, PaymentOrderDto orderDto) {
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(method);
        return strategy.processPayment(orderDto);
    }
}
