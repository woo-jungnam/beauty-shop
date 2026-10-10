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
    @Value("${sepay.bank.account-number:0902588750}")
    private String accountNumber;
    private static final Pattern ORDER = Pattern.compile("\\bORD-?([A-Z0-9]{32}|[A-Z0-9]{8})\\b");

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
        var previous = transactions.findByReferenceCodeForUpdate(reference);
        if (previous.isPresent()) {
            PaymentTransaction tx = previous.get();
            // Retry only unmatched bank receipts; use the stored payload, never replacement money/account data.
            if (tx.getStatus() != TransactionStatus.IGNORED || !"UNKNOWN".equals(tx.getOrderNumber())
                    || !"SEPAY".equals(tx.getGateway())) return;
            SePayWebhookRequest original;
            try { original = mapper.readValue(tx.getRawPayload(), SePayWebhookRequest.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalArgumentException("Invalid stored webhook", exception); }
            settle(original, tx);
            return;
        }
        String payload;
        try { payload = mapper.writeValueAsString(request); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalArgumentException("Invalid webhook", exception); }
        BigDecimal amount = request.getTransferAmount() == null ? BigDecimal.ZERO : request.getTransferAmount();
        PaymentTransaction tx = PaymentTransaction.builder().referenceCode(reference).orderNumber("UNKNOWN")
                // The route determines the ledger source; provider bank metadata remains in rawPayload.
                .gateway("SEPAY")
                .transferType(request.getTransferType() == null ? "unknown" : request.getTransferType())
                .amount(amount).accumulatedAmount(request.getAccumulated()).accountNumber(request.getAccountNumber())
                .subAccount(request.getSubAccount()).transactionDate(request.getTransactionDate())
                .content(request.getContent()).rawPayload(payload).status(TransactionStatus.IGNORED).build();
        transactions.saveAndFlush(tx);
        settle(request, tx);
    }

    private void settle(SePayWebhookRequest request, PaymentTransaction tx) {
        var match = ORDER.matcher(request.getContent() == null ? "" : request.getContent().toUpperCase(java.util.Locale.ROOT));
        String orderNumber = match.find() ? "ORD-" + match.group(1) : "UNKNOWN";
        tx.setOrderNumber(orderNumber);
        BigDecimal amount = tx.getAmount();
        if (!"in".equalsIgnoreCase(request.getTransferType())) return;
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
                || accountNumber == null || !accountNumber.equals(request.getAccountNumber())) {
            tx.setStatus(TransactionStatus.FAILED);
            return;
        }
        if ("UNKNOWN".equals(orderNumber)) return;
        boolean paid = orders.markOrderAsPaid(orderNumber, amount, tx.getReferenceCode());
        String state = orders.findPaymentState(orderNumber).orElse("NOT_FOUND");
        tx.setStatus(switch (state) {
            case "NOT_FOUND" -> TransactionStatus.ORDER_NOT_FOUND;
            case "CANCELLED", "RETURNED", "REFUNDED_VISIT" -> TransactionStatus.CANCELLED_ORDER_RECEIVED;
            default -> paid ? TransactionStatus.SUCCESS : TransactionStatus.PARTIALLY_PAID;
        });
    }
}
