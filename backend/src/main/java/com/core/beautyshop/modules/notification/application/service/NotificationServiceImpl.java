package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final IdentityFacade identityFacade;

    @Override
    public void sendEmail(EmailMessageDto emailMessageDto) {
        log.info("========== [EMAIL DISPATCHED VIA NOTIFICATION SERVICE] ==========");
        log.info("To: {} <{}>", emailMessageDto.getRecipientName(), emailMessageDto.getRecipientEmail());
        log.info("Subject: {}", emailMessageDto.getSubject());
        log.info("Template: {}", emailMessageDto.getTemplateCode());
        log.info("Parameters: {}", emailMessageDto.getParameters());
        log.info("==================================================================");
    }

    @Override
    public void sendOrderConfirmationNotification(Long orderId, String orderNumber, Long userId, BigDecimal totalAmount) {
        String recipientEmail = "guest@beautyshop.local";
        String recipientName = "Quý khách";

        if (userId != null) {
            UserSummaryDto userSummary = identityFacade.findUserSummaryById(userId).orElse(null);
            if (userSummary != null) {
                recipientEmail = userSummary.getEmail() != null ? userSummary.getEmail() : recipientEmail;
                recipientName = userSummary.getFullName() != null ? userSummary.getFullName() : userSummary.getUsername();
            }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("orderId", orderId);
        params.put("orderNumber", orderNumber);
        params.put("totalAmount", totalAmount);

        EmailMessageDto email = EmailMessageDto.builder()
                .recipientEmail(recipientEmail)
                .recipientName(recipientName)
                .subject("Xác nhận đặt hàng thành công #" + orderNumber)
                .templateCode("ORDER_CONFIRMATION")
                .parameters(params)
                .build();

        sendEmail(email);
    }

    @Override
    public void sendOrderStatusUpdateNotification(Long orderId, String orderNumber, String previousStatus, String newStatus) {
        log.info("Notification sent: Order #{} changed status from {} to {}", orderNumber, previousStatus, newStatus);
    }

    @Override
    public void sendOrderCancelledNotification(Long orderId, String orderNumber) {
        log.info("Notification sent: Order #{} has been cancelled successfully", orderNumber);
    }
}
