package com.core.beautyshop.service;

import com.core.beautyshop.dto.request.SePayWebhookRequest;
import com.core.beautyshop.entities.order.Order;
import com.core.beautyshop.entities.order.enums.OrderStatus;
import com.core.beautyshop.entities.order.enums.PaymentStatus;
import com.core.beautyshop.repository.OrderRepository;
import com.core.beautyshop.service.payment.PaymentWebhookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentWebhookServiceImpl paymentWebhookService;

    private SePayWebhookRequest validRequest;
    private Order mockOrder;

    @BeforeEach
    void setUp() {
        validRequest = new SePayWebhookRequest();
        validRequest.setTransferType("in");
        validRequest.setContent("NGUYEN VAN A CHUYEN TIEN ORD-1234ABCD");
        validRequest.setTransferAmount(new BigDecimal("150000"));

        mockOrder = new Order();
        mockOrder.setId(1L);
        mockOrder.setOrderNumber("ORD-1234ABCD");
        mockOrder.setTotalAmount(new BigDecimal("150000"));
        mockOrder.setPaymentStatus(PaymentStatus.PENDING);
        mockOrder.setStatus(OrderStatus.PENDING);
        mockOrder.setStatusHistories(new ArrayList<>());
    }

    @Test
    void testProcessSePayWebhook_Success() {
        when(orderRepository.findByOrderNumber("ORD-1234ABCD")).thenReturn(Optional.of(mockOrder));

        paymentWebhookService.processSePayWebhook(validRequest);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();
        assertEquals(PaymentStatus.PAID, savedOrder.getPaymentStatus());
        assertEquals(OrderStatus.PROCESSING, savedOrder.getStatus());
        assertEquals(1, savedOrder.getStatusHistories().size());
    }

    @Test
    void testProcessSePayWebhook_TransferTypeOut_Ignored() {
        validRequest.setTransferType("out");

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderRepository, never()).findByOrderNumber(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testProcessSePayWebhook_NoOrderNumberInContent_Ignored() {
        validRequest.setContent("CHUYEN TIEN MUA HANG"); // No ORD-XXXXXXXX

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderRepository, never()).findByOrderNumber(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testProcessSePayWebhook_OrderNotFound_Ignored() {
        when(orderRepository.findByOrderNumber("ORD-1234ABCD")).thenReturn(Optional.empty());

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderRepository).findByOrderNumber("ORD-1234ABCD");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testProcessSePayWebhook_InsufficientAmount_NotMarkedAsPaid() {
        when(orderRepository.findByOrderNumber("ORD-1234ABCD")).thenReturn(Optional.of(mockOrder));
        validRequest.setTransferAmount(new BigDecimal("100000")); // Less than 150000

        paymentWebhookService.processSePayWebhook(validRequest);

        verify(orderRepository).findByOrderNumber("ORD-1234ABCD");
        verify(orderRepository, never()).save(any()); // Assuming we don't save if insufficient in this basic impl
        assertEquals(PaymentStatus.PENDING, mockOrder.getPaymentStatus());
    }
}
