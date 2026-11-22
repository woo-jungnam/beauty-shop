package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.catalog.application.service.MediaStorageService;
import com.core.beautyshop.modules.order.application.dto.response.ShipperOrderView;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.application.dto.response.ShipperStatsView;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.OrderStatusHistory;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipperOrderServiceImpl implements ShipperOrderService {

    private static final ZoneId DELIVERY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OrderRepository orderRepository;
    private final MediaStorageService mediaStorageService;
    private final OrderService orderService;

    @Value("${SHIPPER_EARNING_PER_DELIVERY:}")
    private BigDecimal earningPerDelivery;

    @Override
    @Transactional(readOnly = true)
    public Page<ShipperOrderView> getShipperOrders(Long shipperId, OrderStatus status, Pageable pageable) {
        Page<Order> orders = orderRepository.findShipperDeliveries(shipperId, status, pageable);
        return orders.map(this::toShipperView);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipperOrderView getShipperOrderDetail(Long orderId, Long shipperId) {
        Order order = orderRepository.findById(orderId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted())
                        && shipperId.equals(candidate.getShipperId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + orderId));
        return toShipperView(order);
    }

    @Override
    @Transactional
    public ShipperOrderView startDelivery(Long orderId, Long shipperId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted())
                        && shipperId.equals(candidate.getShipperId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + orderId));

        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BusinessException("Chỉ có thể bắt đầu giao cho đơn hàng đang ở trạng thái đã xuất kho (SHIPPED)");
        }

        if (order.getDeliveryStartedAt() != null) return toShipperView(order);
        order.setDeliveryStartedAt(Instant.now());
        String note = "Tài xế đang trên đường giao hàng tới địa chỉ khách hàng";
        createStatusHistory(order, order.getStatus(), note);
        Order saved = orderRepository.save(order);
        return toShipperView(saved);
    }

    @Override
    @Transactional
    public ShipperOrderView completeDelivery(Long orderId, Long shipperId, MultipartFile proofImage, String notes) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted())
                        && shipperId.equals(candidate.getShipperId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + orderId));

        if (order.getStatus() == OrderStatus.DELIVERED && order.getDeliveryProofUrl() != null) {
            return toShipperView(order);
        }
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BusinessException("Chỉ có thể xác nhận đã giao khi đơn hàng đang được vận chuyển");
        }

        requireDeliveryStarted(order);
        if (proofImage == null || proofImage.isEmpty()) {
            throw new BusinessException("Cần ảnh xác nhận giao hàng");
        }
        String deliveryNotes = notes == null ? "" : notes.trim();
        if (deliveryNotes.length() > 500) {
            throw new BusinessException("Ghi chú tối đa 500 ký tự");
        }

        String proofUrl;
        try {
            proofUrl = mediaStorageService.upload(proofImage).url();
        } catch (RuntimeException e) {
            log.warn("Không thể lưu ảnh giao hàng cho đơn {}", orderId, e);
            throw new BusinessException("Không thể lưu ảnh giao hàng. Vui lòng thử lại.");
        }
        if (proofUrl == null || proofUrl.isBlank() || proofUrl.length() > 500) {
            throw new BusinessException("Ảnh giao hàng chưa được lưu hợp lệ. Vui lòng thử lại.");
        }
        order.setDeliveryProofUrl(proofUrl);

        order.setDeliveredNotes(deliveryNotes.isEmpty() ? null : deliveryNotes);

        // Nếu COD: tài xế đã thu tiền mặt, tự động cập nhật thanh toán hoàn tất
        if (order.getPaymentMethod() == PaymentMethod.COD && order.getPaymentStatus() != PaymentStatus.PAID) {
            order.setShipperCodCollected(outstandingCod(order));
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAmount(order.getTotalAmount());
            order.setPaidAt(Instant.now());
        }

        updateDeliveryStatus(orderId, OrderStatus.DELIVERED,
                deliveryNotes.isEmpty() ? "Tài xế xác nhận giao thành công, có ảnh bằng chứng" : deliveryNotes);
        return toShipperView(order);
    }

    @Override
    @Transactional
    public ShipperOrderView reportDeliveryFailure(Long orderId, Long shipperId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted())
                        && shipperId.equals(candidate.getShipperId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id: " + orderId));
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException("Nhập lý do giao thất bại, tối đa 500 ký tự");
        }
        if (order.getStatus() == OrderStatus.DELIVERY_FAILED) {
            return toShipperView(order);
        }
        requireDeliveryStarted(order);
        updateDeliveryStatus(orderId, OrderStatus.DELIVERY_FAILED, reason.trim());
        return toShipperView(order);
    }

    private void requireDeliveryStarted(Order order) {
        if (order.getStatus() != OrderStatus.SHIPPED || order.getDeliveryStartedAt() == null) {
            throw new BusinessException("Vui lòng bắt đầu giao trước khi ghi nhận kết quả");
        }
    }

    private void updateDeliveryStatus(Long orderId, OrderStatus status, String notes) {
        var request = new UpdateOrderStatusRequest();
        request.setStatus(status);
        request.setNotes(notes);
        orderService.updateOrderStatus(orderId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShipperOrderView> getDeliveryHistory(Long shipperId, LocalDate from, LocalDate to,
                                                    OrderStatus status, Pageable pageable) {
        validateDates(from, to);
        return orderRepository.findShipperHistory(shipperId, startOfDay(from), startOfDay(to.plusDays(1)),
                status, pageable).map(entry -> {
                    OrderStatusHistory failure = entry.getPreviousFailure();
                    if (failure == null) return toShipperView(entry.getDelivery());
                    return ShipperOrderView.builder()
                            .id(entry.getDelivery().getId())
                            .orderNumber(entry.getDelivery().getOrderNumber())
                            .status(OrderStatus.DELIVERY_FAILED)
                            .createdAt(failure.getCreatedAt())
                            .notes(failure.getNotes())
                            .build();
                });
    }

    @Override
    @Transactional(readOnly = true)
    public ShipperStatsView getShipperStats(Long shipperId, LocalDate from, LocalDate to) {
        validateDates(from, to);
        var totals = orderRepository.getShipperDeliveryTotals(shipperId, startOfDay(from), startOfDay(to.plusDays(1)));
        LocalDate today = LocalDate.now(DELIVERY_ZONE);
        LocalDate week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate month = today.withDayOfMonth(1);
        LocalDate summaryStart = week.isBefore(month) ? week : month;
        LocalDate first = from.isBefore(summaryStart) ? from : summaryStart;
        LocalDate last = to.isAfter(today) ? to : today;

        Map<LocalDate, Long> days = new LinkedHashMap<>();
        from.datesUntil(to.plusDays(1)).forEach(day -> days.put(day, 0L));
        long todayOrders = 0;
        long weekOrders = 0;
        long monthOrders = 0;
        for (Instant timestamp : orderRepository.findShipperDeliveryTimes(shipperId, startOfDay(first),
                startOfDay(last.plusDays(1)))) {
            LocalDate day = timestamp.atZone(DELIVERY_ZONE).toLocalDate();
            days.computeIfPresent(day, (date, count) -> count + 1);
            if (day.equals(today)) todayOrders++;
            if (!day.isBefore(week) && !day.isAfter(today)) weekOrders++;
            if (!day.isBefore(month) && !day.isAfter(today)) monthOrders++;
        }
        BigDecimal estimatedEarnings = earningPerDelivery == null ? null
                : earningPerDelivery.multiply(BigDecimal.valueOf(totals.getDeliveredOrders()));
        return new ShipperStatsView(totals.getTotalOrders(), totals.getDeliveredOrders(),
                totals.getTotalCodCollected(), totals.getTotalRevenue(), totals.getTotalDeliveryFees(),
                estimatedEarnings, todayOrders, weekOrders, monthOrders, days);
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(DELIVERY_ZONE).toInstant();
    }

    private static void validateDates(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || !to.isBefore(from.plusDays(366))) {
            throw new BusinessException("Khoảng ngày phải hợp lệ và tối đa 366 ngày");
        }
    }

    private static BigDecimal outstandingCod(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.COD || order.getPaymentStatus() == PaymentStatus.PAID) {
            return BigDecimal.ZERO;
        }
        return Objects.requireNonNullElse(order.getTotalAmount(), BigDecimal.ZERO)
                .subtract(Objects.requireNonNullElse(order.getPaidAmount(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
    }

    private ShipperOrderView toShipperView(Order order) {
        BigDecimal codAmount = outstandingCod(order);

        List<String> itemsSummary = order.getItems() != null ? order.getItems().stream()
                .map(item -> (item.getQuantity() != null ? item.getQuantity() + "x " : "")
                        + (item.getProductName() != null ? item.getProductName() : "Sản phẩm")
                        + (item.getVariantName() != null && !item.getVariantName().isBlank() ? " (" + item.getVariantName() + ")" : ""))
                .collect(Collectors.toList()) : List.of();

        String fullAddress = Stream.of(order.getShippingAddress(), order.getWard(), order.getDistrict(), order.getCity())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(", "));

        return ShipperOrderView.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .shippingAddress(fullAddress)
                .totalAmount(order.getTotalAmount())
                .codAmount(codAmount)
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .status(order.getStatus())
                .deliveryProofUrl(order.getDeliveryProofUrl())
                .deliveredNotes(order.getDeliveredNotes())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .deliveredAt(order.getDeliveredAt())
                .assignedAt(order.getShipperAssignedAt())
                .deliveryStartedAt(order.getDeliveryStartedAt())
                .codCollected(order.getShipperCodCollected())
                .codRemitted(order.getShipperCodRemitted())
                .itemsSummary(itemsSummary)
                .build();
    }

    private void createStatusHistory(Order order, OrderStatus status, String notes) {
        if (order.getStatusHistories() == null) {
            order.setStatusHistories(new ArrayList<>());
        }
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .status(status)
                .shipperId(order.getShipperId())
                .shipperName(order.getShipperName())
                .notes(notes)
                .build();
        order.getStatusHistories().add(history);
    }
}
