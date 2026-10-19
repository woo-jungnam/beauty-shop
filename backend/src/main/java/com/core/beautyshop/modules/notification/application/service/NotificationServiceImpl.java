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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    private com.core.beautyshop.modules.notification.api.CustomerNotificationFacade customerNotifications;

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
        deliverOrder(orderId, "CREATED", "Đã tiếp nhận đơn hàng #" + orderNumber,
                "Đơn hàng của bạn đã được tiếp nhận. Tổng thanh toán: " + totalAmount + " VND.");
    }

    @Override
    public void sendOrderPaidNotification(Long orderId, String orderNumber, Long userId, BigDecimal totalAmount) {
        deliverOrder(orderId, "PAID", "Đã nhận thanh toán #" + orderNumber,
                "Đã ghi nhận thanh toán cho đơn hàng của bạn. Vui lòng xem chi tiết đơn để đối chiếu số tiền.");
    }

    @Override
    public void sendOrderStatusUpdateNotification(Long orderId, String orderNumber, String previousStatus, String newStatus) {
        deliverOrder(orderId, "STATUS_" + previousStatus + "_" + newStatus,
                "Cập nhật đơn hàng #" + orderNumber, "Trạng thái đơn hàng: " + statusLabel(newStatus) + ".");
    }

    @Override
    public void sendOrderCancelledNotification(Long orderId, String orderNumber) {
        deliverOrder(orderId, "CANCELLED", "Đã hủy đơn hàng #" + orderNumber,
                "Đơn hàng đã được hủy. Nếu có tiền đã thanh toán, khoản hoàn sẽ được xử lý và ghi nhận riêng.");
    }

    private void deliverOrder(Long orderId, String event, String title, String body) {
        var recipients = jdbc.query("select user_id, customer_email, customer_name, updated_at from orders where id=? and is_deleted=false",
                (rs, row) -> new OrderRecipient(rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                        rs.getString("customer_email"), rs.getString("customer_name"), String.valueOf(rs.getTimestamp("updated_at"))), orderId);
        if (recipients.isEmpty()) return;
        var recipient = recipients.getFirst();
        String email = recipient.email();
        String name = recipient.name();
        if (recipient.userId() != null) {
            var account = identityFacade.findUserSummaryById(recipient.userId()).orElse(null);
            if (account != null && (email == null || email.isBlank())) email = account.getEmail();
            String eventKey = "order:" + orderId + ":" + event + (event.startsWith("STATUS_") ? ":" + recipient.updatedAt() : "");
            customerNotifications.publish(recipient.userId(), eventKey, "ORDER", title, body, "profile?tab=orders&order=" + orderId);
        }
        if (email == null || email.isBlank()) return;
        sendEmail(EmailMessageDto.builder().recipientEmail(email).recipientName(name).subject(title)
                .templateCode("ORDER_EVENT").parameters(Map.of("body", body)).build());
    }
    private String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Chờ xử lý"; case "CONFIRMED" -> "Đã xác nhận"; case "PROCESSING" -> "Đang chuẩn bị";
            case "SHIPPED" -> "Đang giao"; case "DELIVERED" -> "Đã giao"; case "CANCELLED" -> "Đã hủy";
            case "RETURNED" -> "Đã trả hàng"; case "DELIVERY_FAILED" -> "Giao chưa thành công";
            case "RETURN_TO_SENDER" -> "Đang hoàn về cửa hàng"; case "COMPLETED" -> "Đã hoàn tất"; default -> status;
        };
    }
    private record OrderRecipient(Long userId, String email, String name, String updatedAt) {}

    @Override
    @Transactional
    public int cleanupOldReceipts(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = receiptRepository.deleteProcessedBefore(cutoff);
        log.info("Đã dọn dẹp {} notification receipts cũ hơn {} ngày", deleted, retentionDays);
        return deleted;
    }

    private String buildEmailHtml(EmailMessageDto dto) {
        if ("SPA_APPOINTMENT_REMINDER".equals(dto.getTemplateCode()) || "SPA_STAFF_FOLLOW_UP".equals(dto.getTemplateCode()) || "ORDER_EVENT".equals(dto.getTemplateCode())) {
            String recipient = org.springframework.web.util.HtmlUtils.htmlEscape(dto.getRecipientName() == null ? "Quý khách" : dto.getRecipientName());
            String subject = org.springframework.web.util.HtmlUtils.htmlEscape(dto.getSubject() == null ? "BeautyShop Spa" : dto.getSubject());
            String body = org.springframework.web.util.HtmlUtils.htmlEscape(String.valueOf(dto.getParameters().getOrDefault("body", "")))
                    .replace("\n", "<br/>");
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h2>" + subject
                    + "</h2><p>Xin chào " + recipient + ",</p><p>" + body + "</p></body></html>";
        }
        String name = org.springframework.web.util.HtmlUtils.htmlEscape(dto.getRecipientName() != null ? dto.getRecipientName() : "Quý khách");
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
