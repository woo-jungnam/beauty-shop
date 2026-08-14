package com.core.beautyshop.service.order;

import com.core.beautyshop.dto.request.CheckoutRequest;
import com.core.beautyshop.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse checkout(Long userId, CheckoutRequest request);

    OrderResponse getOrderById(Long id);

    Page<OrderResponse> getOrdersByUser(Long userId, Pageable pageable);

    Page<OrderResponse> getAllOrders(Pageable pageable);

    OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request);

    OrderResponse cancelOrder(Long id, Long userId);
}
