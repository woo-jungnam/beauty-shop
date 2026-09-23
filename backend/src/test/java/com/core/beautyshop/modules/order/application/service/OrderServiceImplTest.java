package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.cart.api.CartFacade;
import com.core.beautyshop.modules.cart.api.dto.CartItemResponse;
import com.core.beautyshop.modules.cart.api.dto.CartResponse;
import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.order.application.dto.request.CheckoutRequest;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.application.factory.OrderFactory;
import com.core.beautyshop.modules.order.application.mapper.OrderMapper;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderItem;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private CartFacade cartFacade;
    @Mock private OrderRepository orderRepository;
    @Mock private InventoryFacade inventoryFacade;
    @Mock private CatalogFacade catalogFacade;
    @Mock private IdentityFacade identityFacade;
    @Mock private OrderFactory orderFactory;
    @Mock private OrderMapper orderMapper;
    @Mock private PaymentFacade paymentFacade;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private OrderServiceImpl orderService;

    @Test
    void checkoutCapsDiscountAtGrossAmount() {
        CheckoutRequest request = new CheckoutRequest();
        request.setSessionId("guest-session");

        CartItemResponse cartItem = CartItemResponse.builder()
                .variantId(1L)
                .quantity(1)
                .build();
        CartResponse cart = CartResponse.builder().items(List.of(cartItem)).build();
        ProductVariantSummaryDto variant = ProductVariantSummaryDto.builder()
                .id(1L)
                .sku("SKU-1")
                .productName("Product")
                .variantName("Default")
                .price(new BigDecimal("100.00"))
                .build();
        Order order = Order.builder()
                .orderNumber("ORD-TEST")
                .shippingFee(BigDecimal.ZERO)
                .discountAmount(new BigDecimal("150.00"))
                .items(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();
        OrderResponse response = new OrderResponse();

        when(cartFacade.lockCart(isNull(), eq("guest-session"))).thenReturn(cart);
        when(orderFactory.createOrder(request, null)).thenReturn(order);
        when(catalogFacade.getVariantSummariesByIds(List.of(1L))).thenReturn(Map.of(1L, variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toOrderResponse(order)).thenReturn(response);

        orderService.checkout(null, request);

        assertEquals(new BigDecimal("100.00"), order.getDiscountAmount());
        assertEquals(new BigDecimal("0.00"), order.getTotalAmount());
    }

    @Test
    void guestCanReadOwnOrderOnlyWithMatchingSession() {
        Order order = Order.builder()
                .guestSessionId("guest-session")
                .items(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();
        order.setId(10L);
        OrderResponse response = new OrderResponse();
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderMapper.toOrderResponse(order)).thenReturn(response);

        assertEquals(response, orderService.getOrderById(10L, "guest-session"));
        assertThrows(AccessDeniedException.class,
                () -> orderService.getOrderById(10L, "another-session"));
    }

    @Test
    void returnedOrderPublishesStockRestorationEvent() {
        Order order = Order.builder()
                .orderNumber("ORD-RETURN")
                .status(OrderStatus.DELIVERED)
                .items(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();
        order.setId(11L);
        order.getItems().add(OrderItem.builder()
                .order(order)
                .productVariantId(21L)
                .quantity(3)
                .build());
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.RETURNED);

        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toOrderResponse(order)).thenReturn(new OrderResponse());

        orderService.updateOrderStatus(11L, request);

        verify(eventPublisher).publishEvent(any(OrderEvents.OrderReturnedEvent.class));
    }
}
