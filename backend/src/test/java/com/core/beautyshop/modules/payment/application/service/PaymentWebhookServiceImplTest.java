package com.core.beautyshop.modules.payment.application.service;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceImplTest {

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentWebhookServiceImpl paymentWebhookService;

    private SePayWebhookRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new SePayWebhookRequest();
        validRequest.setTransferType("in");
        validRequest.setContent("NGUYEN VAN A CHUYEN TIEN ORD-1234ABCD");
        validRequest.setTransferAmount(new BigDecimal("150000"));
        validRequest.setReferenceCode("REF123");
    }

    @Test
    void testProcessSePayWebhook_Success() {
        when(orderFacade.markOrderAsPaid(eq("ORD-1234ABCD"), eq(new BigDecimal("150000")), eq("REF123"))).thenReturn(true);

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderFacade).markOrderAsPaid("ORD-1234ABCD", new BigDecimal("150000"), "REF123");
    }

    @Test
    void testProcessSePayWebhook_TransferTypeOut_Ignored() {
        validRequest.setTransferType("out");

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderFacade, never()).markOrderAsPaid(any(), any(), any());
    }

    @Test
    void testProcessSePayWebhook_NoOrderNumberInContent_Ignored() {
        validRequest.setContent("CHUYEN TIEN MUA HANG"); // No ORD-XXXXXXXX

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderFacade, never()).markOrderAsPaid(any(), any(), any());
    }

    @Test
    void testProcessSePayWebhook_OrderNotFound_Ignored() {
        when(orderFacade.markOrderAsPaid(eq("ORD-1234ABCD"), any(), any())).thenReturn(false);

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderFacade).markOrderAsPaid("ORD-1234ABCD", new BigDecimal("150000"), "REF123");
    }

    @Test
    void testProcessSePayWebhook_InsufficientAmount_NotMarkedAsPaid() {
        validRequest.setTransferAmount(new BigDecimal("100000"));
        when(orderFacade.markOrderAsPaid(eq("ORD-1234ABCD"), eq(new BigDecimal("100000")), eq("REF123"))).thenReturn(false);

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderFacade).markOrderAsPaid("ORD-1234ABCD", new BigDecimal("100000"), "REF123");
    }
}

