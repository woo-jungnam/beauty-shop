package com.core.beautyshop.modules.notification.application.service;

import com.core.beautyshop.modules.notification.application.dto.EmailMessageDto;

public interface NotificationService {

    /**
     * Gửi email thông báo
     */
    void sendEmail(EmailMessageDto emailMessageDto);

    /**
     * Gửi email xác nhận đơn hàng khi đơn hàng được tạo thành công
     */
    void sendOrderConfirmationNotification(Long orderId, String orderNumber, Long userId, java.math.BigDecimal totalAmount);

    /**
     * Gửi email thông báo cập nhật trạng thái đơn hàng
     */
    void sendOrderStatusUpdateNotification(Long orderId, String orderNumber, String previousStatus, String newStatus);

    /**
     * Gửi email thông báo hủy đơn hàng
     */
    void sendOrderCancelledNotification(Long orderId, String orderNumber);
}
