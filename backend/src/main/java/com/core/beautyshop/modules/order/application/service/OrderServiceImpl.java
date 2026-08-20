package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.cart.application.dto.response.CartItemResponse;
import com.core.beautyshop.modules.cart.application.dto.response.CartResponse;
import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.order.application.dto.request.CheckoutRequest;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.application.factory.OrderFactory;
import com.core.beautyshop.modules.order.application.mapper.OrderMapper;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderItem;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.OrderStatusHistory;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.event.OrderEvents;
import com.core.beautyshop.modules.payment.application.dto.response.PaymentInstruction;
import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategy;
import com.core.beautyshop.modules.payment.application.strategy.PaymentStrategyFactory;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CartFacade cartFacade;
    private final OrderRepository orderRepository;
    private final InventoryFacade inventoryFacade;
    private final CatalogFacade catalogFacade;
    private final IdentityFacade identityFacade;
    private final OrderFactory orderFactory;
    private final OrderMapper orderMapper;
    private final PaymentStrategyFactory paymentStrategyFactory;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        return checkout(userId, request);
    }

    @Override
    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request) {
        CartResponse cart = validateCart(userId, request.getSessionId());

        if (userId != null && !identityFacade.existsById(userId)) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId);
        }

        Order order = orderFactory.createOrder(request, userId);

        BigDecimal subTotal = buildOrderItems(order, cart);

        order.setSubTotal(subTotal);
        order.setTotalAmount(subTotal.add(order.getShippingFee()).subtract(order.getDiscountAmount()));

        createStatusHistory(order, OrderStatus.PENDING, "Order created via checkout");

        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(OrderEvents.OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .userId(userId)
                .sessionId(request.getSessionId())
                .totalAmount(savedOrder.getTotalAmount())
                .build());

        return buildOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + id));

        if (order.getUserId() != null) {
            Long currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
            if (!SecurityUtils.isAdmin() && (currentUserId == null || !order.getUserId().equals(currentUserId))) {
                throw new AccessDeniedException("Bạn không có quyền xem thông tin đơn hàng này!");
            }
        }

        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Pageable pageable) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return getOrdersByUser(currentUserId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUser(Long userId, Pageable pageable) {
        Long currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (!SecurityUtils.isAdmin() && (currentUserId == null || !userId.equals(currentUserId))) {
            throw new AccessDeniedException("Bạn không có quyền xem danh sách đơn hàng của người dùng khác!");
        }

        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(orderMapper::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(orderMapper::toOrderResponse);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + id));

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(request.getStatus());
        createStatusHistory(order, request.getStatus(), request.getNotes());

        if (request.getStatus() == OrderStatus.CANCELLED && previousStatus != OrderStatus.CANCELLED) {
            publishOrderCancelledEvent(order);
        }

        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(OrderEvents.OrderStatusChangedEvent.builder()
                .orderId(saved.getId())
                .orderNumber(saved.getOrderNumber())
                .previousStatus(previousStatus)
                .newStatus(saved.getStatus())
                .build());

        return orderMapper.toOrderResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return cancelOrder(id, currentUserId);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, Long userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + id));

        if (order.getUserId() != null && !order.getUserId().equals(userId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền hủy đơn hàng này!");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Chỉ những đơn hàng ở trạng thái CHỜ XỬ LÝ mới có thể bị hủy");
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        createStatusHistory(order, OrderStatus.CANCELLED, "Order cancelled by customer");

        publishOrderCancelledEvent(order);

        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(OrderEvents.OrderStatusChangedEvent.builder()
                .orderId(saved.getId())
                .orderNumber(saved.getOrderNumber())
                .previousStatus(previousStatus)
                .newStatus(saved.getStatus())
                .build());

        return orderMapper.toOrderResponse(saved);
    }

    private CartResponse validateCart(Long userId, String sessionId) {
        CartResponse cart = cartFacade.getCart(userId, sessionId);
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException("Giỏ hàng đang trống");
        }
        return cart;
    }

    private BigDecimal buildOrderItems(Order order, CartResponse cart) {
        BigDecimal subTotal = BigDecimal.ZERO;

        for (CartItemResponse item : cart.getItems()) {
            inventoryFacade.reserveStock(item.getVariantId(), item.getQuantity());

            ProductVariantSummaryDto variant = catalogFacade.getVariantSummaryById(item.getVariantId());

            BigDecimal price = variant.getDiscountPrice() != null
                    ? variant.getDiscountPrice()
                    : variant.getPrice();

            subTotal = subTotal.add(price.multiply(new BigDecimal(item.getQuantity())));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productVariantId(variant.getId())
                    .sku(variant.getSku())
                    .productName(variant.getProductName())
                    .variantName(variant.getVariantName())
                    .quantity(item.getQuantity())
                    .price(variant.getPrice())
                    .discount(variant.getDiscountPrice() != null
                            ? variant.getPrice().subtract(variant.getDiscountPrice())
                            : BigDecimal.ZERO)
                    .build();
            order.getItems().add(orderItem);
        }

        return subTotal;
    }

    private void createStatusHistory(Order order, OrderStatus status, String notes) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .status(status)
                .notes(notes)
                .build();
        order.getStatusHistories().add(history);
    }

    private void publishOrderCancelledEvent(Order order) {
        if (order.getItems() != null) {
            List<OrderEvents.OrderItemSummary> itemSummaries = order.getItems().stream()
                    .map(item -> OrderEvents.OrderItemSummary.builder()
                            .variantId(item.getProductVariantId())
                            .quantity(item.getQuantity())
                            .build())
                    .collect(Collectors.toList());

            eventPublisher.publishEvent(OrderEvents.OrderCancelledEvent.builder()
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .items(itemSummaries)
                    .build());
        }
    }

    private OrderResponse buildOrderResponse(Order savedOrder) {
        OrderResponse response = orderMapper.toOrderResponse(savedOrder);

        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(savedOrder.getPaymentMethod());
        com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto paymentOrderDto = com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto.builder()
                .orderNumber(savedOrder.getOrderNumber())
                .totalAmount(savedOrder.getTotalAmount())
                .customerName(savedOrder.getCustomerName())
                .build();
        PaymentInstruction instruction = strategy.processPayment(paymentOrderDto);
        response.setPaymentInstruction(instruction);

        return response;
    }
}
