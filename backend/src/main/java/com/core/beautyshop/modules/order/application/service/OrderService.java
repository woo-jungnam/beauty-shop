package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.order.application.dto.request.CheckoutRequest;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse checkout(CheckoutRequest request);

    OrderResponse checkout(Long userId, CheckoutRequest request);

    OrderResponse getOrderById(Long id);

    Page<OrderResponse> getMyOrders(Pageable pageable);

    Page<OrderResponse> getOrdersByUser(Long userId, Pageable pageable);

    Page<OrderResponse> getAllOrders(Pageable pageable);

    OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request);

    OrderResponse cancelOrder(Long id);

    OrderResponse cancelOrder(Long id, Long userId);
}

