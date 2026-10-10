package com.core.beautyshop.modules.order.application.mapper;

import com.core.beautyshop.modules.order.application.dto.response.OrderItemResponse;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class OrderMapper {

    public OrderResponse toOrderResponse(Order order) {
        if (order == null) {
            return null;
        }
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .servicePackageId(order.getServicePackageId())
                .appointmentId(order.getAppointmentId())
                .spaVisitItems(order.getSpaVisitItems() == null ? List.of() : order.getSpaVisitItems().stream()
                        .map(item -> new com.core.beautyshop.modules.order.api.dto.SpaVisitCharge(item.getAppointmentItemId(),
                                item.getServiceId(), item.getServiceName(), item.getUnitPrice())).toList())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .shippingAddress(formatShippingAddress(order))
                .subTotal(order.getSubTotal())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .paidAmount(order.getPaidAmount())
                .refundedAmount(order.getRefundedAmount())
                .refundReference(order.getRefundReference())
                .paymentDeadline(order.getPaymentDeadline())
                .paidAt(order.getPaidAt())
                .voucherId(order.getVoucherId())
                .carrierName(order.getCarrierName())
                .trackingCode(order.getTrackingCode())
                .cancelReason(order.getCancelReason())
                .cancelledBy(order.getCancelledBy())
                .notes(order.getNotes())
                .statusHistories(order.getStatusHistories() == null ? List.of() : order.getStatusHistories().stream()
                        .map(history -> new com.core.beautyshop.modules.order.application.dto.response.OrderStatusHistoryResponse(
                                history.getStatus(), history.getNotes(), history.getCreatedAt())).toList())
                .createdAt(order.getCreatedAt())
                .items(order.getItems() != null ? 
                        order.getItems().stream().map(this::toOrderItemResponse).collect(Collectors.toList()) 
                        : List.of())
                .build();
    }

    private String formatShippingAddress(Order order) {
        return Stream.of(order.getShippingAddress(), order.getWard(), order.getDistrict(), order.getCity())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(", "));
    }

    public OrderItemResponse toOrderItemResponse(OrderItem item) {
        if (item == null) {
            return null;
        }
        return OrderItemResponse.builder()
                .id(item.getId())
                .variantId(item.getProductVariantId())
                .sku(item.getSku())
                .productName(item.getProductName())
                .variantName(item.getVariantName())
                .imageUrl(item.getImageUrl())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .discount(item.getDiscount())
                .build();
    }
}
