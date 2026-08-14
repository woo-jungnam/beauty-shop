package com.core.beautyshop.service.order;

import com.core.beautyshop.dto.request.CheckoutRequest;
import com.core.beautyshop.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.dto.response.CartResponse;
import com.core.beautyshop.dto.response.CartItemResponse;
import com.core.beautyshop.dto.response.OrderResponse;
import com.core.beautyshop.dto.response.PaymentInstruction;
import com.core.beautyshop.entities.order.Order;
import com.core.beautyshop.entities.order.OrderItem;
import com.core.beautyshop.entities.order.OrderStatusHistory;
import com.core.beautyshop.entities.order.enums.OrderStatus;
import com.core.beautyshop.entities.product.ProductVariant;
import com.core.beautyshop.entities.user.User;
import com.core.beautyshop.exception.BusinessException;
import com.core.beautyshop.exception.ResourceNotFoundException;
import com.core.beautyshop.factory.OrderFactory;
import com.core.beautyshop.factory.PaymentStrategyFactory;
import com.core.beautyshop.mapper.OrderMapper;
import com.core.beautyshop.repository.OrderRepository;
import com.core.beautyshop.repository.ProductVariantRepository;
import com.core.beautyshop.repository.UserRepository;
import com.core.beautyshop.service.cart.CartService;
import com.core.beautyshop.service.inventory.InventoryService;
import com.core.beautyshop.service.payment.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final OrderFactory orderFactory;
    private final OrderMapper orderMapper;
    private final PaymentStrategyFactory paymentStrategyFactory;

    @Override
    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request) {
        CartResponse cart = validateCart(userId, request.getSessionId());

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Order order = orderFactory.createOrder(request, user);

        BigDecimal subTotal = buildOrderItems(order, cart);

        order.setSubTotal(subTotal);
        order.setTotalAmount(subTotal.add(order.getShippingFee()).subtract(order.getDiscountAmount()));

        createStatusHistory(order, OrderStatus.PENDING, "Order created via checkout");

        Order savedOrder = orderRepository.save(order);
        cartService.clearCart(cart.getId());

        return buildOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + id));
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUser(Long userId, Pageable pageable) {
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

        order.setStatus(request.getStatus());
        createStatusHistory(order, request.getStatus(), request.getNotes());

        // If order is cancelled by admin, release reserved stock
        if (request.getStatus() == OrderStatus.CANCELLED) {
            releaseOrderStock(order);
        }

        Order saved = orderRepository.save(order);
        return orderMapper.toOrderResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, Long userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + id));

        // Verify user owns the order
        if (order.getUser() != null && !order.getUser().getId().equals(userId)) {
            throw new BusinessException("Bạn không có quyền hủy đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Chỉ những đơn hàng ở trạng thái CHỜ XỬ LÝ mới có thể bị hủy");
        }

        order.setStatus(OrderStatus.CANCELLED);
        createStatusHistory(order, OrderStatus.CANCELLED, "Order cancelled by customer");

        releaseOrderStock(order);

        Order saved = orderRepository.save(order);
        return orderMapper.toOrderResponse(saved);
    }

    // ---- Private helpers ----

    private CartResponse validateCart(Long userId, String sessionId) {
        CartResponse cart = cartService.getCart(userId, sessionId);
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException("Giỏ hàng đang trống");
        }
        return cart;
    }

    private BigDecimal buildOrderItems(Order order, CartResponse cart) {
        BigDecimal subTotal = BigDecimal.ZERO;

        for (CartItemResponse item : cart.getItems()) {
            inventoryService.reserveStock(item.getVariantId(), item.getQuantity());

            ProductVariant variant = productVariantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new BusinessException("Product variant not found"));

            BigDecimal price = variant.getDiscountPrice() != null
                    ? variant.getDiscountPrice()
                    : variant.getPrice();

            subTotal = subTotal.add(price.multiply(new BigDecimal(item.getQuantity())));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productVariant(variant)
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

    private void releaseOrderStock(Order order) {
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                inventoryService.releaseStock(item.getProductVariant().getId(), item.getQuantity());
            }
        }
    }

    private OrderResponse buildOrderResponse(Order savedOrder) {
        OrderResponse response = orderMapper.toOrderResponse(savedOrder);

        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(savedOrder.getPaymentMethod());
        PaymentInstruction instruction = strategy.processPayment(savedOrder);
        response.setPaymentInstruction(instruction);

        return response;
    }
}
