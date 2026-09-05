package com.core.beautyshop.modules.payment.application.service;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.domain.PaymentTransaction;
import com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final OrderFacade orderFacade;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("(ORD-[A-Z0-9]{8})");

    @Override
    @Transactional
    public void processSePayWebhook(SePayWebhookRequest request) {
        log.info("Đã nhận webhook SePay: referenceCode={}, gateway={}, transferAmount={}",
                request.getReferenceCode(), request.getGateway(), request.getTransferAmount());

        // 1. Idempotency Check: Chống xử lý trùng lặp Webhook
        if (request.getReferenceCode() != null &&
                paymentTransactionRepository.existsByReferenceCode(request.getReferenceCode())) {
            log.info("Kiểm tra trùng lặp: Webhook với referenceCode={} đã được xử lý. Bỏ qua trùng lặp.",
                    request.getReferenceCode());
            return;
        }

        String rawJson = "{}";
        try {
            rawJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.warn("Lỗi khi tuần tự hóa payload của webhook sang JSON: {}", e.getMessage());
        }

        if (!"in".equalsIgnoreCase(request.getTransferType())) {
            log.info("Bỏ qua webhook do loại giao dịch không phải là 'in' (tiền vào): {}", request.getTransferType());
            saveTransactionRecord("UNKNOWN", request, rawJson, TransactionStatus.IGNORED);
            return;
        }

        String content = request.getContent();
        if (content == null || content.trim().isEmpty()) {
            log.warn("Bỏ qua webhook do nội dung chuyển khoản rỗng");
            saveTransactionRecord("UNKNOWN", request, rawJson, TransactionStatus.IGNORED);
            return;
        }

        Matcher matcher = ORDER_NUMBER_PATTERN.matcher(content);
        if (!matcher.find()) {
            log.warn("Bỏ qua webhook do không tìm thấy mã đơn hàng ORD-XXXXXXXX trong nội dung: {}", content);
            saveTransactionRecord("UNKNOWN", request, rawJson, TransactionStatus.IGNORED);
            return;
        }

        String orderNumber = matcher.group(1);
        log.info("Tìm thấy mã đơn hàng: {} trong nội dung chuyển khoản", orderNumber);

        BigDecimal transferAmount = request.getTransferAmount() != null ? request.getTransferAmount() : BigDecimal.ZERO;
        boolean success = orderFacade.markOrderAsPaid(orderNumber, transferAmount, request.getReferenceCode());

        TransactionStatus status = success ? TransactionStatus.SUCCESS : TransactionStatus.PARTIALLY_PAID;
        saveTransactionRecord(orderNumber, request, rawJson, status);

        if (success) {
            log.info("Xử lý thành công webhook thanh toán cho đơn hàng: {}", orderNumber);
        } else {
            log.warn("Thanh toán thất bại hoặc mới thanh toán một phần cho đơn hàng: {}", orderNumber);
        }
    }

    private void saveTransactionRecord(String orderNumber, SePayWebhookRequest request,
                                      String rawJson, TransactionStatus status) {
        try {
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .orderNumber(orderNumber)
                    .referenceCode(request.getReferenceCode() != null ? request.getReferenceCode() : "N/A-" + System.currentTimeMillis())
                    .gateway(request.getGateway() != null ? request.getGateway() : "SEPAY")
                    .transferType(request.getTransferType() != null ? request.getTransferType() : "in")
                    .amount(request.getTransferAmount() != null ? request.getTransferAmount() : BigDecimal.ZERO)
                    .accumulatedAmount(request.getAccumulated())
                    .accountNumber(request.getAccountNumber())
                    .subAccount(request.getSubAccount())
                    .transactionDate(request.getTransactionDate())
                    .content(request.getContent())
                    .rawPayload(rawJson)
                    .status(status)
                    .build();

            paymentTransactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("Lỗi khi lưu bản ghi nhật ký giao dịch thanh toán: {}", e.getMessage(), e);
        }
    }
}
