package com.core.beautyshop.modules.order.application.service;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OrderExpirationService {
    private final OrderRepository orders;
    private final OrderService orderService;
    @Transactional
    public boolean expire(Long id) {
        Order order = orders.findByIdForUpdate(id).orElse(null);
        if (order == null || (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) || order.getPaymentDeadline() == null
                || order.getPaymentDeadline().isAfter(java.time.Instant.now()) || order.getPaymentStatus() == PaymentStatus.PAID) return false;
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);
        request.setNotes("Bank payment deadline expired");
        orderService.updateOrderStatus(id, request);
        return true;
    }
}
