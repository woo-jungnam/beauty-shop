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
    private final com.core.beautyshop.modules.spa.api.SpaDepositFacade deposits;
    @Value("${sepay.bank.account-number}")
    private String accountNumber;

    public String reference(SePayWebhookRequest request) {
        if (request.getReferenceCode() != null && !request.getReferenceCode().isBlank()) {
            if (request.getReferenceCode().length() > 100) throw new BusinessException("Mã tham chiếu thanh toán không hợp lệ");
            return request.getReferenceCode().trim();
        }
        if (request.getId() != null && request.getId() > 0) return "SEPAY-ID-" + request.getId();
        throw new BusinessException("Mã giao dịch từ cổng thanh toán là bắt buộc");
    }

    @Transactional
    public void process(SePayWebhookRequest request, String reference) {
        var previous = transactions.findByReferenceCodeForUpdate(reference);
        if (previous.isPresent()) {
            PaymentTransaction tx = previous.get();
            if (tx.getStatus() != TransactionStatus.IGNORED || !"UNKNOWN".equals(tx.getOrderNumber())
                    || !"SEPAY".equals(tx.getGateway())) return;
            SePayWebhookRequest original;
            try {
                var stored=mapper.readTree(tx.getRawPayload());
                if(stored!=null&&stored.isTextual())stored=mapper.readTree(stored.asText());
                if(stored==null||!stored.isObject())throw new IllegalArgumentException("Stored webhook must contain a JSON object");
                original=mapper.treeToValue(stored,SePayWebhookRequest.class);
            }
            catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalArgumentException("Invalid stored webhook", exception); }
            settle(original, tx);
            return;
        }
        String payload;
        try { payload = mapper.writeValueAsString(request); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalArgumentException("Invalid webhook", exception); }
        BigDecimal amount = request.getTransferAmount() == null ? BigDecimal.ZERO : request.getTransferAmount();
        PaymentTransaction tx = PaymentTransaction.builder().referenceCode(reference).orderNumber("UNKNOWN")
                .gateway("SEPAY")
                .transferType(request.getTransferType() == null ? "unknown" : request.getTransferType())
                .amount(amount).accumulatedAmount(request.getAccumulated()).accountNumber(request.getAccountNumber())
                .subAccount(request.getSubAccount()).transactionDate(request.getTransactionDate())
                .content(request.getContent()).rawPayload(payload).status(TransactionStatus.IGNORED).build();
        transactions.saveAndFlush(tx);
        settle(request, tx);
    }

    private void settle(SePayWebhookRequest request, PaymentTransaction tx) {
        String orderNumber = PaymentTransferTarget.parse(request.getContent());
        tx.setOrderNumber(orderNumber);
        BigDecimal amount = tx.getAmount();
        if (!"in".equalsIgnoreCase(request.getTransferType())) return;
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
                || accountNumber == null || !accountNumber.equals(request.getAccountNumber())) {
            tx.setStatus(TransactionStatus.FAILED);
            return;
        }
        if ("UNKNOWN".equals(orderNumber) || "AMBIGUOUS".equals(orderNumber)) return;
        if (orderNumber.startsWith("SPADEP-")) {
            if (amount.compareTo(new BigDecimal("99999999")) > 0) {
                tx.setStatus(TransactionStatus.DEPOSIT_REVIEW);
                return;
            }
            var receipt = new com.core.beautyshop.modules.payment.api.dto.BankReceipt(tx.getId(), tx.getReferenceCode(), amount, orderNumber);
            tx.setStatus(TransactionStatus.valueOf(deposits.receiveDeposit(orderNumber, receipt)));
            return;
        }
        boolean paid = orders.markOrderAsPaid(orderNumber, amount, tx.getReferenceCode());
        String state = orders.findPaymentState(orderNumber).orElse("NOT_FOUND");
        tx.setStatus(switch (state) {
            case "NOT_FOUND" -> TransactionStatus.ORDER_NOT_FOUND;
            case "CANCELLED", "RETURNED", "RETURN_TO_SENDER", "REFUNDED_VISIT" -> TransactionStatus.CANCELLED_ORDER_RECEIVED;
            default -> paid ? TransactionStatus.SUCCESS : TransactionStatus.PARTIALLY_PAID;
        });
    }
}
