package com.core.beautyshop.modules.payment.application.service;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
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

    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("(ORD-[A-Z0-9]{8})");

    @Override
    @Transactional
    public void processSePayWebhook(SePayWebhookRequest request) {
        log.info("Received SePay webhook: {}", request);

        if (!"in".equalsIgnoreCase(request.getTransferType())) {
            log.info("Ignored webhook because transferType is not 'in': {}", request.getTransferType());
            return;
        }

        String content = request.getContent();
        if (content == null || content.trim().isEmpty()) {
            log.warn("Ignored webhook because content is empty");
            return;
        }

        Matcher matcher = ORDER_NUMBER_PATTERN.matcher(content);
        if (!matcher.find()) {
            log.warn("Ignored webhook because no matching ORD-XXXXXXXX found in content: {}", content);
            return;
        }

        String orderNumber = matcher.group(1);
        log.info("Found order number: {} in transfer content", orderNumber);

        BigDecimal transferAmount = request.getTransferAmount() != null ? request.getTransferAmount() : BigDecimal.ZERO;
        boolean success = orderFacade.markOrderAsPaid(orderNumber, transferAmount, request.getReferenceCode());
        if (success) {
            log.info("Successfully processed payment webhook for order: {}", orderNumber);
        } else {
            log.warn("Failed or partially paid for order: {}", orderNumber);
        }
    }
}
