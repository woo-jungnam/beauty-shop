package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.cart.api.dto.CartItemResponse;
import com.core.beautyshop.modules.cart.api.dto.CartResponse;
import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
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
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.modules.promotion.api.PromotionFacade;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final PaymentFacade paymentFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final PromotionFacade promotionFacade;
    private final com.core.beautyshop.shared.config.SystemConfigService systemConfigService;

    @org.springframework.beans.factory.annotation.Value("${app.order.payment-timeout-minutes:30}")
    private long paymentTimeoutMinutes = 30;

    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        return checkout(userId, request);
    }

    @Override
    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request) {
        if (request.getPaymentMethod() == com.core.beautyshop.shared.domain.enums.PaymentMethod.CASH)
            throw new BusinessException("CASH is only supported by Spa visit checkout");
        String key = checkoutKey(userId, request);
        String hash = requestHash(request);
        // The cart row survives clearing. Lock it before reading items or replaying a request.
        CartResponse cart = cartFacade.lockCart(userId, request.getSessionId());
        if (key != null) {
            var previous = orderRepository.findByCheckoutKey(key);
            if (previous.isPresent()) {
                if (!hash.equals(previous.get().getCheckoutHash())) {
                    throw new BusinessException("Idempotency-Key was already used with a different request");
                }
                return buildOrderResponse(previous.get());
            }
        }
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException("Cart is empty");
        }
        List<Long> unavailableVariantIds = cart.getItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.getAvailable()))
                .map(CartItemResponse::getVariantId)
                .toList();
        if (!unavailableVariantIds.isEmpty()) {
            throw new BusinessException("Cart contains unavailable products; remove variants: " + unavailableVariantIds);
        }

        if (userId != null && !identityFacade.existsById(userId)) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId);
        }

        Order order = orderFactory.createOrder(request, userId);
        order.setCheckoutKey(key);
        order.setCheckoutHash(hash);
        if (order.getPaymentMethod() == com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK) {
            order.setPaymentDeadline(java.time.Instant.now().plusSeconds(paymentTimeoutMinutes * 60));
        }

        BigDecimal subTotal = buildOrderItems(order, cart);

        BigDecimal membershipDiscount = BigDecimal.ZERO;
        if (userId != null) {
            UserSummaryDto userSummary = identityFacade.getUserSummaryById(userId);
            Integer discountPercentage = userSummary.getMembershipDiscountPercentage();
            if (discountPercentage != null && discountPercentage > 0) {
                BigDecimal discountPercent = BigDecimal.valueOf(discountPercentage)
                        .divide(BigDecimal.valueOf(100));
                membershipDiscount = subTotal.multiply(discountPercent);
            }
        }
        
        BigDecimal shippingFee = systemConfigService != null
                ? systemConfigService.calculateShippingFee(subTotal)
                : BigDecimal.ZERO;
        order.setShippingFee(shippingFee);

        BigDecimal grossAmount = subTotal.add(order.getShippingFee()).max(BigDecimal.ZERO);
        PromotionFacade.AppliedVoucher appliedVoucher = promotionFacade.validate(request.getVoucherCode(), userId, subTotal);
        if (appliedVoucher != null) {
            order.setVoucherId(appliedVoucher.id());
            order.setDiscountAmount(appliedVoucher.discountAmount());
        }
        BigDecimal totalDiscount = order.getDiscountAmount() != null
                ? order.getDiscountAmount().add(membershipDiscount)
                : membershipDiscount;
        BigDecimal applicableDiscount = totalDiscount.max(BigDecimal.ZERO).min(grossAmount);

        order.setDiscountAmount(applicableDiscount);
        order.setSubTotal(subTotal);
        // VND obligations are rounded once before persistence and payment instructions.
        order.setTotalAmount(grossAmount.subtract(applicableDiscount).setScale(0, java.math.RoundingMode.HALF_UP));

        createStatusHistory(order, OrderStatus.PENDING, "Đơn hàng được tạo khi thanh toán");
        boolean freeBankOrder = order.getPaymentMethod() == com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK
                && order.getTotalAmount().signum() == 0;
        if (freeBankOrder) {
            order.setPaymentStatus(com.core.beautyshop.modules.order.domain.enums.PaymentStatus.PAID);
            order.setPaidAt(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            order.setPaymentDeadline(null);
            order.setStatus(OrderStatus.PROCESSING);
            createStatusHistory(order, OrderStatus.PROCESSING, "Zero amount payment obligation settled");
        }

        Order savedOrder = orderRepository.save(order);

        promotionFacade.redeem(appliedVoucher, userId, savedOrder.getId());

        eventPublisher.publishEvent(OrderEvents.OrderCreatedEvent.builder()
                .orderId(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .userId(userId)
                .sessionId(request.getSessionId())
                .totalAmount(savedOrder.getTotalAmount())
                .build());

        if (freeBankOrder) {
            List<OrderEvents.OrderItemSummary> itemSummaries = savedOrder.getItems() != null
                    ? savedOrder.getItems().stream()
                    .map(item -> OrderEvents.OrderItemSummary.builder()
                            .variantId(item.getProductVariantId())
                            .quantity(item.getQuantity())
                            .build())
                    .toList()
                    : java.util.List.of();

            eventPublisher.publishEvent(OrderEvents.OrderStatusChangedEvent.builder().orderId(savedOrder.getId())
                    .orderNumber(savedOrder.getOrderNumber()).previousStatus(OrderStatus.PENDING).newStatus(OrderStatus.PROCESSING).build());
            eventPublisher.publishEvent(OrderEvents.OrderPaidEvent.builder().orderId(savedOrder.getId())
                    .orderNumber(savedOrder.getOrderNumber())
                    .totalAmount(savedOrder.getTotalAmount())
                    .userId(savedOrder.getUserId()).servicePackageId(savedOrder.getServicePackageId())
                    .items(itemSummaries)
                    .build());
        }

        return buildOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return getOrderById(id, null);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, String guestSessionId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + id));

        if (order.getUserId() == null && !SecurityUtils.isAdmin()
                && !matchesGuestSession(order.getGuestSessionId(), guestSessionId)) {
            throw new AccessDeniedException("Mã phiên khách vãng lai không hợp lệ");
        }

        if (order.getUserId() != null) {
            Long currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
            if (!SecurityUtils.isAdmin() && (currentUserId == null || !order.getUserId().equals(currentUserId))) {
                throw new AccessDeniedException("Bạn không có quyền xem thông tin đơn hàng này!");
            }
        }

        return buildOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'ORDER_STAFF')")
    public OrderResponse getAdminOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return buildOrderResponse(order);
    }

    private boolean matchesGuestSession(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Pageable pageable) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return orderRepository.findVisibleCustomerHistoryByUserId(currentUserId, pageable)
                .map(orderMapper::toOrderResponse);
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
    @Transactional(readOnly = true)
    public Page<OrderResponse> searchAdminOrders(com.core.beautyshop.modules.order.application.dto.request.AdminOrderFilter filter, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Order> specification = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (filter != null) {
                if (filter.keyword() != null && !filter.keyword().isBlank()) {
                    String pattern = "%" + filter.keyword().trim().toLowerCase(java.util.Locale.ROOT) + "%";
                    predicates.add(cb.or(cb.like(cb.lower(root.get("orderNumber")), pattern),
                            cb.like(cb.lower(root.get("customerName")), pattern),
                            cb.like(cb.lower(root.get("customerPhone")), pattern)));
                }
                if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
                if (filter.paymentStatus() != null) predicates.add(cb.equal(root.get("paymentStatus"), filter.paymentStatus()));
                if (filter.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
                if (filter.to() != null) predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return orderRepository.findAll(specification, pageable).map(orderMapper::toOrderResponse);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + id));

        OrderStatus previousStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        if (order.getAppointmentId() != null)
            throw new BusinessException("A completed Spa visit invoice has no shipping or order-status workflow");
        if (order.getServicePackageId() != null && (newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.RETURNED))
            throw new BusinessException("Spa package orders cannot be shipped or returned as physical goods");

        validateStatusTransition(previousStatus, newStatus);
        if (previousStatus == newStatus) return orderMapper.toOrderResponse(order);

        if (newStatus == OrderStatus.SHIPPED) {
            boolean hasCarrier = request.getCarrierName() != null && !request.getCarrierName().isBlank();
            boolean hasTracking = request.getTrackingCode() != null && !request.getTrackingCode().isBlank();
            if (hasCarrier != hasTracking) {
                throw new BusinessException("Carrier and tracking code are required when shipping");
            }
            if (hasCarrier) {
                order.setCarrierName(request.getCarrierName().trim());
                order.setTrackingCode(request.getTrackingCode().trim());
            }
        }
        if (newStatus == OrderStatus.CANCELLED) {
            if (request.getNotes() == null || request.getNotes().isBlank()) throw new BusinessException("Cancellation reason is required");
            order.setCancelReason(request.getNotes().trim());
            order.setCancelledBy(SecurityUtils.getCurrentUserIdOptional().orElse(null));
        }

        if ((newStatus == OrderStatus.PROCESSING || newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.DELIVERED)
                && order.getPaymentMethod() == com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK
                && order.getPaymentStatus() != com.core.beautyshop.modules.order.domain.enums.PaymentStatus.PAID) {
            throw new BusinessException("Bank payment must be settled before shipping");
        }
        if (newStatus == OrderStatus.DELIVERED
                && order.getPaymentMethod() == com.core.beautyshop.shared.domain.enums.PaymentMethod.COD) {
            BigDecimal remaining = order.getTotalAmount().subtract(order.getPaidAmount()).max(BigDecimal.ZERO);
            paymentFacade.recordCodCollection(order.getOrderNumber(), remaining);
            // Delivery confirms cash collection only for the outstanding balance; preserve prior transfers/excess.
            order.setPaidAmount(order.getPaidAmount().max(order.getTotalAmount()));
            order.setPaymentStatus(com.core.beautyshop.modules.order.domain.enums.PaymentStatus.PAID);
            if (order.getPaidAt() == null) order.setPaidAt(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        }
        if ((newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.RETURNED)
                && order.getPaidAmount().compareTo(order.getRefundedAmount()) > 0) {
            order.setPaymentStatus(com.core.beautyshop.modules.order.domain.enums.PaymentStatus.REFUND_PENDING);
        }
        order.setStatus(newStatus);
        createStatusHistory(order, newStatus, request.getNotes());

        if (newStatus == OrderStatus.CANCELLED && previousStatus != OrderStatus.CANCELLED) {
            publishOrderCancelledEvent(order);
        }

        if (newStatus == OrderStatus.DELIVERED && previousStatus != OrderStatus.DELIVERED) {
            publishOrderDeliveredEvent(order);
        }

        if (newStatus == OrderStatus.RETURNED && previousStatus != OrderStatus.RETURNED) {
            publishOrderReturnedEvent(order);
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
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + id));

        if (order.getUserId() == null && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Guest orders can only be cancelled by an administrator");
        }

        if (order.getUserId() != null && !order.getUserId().equals(userId) && !SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Bạn không có quyền hủy đơn hàng này!");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Chỉ những đơn hàng ở trạng thái CHỜ XỬ LÝ mới có thể bị hủy");
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledBy(userId);
        order.setCancelReason("Cancelled by customer");
        if (order.getPaidAmount().compareTo(order.getRefundedAmount()) > 0) {
            order.setPaymentStatus(com.core.beautyshop.modules.order.domain.enums.PaymentStatus.REFUND_PENDING);
        }
        createStatusHistory(order, OrderStatus.CANCELLED, "Khách hàng hủy đơn hàng");

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

    private String checkoutKey(Long userId, CheckoutRequest request) {
        String key = request.getIdempotencyKey();
        if (key == null) return null;
        if (key.isBlank() || key.length() > 128) throw new BusinessException("Invalid Idempotency-Key");
        if (userId == null && (request.getSessionId() == null || request.getSessionId().isBlank())) {
            throw new BusinessException("Guest session is required");
        }
        String scope = userId == null ? "guest:" + request.getSessionId() : "user:" + userId;
        return sha256(scope.length() + ":" + scope + key);
    }

    private String requestHash(CheckoutRequest request) {
        try {
            return sha256(objectMapper.writeValueAsString(request));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid checkout request", exception);
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
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

        List<Long> variantIds = cart.getItems().stream()
                .map(CartItemResponse::getVariantId)
                .sorted()
                .distinct()
                .collect(Collectors.toList());

        java.util.Map<Long, ProductVariantSummaryDto> variantMap = catalogFacade.getVariantSummariesByIds(variantIds);

        List<CartItemResponse> sortedItems = cart.getItems().stream()
                .sorted(java.util.Comparator.comparing(CartItemResponse::getVariantId))
                .toList();

        for (CartItemResponse item : sortedItems) {
            ProductVariantSummaryDto variant = variantMap.get(item.getVariantId());
            if (variant == null) {
                throw new ResourceNotFoundException("Không tìm thấy thông tin biến thể với ID: " + item.getVariantId());
            }

            inventoryFacade.reserveStock(order.getOrderNumber(), item.getVariantId(), item.getQuantity());

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
                    .imageUrl(variant.getProductThumbnailUrl())
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

        if (savedOrder.getAppointmentId() != null) {
            BigDecimal due = savedOrder.getTotalAmount().subtract(savedOrder.getPaidAmount()).max(BigDecimal.ZERO);
            if (due.signum() > 0 && savedOrder.getRefundedAmount().signum() == 0) {
                response.setPaymentInstruction(savedOrder.getPaymentMethod() == com.core.beautyshop.shared.domain.enums.PaymentMethod.CASH
                        ? PaymentInstruction.builder().method("CASH").instructionMessage("Thanh toán " + due + " VND tại quầy Spa.").build()
                        : paymentFacade.processPayment(com.core.beautyshop.shared.domain.enums.PaymentMethod.BANK,
                            PaymentOrderDto.builder().orderNumber(savedOrder.getOrderNumber()).totalAmount(due)
                                .customerName(savedOrder.getCustomerName()).build()));
            }
            return response;
        }

        PaymentOrderDto paymentOrderDto = PaymentOrderDto.builder()
                .orderNumber(savedOrder.getOrderNumber())
                .totalAmount(savedOrder.getTotalAmount())
                .customerName(savedOrder.getCustomerName())
                .build();
        PaymentInstruction instruction = paymentFacade.processPayment(savedOrder.getPaymentMethod(), paymentOrderDto);
        response.setPaymentInstruction(instruction);

        return response;
    }

    private void publishOrderDeliveredEvent(Order order) {
        if (order.getItems() != null) {
            List<OrderEvents.OrderItemSummary> itemSummaries = order.getItems().stream()
                    .map(item -> OrderEvents.OrderItemSummary.builder()
                            .variantId(item.getProductVariantId())
                            .quantity(item.getQuantity())
                            .build())
                    .collect(Collectors.toList());

            eventPublisher.publishEvent(OrderEvents.OrderDeliveredEvent.builder()
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .totalAmount(order.getTotalAmount())
                    .items(itemSummaries)
                    .build());
        }
    }

    private void publishOrderReturnedEvent(Order order) {
        if (order.getItems() != null) {
            List<OrderEvents.OrderItemSummary> itemSummaries = order.getItems().stream()
                    .map(item -> OrderEvents.OrderItemSummary.builder()
                            .variantId(item.getProductVariantId())
                            .quantity(item.getQuantity())
                            .build())
                    .collect(Collectors.toList());

            eventPublisher.publishEvent(OrderEvents.OrderReturnedEvent.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .orderNumber(order.getOrderNumber())
                    .items(itemSummaries)
                    .build());
        }
    }

    private void validateStatusTransition(OrderStatus from, OrderStatus to) {
        if (from == to) return;

        boolean valid = switch (to) {
            case CONFIRMED -> from == OrderStatus.PENDING;
            case PROCESSING -> from == OrderStatus.CONFIRMED || from == OrderStatus.PENDING;
            case SHIPPED -> from == OrderStatus.PROCESSING;
            case DELIVERED -> from == OrderStatus.SHIPPED;
            case CANCELLED -> from == OrderStatus.PENDING || from == OrderStatus.CONFIRMED || from == OrderStatus.PROCESSING;
            case RETURNED -> from == OrderStatus.DELIVERED;
            default -> false;
        };

        if (!valid) {
            throw new BusinessException(
                    "Không thể chuyển trạng thái đơn hàng từ " + from + " sang " + to);
        }
    }
}
