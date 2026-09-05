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
        log.info("========== [EMAIL ĐÃ ĐƯỢC GỬI QUA DỊCH VỤ THÔNG BÁO] ==========");
        log.info("Gửi tới: {} <{}>", emailMessageDto.getRecipientName(), emailMessageDto.getRecipientEmail());
        log.info("Tiêu đề: {}", emailMessageDto.getSubject());
        log.info("Mẫu (Template): {}", emailMessageDto.getTemplateCode());
        log.info("Tham số: {}", emailMessageDto.getParameters());
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
        log.info("Đã gửi thông báo: Đơn hàng #{} thay đổi trạng thái từ {} sang {}", orderNumber, previousStatus, newStatus);
    }

    @Override
    public void sendOrderCancelledNotification(Long orderId, String orderNumber) {
        log.info("Đã gửi thông báo: Đơn hàng #{} đã được hủy thành công", orderNumber);
    }
}
