package com.core.beautyshop.modules.payment.application.service;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.domain.*;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor
public class PaymentWebhookProcessor {
    private final OrderFacade orders;
    private final PaymentTransactionRepository transactions;
    private final ObjectMapper mapper;
    @Value("${sepay.bank.account-number}")
    private String accountNumber;
    private static final Pattern ORDER = Pattern.compile("\\b(ORD-(?:[A-Z0-9]{32}|[A-Z0-9]{8}))\\b");

    public String reference(SePayWebhookRequest request) {
        if (request.getReferenceCode() != null && !request.getReferenceCode().isBlank()) {
            if (request.getReferenceCode().length() > 100) throw new BusinessException("Invalid payment reference");
            return request.getReferenceCode().trim();
        }
        if (request.getId() != null && request.getId() > 0) return "SEPAY-ID-" + request.getId();
        throw new BusinessException("A stable provider transaction identifier is required");
    }

    @Transactional
    public void process(SePayWebhookRequest request, String reference) {
        if (transactions.existsByReferenceCode(reference)) return;
        String payload;
        try { payload = mapper.writeValueAsString(request); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalArgumentException("Invalid webhook", exception); }
        var match = ORDER.matcher(request.getContent() == null ? "" : request.getContent().toUpperCase(java.util.Locale.ROOT));
        String orderNumber = match.find() ? match.group(1) : "UNKNOWN";
        BigDecimal amount = request.getTransferAmount() == null ? BigDecimal.ZERO : request.getTransferAmount();
        PaymentTransaction tx = PaymentTransaction.builder().referenceCode(reference).orderNumber(orderNumber)
                .gateway(request.getGateway() == null ? "SEPAY" : request.getGateway())
                .transferType(request.getTransferType() == null ? "unknown" : request.getTransferType())
                .amount(amount).accumulatedAmount(request.getAccumulated()).accountNumber(request.getAccountNumber())
                .subAccount(request.getSubAccount()).transactionDate(request.getTransactionDate())
                .content(request.getContent()).rawPayload(payload).status(TransactionStatus.IGNORED).build();
        transactions.saveAndFlush(tx);
        if (!"in".equalsIgnoreCase(request.getTransferType()) || "UNKNOWN".equals(orderNumber)) return;
        if (amount.signum() <= 0 || accountNumber == null || !accountNumber.equals(request.getAccountNumber())) {
            tx.setStatus(TransactionStatus.FAILED);
            return;
        }
        boolean paid = orders.markOrderAsPaid(orderNumber, amount, reference);
        String state = orders.findPaymentState(orderNumber).orElse("NOT_FOUND");
        tx.setStatus(switch (state) {
            case "NOT_FOUND" -> TransactionStatus.ORDER_NOT_FOUND;
            case "CANCELLED", "RETURNED" -> TransactionStatus.CANCELLED_ORDER_RECEIVED;
            default -> paid ? TransactionStatus.SUCCESS : TransactionStatus.PARTIALLY_PAID;
        });
    }
}
