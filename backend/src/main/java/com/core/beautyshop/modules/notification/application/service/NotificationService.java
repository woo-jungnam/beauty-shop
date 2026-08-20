package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;

import java.math.BigDecimal;

public interface NotificationService {

    void sendEmail(EmailMessageDto emailMessageDto);

    void sendOrderConfirmationNotification(Long orderId, String orderNumber, Long userId, BigDecimal totalAmount);

    void sendOrderStatusUpdateNotification(Long orderId, String orderNumber, String previousStatus, String newStatus);

    void sendOrderCancelledNotification(Long orderId, String orderNumber);
}
