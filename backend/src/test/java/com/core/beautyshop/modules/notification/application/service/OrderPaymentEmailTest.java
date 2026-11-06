package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;
import com.core.beautyshop.modules.notification.domain.NotificationReceiptRepository;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.OrderFacade.*;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderPaymentEmailTest {
    private static final Instant PAID_AT = Instant.parse("2026-11-06T03:15:00Z");
    private static BigDecimal amount(String value) { return new BigDecimal(value); }

    private static OrderEmailDetailDto detail(boolean spa) {
        return new OrderEmailDetailDto("ORD-EMAIL-21", "Nguyễn An", "0901234567",
                spa ? null : "12 Nguyễn Huệ, Bến Nghé, Quận 1, TP. Hồ Chí Minh", "BANK",
                spa ? List.of() : List.of(new OrderEmailItemDto("Serum phục hồi", "Chai 30ml", 2,
                        amount("100000"), amount("10000"), "SERUM-30", "/uploads/serum.jpg")),
                amount("180000"), BigDecimal.ZERO, amount("10000"), amount("170000"), PAID_AT.minusSeconds(3600),
                "PROCESSING", "PAID", amount("170000"), BigDecimal.ZERO, PAID_AT,
                spa ? 9L : null, spa ? "TREATMENT_COURSE" : null, spa ? 12L : null,
                spa ? List.of(new OrderEmailServiceDto("Chăm sóc da chuyên sâu", 2, amount("90000"))) : List.of(),
                null, null, "Gọi trước khi giao");
    }

    @Test
    void paidEventSendsActualMimeWithCompleteOrderAndCorrectDiscountedPrices() throws Exception {
        var sender = mock(JavaMailSender.class);
        var facade = mock(OrderFacade.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        when(facade.findOrderRecipient(21L)).thenReturn(Optional.of(new OrderRecipientDto(null, "customer@example.test", "Nguyễn An", "now")));
        when(facade.findOrderEmailDetail(21L)).thenReturn(Optional.of(detail(false)));
        var service = new NotificationServiceImpl(mock(IdentityFacade.class), mock(NotificationReceiptRepository.class), Optional.of(sender), facade, null);
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "mailFrom", "shop@example.test");
        ReflectionTestUtils.setField(service, "supportEmail", "support@example.test");
        ReflectionTestUtils.setField(service, "supportPhone", "02812345678");
        ReflectionTestUtils.setField(service, "mediaBaseUrl", "https://shop.example.test");
        service.sendOrderPaidNotification(21L, "ORD-EMAIL-21", null, amount("170000"));
        var outer = (Multipart) message.getContent();
        var related = (Multipart) outer.getBodyPart(0).getContent();
        String html = (String) related.getBodyPart(0).getContent();
        assertThat(message.getSubject()).isEqualTo("Đã nhận thanh toán #ORD-EMAIL-21");
        assertThat(html).contains("ORD-EMAIL-21", "Nguyễn An", "0901234567", "TP. Hồ Chí Minh", "06/11/2026 10:15",
                "Đã thanh toán", "170.000 ₫", "Còn phải thanh toán", "90.000 ₫", "180.000 ₫", "100.000 ₫",
                "Giảm 10.000 ₫/SP", "SERUM-30", "Chai 30ml", "Miễn phí", "support@example.test", "02812345678",
                "https://shop.example.test/uploads/serum.jpg", "Serum phục hồi", "bgcolor=\"#8c2a47\"", "@media");
        assertThat(html).doesNotContain("<script>", "1900 xxxx");
        verify(sender).send(message);
        Files.createDirectories(Path.of("target", "email-previews"));
        Files.writeString(Path.of("target", "email-previews", "payment-products.html"), html);
    }

    @Test
    void spaEmailShowsServiceQuantityAndTotalsWithoutShippingSentinel() throws Exception {
        var message = email(detail(true));
        message.setParameters(Map.of("body", "Đã nhận thanh toán", "spaPackage",
                new com.core.beautyshop.modules.spa.api.SpaOrderEmailFacade.PackageDetail("Gói phục hồi da", 90,
                        List.of(new com.core.beautyshop.modules.spa.api.SpaOrderEmailFacade.Entitlement("Chăm sóc chuyên sâu", 6)))));
        String html = OrderEmailRenderer.render(message, detail(true), "", "", "");
        assertThat(html).contains("Dịch vụ Spa / Liệu trình", "#9", "#12", "Liệu trình chăm sóc da", "Chăm sóc da chuyên sâu × 2", "180.000 ₫");
        assertThat(html).doesNotContain("SPA_SERVICE", "Địa chỉ giao hàng", "1900 xxxx", "support@beautyshop.vn");
        assertThat(html).contains("Gói phục hồi da", "90 ngày", "Chăm sóc chuyên sâu", "6 buổi");
        Files.createDirectories(Path.of("target", "email-previews"));
        Files.writeString(Path.of("target", "email-previews", "payment-spa.html"), html);
    }

    @Test
    void legacyNullDiscountAndUnsafeImageDoNotBreakEmailOrInjectMarkup() {
        var item = new OrderEmailItemDto("Kem dưỡng <script>alert(1)</script>", null, 1, amount("50000"), null, null, "javascript:alert(1)");
        var order = new OrderEmailDetailDto("LEGACY", null, null, null, "COD", List.of(item), null, null, null,
                amount("50000"), null, null, null, null, null, null, null, null, null, List.of(), null, null, null);
        String html = OrderEmailRenderer.render(email(order), order, "", "", "");
        assertThat(html).contains("Kem dưỡng", "50.000 ₫", "&lt;script&gt;").doesNotContain("javascript:", "<script>", ">null<", "1900 xxxx");
        assertThat(OrderEmailRenderer.render(email(order), null, "", "", "")).contains("Chi tiết đơn hàng hiện chưa có");
    }

    private static EmailMessageDto email(OrderEmailDetailDto detail) {
        return EmailMessageDto.builder().recipientName("Nguyễn An").subject("Đã nhận thanh toán")
                .templateCode("ORDER_EVENT").parameters(Map.of("body", "Đã ghi nhận thanh toán.", "orderDetail", detail)).build();
    }
}
