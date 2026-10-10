package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;
import com.core.beautyshop.modules.notification.domain.NotificationReceiptRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:notification_fix;DB_CLOSE_DELAY=-1;MODE=MySQL", "app.mail.enabled=true"})
@ActiveProfiles("test")
class NotificationDeliveryIntegrationTest {
    @Autowired NotificationInbox inbox;
    @Autowired NotificationService notifications;
    @Autowired NotificationReceiptRepository receipts;
    @MockBean JavaMailSender sender;

    @Test
    void failedSmtpRollsBackReceiptAndSuccessfulRetryIsDeduplicated() {
        String event = UUID.randomUUID().toString();
        var email = EmailMessageDto.builder().recipientEmail("customer@example.com").recipientName("Customer")
                .subject("Order confirmation").templateCode("ORDER_CONFIRMATION").build();
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        doThrow(new MailSendException("SMTP unavailable")).doNothing().when(sender).send(any(MimeMessage.class));
        assertThrows(IllegalStateException.class, () -> inbox.process(event, () -> notifications.sendEmail(email)));
        assertFalse(receipts.existsById(event));
        inbox.process(event, () -> notifications.sendEmail(email));
        assertTrue(receipts.existsById(event));
        inbox.process(event, () -> notifications.sendEmail(email));
        verify(sender, times(2)).send(any(MimeMessage.class));
    }
}
