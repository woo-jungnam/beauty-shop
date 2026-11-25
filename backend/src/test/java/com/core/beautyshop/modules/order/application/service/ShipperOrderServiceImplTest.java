package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.catalog.application.service.MediaStorageService;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.order.application.dto.request.AssignShipperRequest;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.application.dto.response.ShipperOrderView;
import com.core.beautyshop.modules.order.application.mapper.OrderMapper;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderItem;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipperOrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MediaStorageService mediaStorageService;

    @Mock
    private OrderService orderService;

    @Spy
    private OrderMapper orderMapper = new OrderMapper();

    @InjectMocks
    private ShipperOrderServiceImpl shipperOrderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = Order.builder()
                .orderNumber("ORD-TEST-100")
                .userId(1L)
                .customerName("Nguyễn Văn A")
                .customerPhone("0987654321")
                .shippingAddress("123 Nguyễn Huệ, Q.1, TP.HCM")
                .totalAmount(new BigDecimal("500000"))
                .status(OrderStatus.SHIPPED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .shipperId(50L)
                .shipperName("Bác Tài Shipper")
                .shipperPhone("0909090909")
                .statusHistories(new ArrayList<>())
                .items(new ArrayList<>())
                .build();
        testOrder.setId(100L);

        OrderItem item = OrderItem.builder()
                .sku("CERAVE-50")
                .productName("Kem dưỡng ẩm Cerave")
                .variantName("50ml")
                .imageUrl("/uploads/cerave.jpg")
                .quantity(2)
                .price(new BigDecimal("250000"))
                .build();
        item.setId(1L);
        testOrder.getItems().add(item);
    }

    @Test
    void testGetShipperOrders() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(orderRepository.findShipperDeliveries(eq(50L), eq(OrderStatus.SHIPPED), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(testOrder), pageable, 1));

        Page<ShipperOrderView> result = shipperOrderService.getShipperOrders(50L, OrderStatus.SHIPPED, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        ShipperOrderView view = result.getContent().get(0);
        assertEquals("ORD-TEST-100", view.getOrderNumber());
        assertEquals("Nguyễn Văn A", view.getCustomerName());
        assertEquals(1, view.getItemsSummary().size());
        assertTrue(view.getItemsSummary().get(0).contains("Kem dưỡng ẩm Cerave"));
        var item = view.getItems().getFirst();
        assertEquals("CERAVE-50", item.getSku());
        assertEquals("/uploads/cerave.jpg", item.getImageUrl());
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("250000"), item.getPrice());
    }

    @Test
    void testGetShipperOrdersWithKeywordQueriesTrimmedLowercasePattern() {
        PageRequest pageable = PageRequest.of(1, 10);
        when(orderRepository.findShipperDeliveriesByKeyword(
                eq(50L), isNull(), eq("%ord-test%"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(testOrder), pageable, 11));

        Page<ShipperOrderView> result = shipperOrderService.getShipperOrders(50L, null, "  ORD-TEST  ", pageable);

        assertNotNull(result);
        assertEquals(11, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals("ORD-TEST-100", result.getContent().get(0).getOrderNumber());
    }

    @Test
    void otherShipperCannotUpdateAssignedOrder() {
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testOrder));

        assertThrows(com.core.beautyshop.shared.exception.ResourceNotFoundException.class,
                () -> shipperOrderService.startDelivery(100L, 51L));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void detailUsesCatalogImageForLegacyItemWithNoOrderSnapshot() {
        OrderItem item = testOrder.getItems().getFirst();
        item.setProductVariantId(12L);
        item.setImageUrl(null);
        var variant = ProductVariantSummaryDto.builder()
                .productId(44L)
                .productThumbnailUrl("/uploads/catalog-image.jpg")
                .build();
        doReturn(Map.of(12L, variant)).when(orderMapper).loadItemVariants(any());
        when(orderRepository.findById(100L)).thenReturn(Optional.of(testOrder));

        var view = shipperOrderService.getShipperOrderDetail(100L, 50L);

        assertEquals("/uploads/catalog-image.jpg", view.getItems().getFirst().getImageUrl());
        assertEquals("Kem dưỡng ẩm Cerave", view.getItems().getFirst().getProductName());
        assertEquals(44L, view.getItems().getFirst().getProductId());
        assertThrows(com.core.beautyshop.shared.exception.ResourceNotFoundException.class,
                () -> shipperOrderService.getShipperOrderDetail(100L, 51L));
    }

    @Test
    void testStartDelivery_Success() {
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShipperOrderView result = shipperOrderService.startDelivery(100L, 50L);
        assertNotNull(result.getDeliveryStartedAt());
        assertEquals(result.getDeliveryStartedAt(), shipperOrderService.startDelivery(100L, 50L).getDeliveryStartedAt());

        assertNotNull(result);
        assertEquals(OrderStatus.SHIPPED, result.getStatus());
        assertEquals(1, testOrder.getStatusHistories().size());
        assertTrue(testOrder.getStatusHistories().get(0).getNotes().contains("đang trên đường giao"));
    }

    @Test
    void testStartDelivery_InvalidStatus_ThrowsException() {
        testOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testOrder));

        assertThrows(BusinessException.class, () -> shipperOrderService.startDelivery(100L, 50L));
    }

    @Test
    void cannotRecordDeliveryOutcomeBeforeStarting() {
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testOrder));
        var photo = new MockMultipartFile("proofImage", "proof.jpg", "image/jpeg", new byte[]{1});

        assertThrows(BusinessException.class, () -> shipperOrderService.completeDelivery(100L, 50L, photo, null));
        assertThrows(BusinessException.class, () -> shipperOrderService.reportDeliveryFailure(100L, 50L, "Recipient absent"));
        verifyNoInteractions(mediaStorageService, orderService);
    }

    @Test
    void failedUploadLeavesDeliveryAndCodUntouched() {
        testOrder.setDeliveryStartedAt(Instant.now());
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testOrder));
        var photo = new MockMultipartFile("proofImage", "proof.jpg", "image/jpeg", new byte[]{1});
        when(mediaStorageService.upload(photo)).thenThrow(new IllegalStateException("storage unavailable"));

        assertThrows(BusinessException.class, () -> shipperOrderService.completeDelivery(100L, 50L, photo, null));
        assertEquals(OrderStatus.SHIPPED, testOrder.getStatus());
        assertEquals(PaymentStatus.PENDING, testOrder.getPaymentStatus());
        assertNull(testOrder.getDeliveredAt());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }

    @Test
    void reportFailureRequiresOwnershipAndDelegatesToOrderWorkflow() {
        testOrder.setDeliveryStartedAt(Instant.now());
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testOrder));
        assertThrows(com.core.beautyshop.shared.exception.ResourceNotFoundException.class,
                () -> shipperOrderService.reportDeliveryFailure(100L, 51L, "Không liên hệ được khách"));
        assertThrows(BusinessException.class,
                () -> shipperOrderService.reportDeliveryFailure(100L, 50L, " "));
        verifyNoInteractions(orderService);

        when(orderService.updateOrderStatus(eq(100L), any())).thenAnswer(invocation -> {
            com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest update =
                    invocation.getArgument(1);
            assertEquals(OrderStatus.DELIVERY_FAILED, update.getStatus());
            assertEquals("Không liên hệ được khách", update.getNotes());
            testOrder.setStatus(update.getStatus());
            return null;
        });

        var result = shipperOrderService.reportDeliveryFailure(100L, 50L, " Không liên hệ được khách ");
        assertEquals(OrderStatus.DELIVERY_FAILED, result.getStatus());
        assertEquals(PaymentStatus.PENDING, testOrder.getPaymentStatus());
        shipperOrderService.reportDeliveryFailure(100L, 50L, "Không liên hệ được khách");
        verify(orderService).updateOrderStatus(eq(100L), any());
    }

    @Test
    void testCompleteDelivery_WithProofPhotoAndNotes_Success() {
        testOrder.setDeliveryStartedAt(Instant.now());
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testOrder));
        when(orderService.updateOrderStatus(eq(100L), any())).thenAnswer(invocation -> {
            com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest update =
                    invocation.getArgument(1);
            assertEquals(OrderStatus.DELIVERED, update.getStatus());
            testOrder.setStatus(update.getStatus());
            testOrder.setDeliveredAt(Instant.now());
            return null;
        });

        testOrder.setPaidAmount(new BigDecimal("100000"));
        MockMultipartFile mockFile = new MockMultipartFile(
                "proofImage",
                "proof.jpg",
                "image/jpeg",
                "fake-image-bytes".getBytes()
        );
        MediaStorageService.MediaView mockMedia = new MediaStorageService.MediaView(1L, "proof.jpg", "image/jpeg", 100L, "/uploads/proof-uuid.jpg");
        when(mediaStorageService.upload(mockFile)).thenReturn(mockMedia);

        ShipperOrderView result = shipperOrderService.completeDelivery(100L, 50L, mockFile, "Đã giao tận tay cho khách");

        assertNotNull(result);
        assertEquals(OrderStatus.DELIVERED, testOrder.getStatus());
        assertEquals(PaymentStatus.PAID, testOrder.getPaymentStatus());
        assertEquals(new BigDecimal("400000"), testOrder.getShipperCodCollected());
        assertEquals("/uploads/proof-uuid.jpg", testOrder.getDeliveryProofUrl());
        assertEquals("Đã giao tận tay cho khách", testOrder.getDeliveredNotes());
        assertNotNull(testOrder.getDeliveredAt());

        verify(orderService).updateOrderStatus(eq(100L), any());
    }
}
