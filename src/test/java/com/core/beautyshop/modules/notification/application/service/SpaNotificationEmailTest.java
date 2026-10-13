package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;
import com.core.beautyshop.modules.notification.domain.NotificationReceiptRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SpaNotificationEmailTest {
    @Test
    void staffInstructionsAndCustomerNameAreRenderedAsTextInsteadOfExecutableHtml() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        NotificationServiceImpl service = new NotificationServiceImpl(mock(IdentityFacade.class),
                mock(NotificationReceiptRepository.class), Optional.of(sender));
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "mailFrom", "spa@example.test");
        service.sendEmail(EmailMessageDto.builder().recipientEmail("customer@example.test").recipientName("<img src=x>")
                .subject("Staff instructions").templateCode("SPA_STAFF_FOLLOW_UP")
                .parameters(Map.of("body", "<script>alert('x')</script>\nStaff wrote this line.")).build());
        Multipart outer = (Multipart) message.getContent();
        Multipart related = (Multipart) outer.getBodyPart(0).getContent();
        String html = (String) related.getBodyPart(0).getContent();
        assertThat(html).contains("&lt;script&gt;", "&lt;img", "<br/>", "Staff wrote this line.");
        assertThat(html).doesNotContain("<script>", "<img src=x>");
        verify(sender).send(message);
    }
}
