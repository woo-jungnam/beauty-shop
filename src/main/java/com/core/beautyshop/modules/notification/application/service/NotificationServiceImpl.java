package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;
import com.core.beautyshop.modules.notification.domain.NotificationReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final IdentityFacade identityFacade;
    private final NotificationReceiptRepository receiptRepository;
    private final Optional<JavaMailSender> mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@beautyshop.vn}")
    private String mailFrom;

    @Override
    public void sendEmail(EmailMessageDto emailMessageDto) {
        if (!mailEnabled || mailSender.isEmpty()) {
            log.info("Email transport is not enabled or credentials not configured; template {} logged for {}: {}",
                    emailMessageDto.getTemplateCode(), emailMessageDto.getRecipientEmail(), emailMessageDto.getSubject());
            return;
        }

        try {
            JavaMailSender sender = mailSender.get();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom, "BeautyShop");
            helper.setTo(emailMessageDto.getRecipientEmail());
            helper.setSubject(emailMessageDto.getSubject());

            String html = buildEmailHtml(emailMessageDto);
            helper.setText(html, true);

            sender.send(message);
            log.info("Đã gửi email thành công tới {} cho template {}", emailMessageDto.getRecipientEmail(), emailMessageDto.getTemplateCode());
        } catch (Exception ex) {
            log.error("Không thể gửi email qua SMTP tới {}: {}", emailMessageDto.getRecipientEmail(), ex.getMessage());
            throw new IllegalStateException("Email delivery failed; the notification will be retried", ex);
        }
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
    public void sendOrderPaidNotification(Long orderId, String orderNumber, Long userId, BigDecimal totalAmount) {
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
                .subject("Xác nhận thanh toán thành công qua chuyển khoản đơn hàng #" + orderNumber)
                .templateCode("ORDER_PAID")
                .parameters(params)
                .build();

        sendEmail(email);
    }

    @Override
    public void sendOrderStatusUpdateNotification(Long orderId, String orderNumber, String previousStatus, String newStatus) {
        log.info("Đã ghi nhận thông báo: Đơn hàng #{} thay đổi trạng thái từ {} sang {}", orderNumber, previousStatus, newStatus);
    }

    @Override
    public void sendOrderCancelledNotification(Long orderId, String orderNumber) {
        log.info("Đã ghi nhận thông báo: Đơn hàng #{} đã được hủy", orderNumber);
    }

    @Override
    @Transactional
    public int cleanupOldReceipts(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = receiptRepository.deleteProcessedBefore(cutoff);
        log.info("Đã dọn dẹp {} notification receipts cũ hơn {} ngày", deleted, retentionDays);
        return deleted;
    }

    private String buildEmailHtml(EmailMessageDto dto) {
        if ("SPA_APPOINTMENT_REMINDER".equals(dto.getTemplateCode()) || "SPA_STAFF_FOLLOW_UP".equals(dto.getTemplateCode())) {
            String recipient = org.springframework.web.util.HtmlUtils.htmlEscape(dto.getRecipientName() == null ? "Quý khách" : dto.getRecipientName());
            String subject = org.springframework.web.util.HtmlUtils.htmlEscape(dto.getSubject() == null ? "BeautyShop Spa" : dto.getSubject());
            String body = org.springframework.web.util.HtmlUtils.htmlEscape(String.valueOf(dto.getParameters().getOrDefault("body", "")))
                    .replace("\n", "<br/>");
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h2>" + subject
                    + "</h2><p>Xin chào " + recipient + ",</p><p>" + body + "</p></body></html>";
        }
        String name = dto.getRecipientName() != null ? dto.getRecipientName() : "Quý khách";
        String orderNumber = dto.getParameters() != null && dto.getParameters().get("orderNumber") != null
                ? String.valueOf(dto.getParameters().get("orderNumber")) : "";
        String amount = dto.getParameters() != null && dto.getParameters().get("totalAmount") != null
                ? String.valueOf(dto.getParameters().get("totalAmount")) + " VNĐ" : "";

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; background-color: #f9f9f9; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    <h2 style="color: #d81b60; text-align: center;">BeautyShop - Thông Báo Đơn Hàng</h2>
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Cảm ơn bạn đã mua sắm tại <strong>BeautyShop</strong>. Đơn hàng của bạn đã được ghi nhận trên hệ thống:</p>
                    <div style="background: #fce4ec; padding: 15px; border-radius: 6px; margin: 20px 0;">
                        <p style="margin: 5px 0;"><strong>Mã đơn hàng:</strong> %s</p>
                        <p style="margin: 5px 0;"><strong>Tổng thanh toán:</strong> %s</p>
                    </div>
                    <p>Chúng tôi sẽ nhanh chóng chuẩn bị và giao hàng tới bạn trong thời gian sớm nhất.</p>
                    <hr style="border: none; border-top: 1px solid #eeeeee; margin: 20px 0;" />
                    <p style="font-size: 12px; color: #888888; text-align: center;">Đây là email tự động từ hệ thống BeautyShop. Vui lòng không phản hồi email này.</p>
                </div>
            </body>
            </html>
            """.formatted(name, orderNumber, amount);
    }
}
