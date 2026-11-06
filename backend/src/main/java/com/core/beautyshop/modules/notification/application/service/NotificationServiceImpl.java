package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.notification.api.CustomerNotificationFacade;
import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.notification.domain.NotificationReceiptRepository;
import com.core.beautyshop.modules.order.api.OrderFacade.OrderEmailDetailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final IdentityFacade identityFacade;
    private final NotificationReceiptRepository receiptRepository;
    private final Optional<JavaMailSender> mailSender;
    private final OrderFacade orderFacade;
    private final CustomerNotificationFacade customerNotifications;

    @Autowired(required = false)
    private com.core.beautyshop.modules.spa.api.SpaOrderEmailFacade spaOrderEmails;

    public NotificationServiceImpl(IdentityFacade identityFacade,
                                   NotificationReceiptRepository receiptRepository,
                                   Optional<JavaMailSender> mailSender) {
        this(identityFacade, receiptRepository, mailSender, null, null);
    }

    @Autowired
    public NotificationServiceImpl(IdentityFacade identityFacade,
                                   NotificationReceiptRepository receiptRepository,
                                   Optional<JavaMailSender> mailSender,
                                   OrderFacade orderFacade,
                                   CustomerNotificationFacade customerNotifications) {
        this.identityFacade = identityFacade;
        this.receiptRepository = receiptRepository;
        this.mailSender = mailSender;
        this.orderFacade = orderFacade;
        this.customerNotifications = customerNotifications;
    }

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@beautyshop.vn}")
    private String mailFrom;

    @Value("${app.mail.support-email:}")
    private String supportEmail;

    @Value("${app.mail.support-phone:}")
    private String supportPhone;

    @Value("${app.mail.media-base-url:}")
    private String mediaBaseUrl;

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
        if (orderFacade == null) return;
        var recipientOpt = orderFacade.findOrderRecipient(orderId);
        if (recipientOpt.isEmpty()) return;
        var recipient = recipientOpt.get();
        String email = recipient.email();
        String name = recipient.name();
        if (recipient.userId() != null) {
            var account = identityFacade.findUserSummaryById(recipient.userId()).orElse(null);
            if (account != null && (email == null || email.isBlank())) email = account.getEmail();
            String eventKey = "order:" + orderId + ":" + event + (event.startsWith("STATUS_") ? ":" + recipient.updatedAt() : "");
            if (customerNotifications != null) {
                customerNotifications.publish(recipient.userId(), eventKey, "ORDER", title, body, "profile?tab=orders&order=" + orderId);
            }
        }
        if (email == null || email.isBlank()) return;

        Map<String, Object> params = new HashMap<>();
        params.put("body", body);
        var detailOpt = orderFacade.findOrderEmailDetail(orderId);
        detailOpt.ifPresent(d -> params.put("orderDetail", d));
        if (detailOpt.isPresent() && detailOpt.get().servicePackageId() != null && spaOrderEmails != null) {
            spaOrderEmails.findPurchasedPackage(orderId).ifPresent(detail -> params.put("spaPackage", detail));
        }

        sendEmail(EmailMessageDto.builder().recipientEmail(email).recipientName(name).subject(title)
                .templateCode("ORDER_EVENT").parameters(params).build());
    }
    private String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Chờ xử lý"; case "CONFIRMED" -> "Đã xác nhận"; case "PROCESSING" -> "Đang chuẩn bị";
            case "SHIPPED" -> "Đang giao"; case "DELIVERED" -> "Đã giao"; case "CANCELLED" -> "Đã hủy";
            case "RETURNED" -> "Đã trả hàng"; case "DELIVERY_FAILED" -> "Giao chưa thành công";
            case "RETURN_TO_SENDER" -> "Đang hoàn về cửa hàng"; case "COMPLETED" -> "Đã hoàn tất"; default -> status;
        };
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
        String recipient = HtmlUtils.htmlEscape(dto.getRecipientName() == null ? "Quý khách" : dto.getRecipientName());
        String subject = HtmlUtils.htmlEscape(dto.getSubject() == null ? "BeautyShop" : dto.getSubject());
        String body = HtmlUtils.htmlEscape(String.valueOf((dto.getParameters() == null ? "" : dto.getParameters().getOrDefault("body", ""))))
                .replace("\n", "<br/>");

        // SPA templates — keep simple
        if ("SPA_APPOINTMENT_REMINDER".equals(dto.getTemplateCode()) || "SPA_STAFF_FOLLOW_UP".equals(dto.getTemplateCode())) {
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h2>" + subject
                    + "</h2><p>Xin chào " + recipient + ",</p><p>" + body + "</p></body></html>";
        }

        Object detailObj = dto.getParameters() == null ? null : dto.getParameters().get("orderDetail");
        OrderEmailDetailDto detail = detailObj instanceof OrderEmailDetailDto d ? d : null;
        return OrderEmailRenderer.render(dto, detail, supportEmail, supportPhone, mediaBaseUrl);
    }
}
