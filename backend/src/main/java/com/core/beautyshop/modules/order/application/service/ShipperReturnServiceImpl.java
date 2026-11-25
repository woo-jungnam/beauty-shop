package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.catalog.application.service.MediaStorageService;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.notification.api.CustomerNotificationFacade;
import com.core.beautyshop.modules.order.application.dto.response.ShipperReturnView;
import com.core.beautyshop.modules.order.application.mapper.OrderMapper;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderItem;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.OrderStatusHistory;
import com.core.beautyshop.modules.order.domain.ProductReturn;
import com.core.beautyshop.modules.order.domain.ProductReturnRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipperReturnServiceImpl implements ShipperReturnService {

    private static final List<String> DEFAULT_PICKUP_STATUSES =
            List.of("PICKUP_ASSIGNED", "PICKED_UP", "RECEIVED", "REFUNDED");

    private final ProductReturnRepository returnRepository;
    private final OrderRepository orderRepository;
    private final MediaStorageService mediaStorageService;
    private final CustomerNotificationFacade customerNotifications;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ShipperReturnView> getShipperReturns(Long shipperId, String status, String keyword, Pageable pageable) {
        Collection<String> targetStatuses = resolveStatusFilter(status);
        String trimmed = keyword == null ? "" : keyword.trim();
        Page<ProductReturn> page = trimmed.isEmpty()
                ? returnRepository.findShipperPickups(shipperId, targetStatuses, pageable)
                : returnRepository.findShipperPickupsByKeyword(
                        shipperId,
                        targetStatuses,
                        "%" + trimmed.toLowerCase(Locale.ROOT) + "%",
                        pageable);
        if (page.isEmpty()) {
            return page.map(row -> toView(row, null, Map.of()));
        }
        Set<Long> orderIds = page.getContent().stream().map(ProductReturn::getOrderId).collect(Collectors.toSet());
        Map<Long, Order> orderMap = orderRepository.findAllById(orderIds).stream()
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));
        Map<Long, ProductVariantSummaryDto> variants = orderMapper.loadItemVariants(orderMap.values().stream());
        return page.map(row -> toView(row, orderMap.get(row.getOrderId()), variants));
    }

    @Override
    @Transactional(readOnly = true)
    public ShipperReturnView getShipperReturnDetail(Long returnId, Long shipperId) {
        ProductReturn row = returnRepository.findById(returnId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted())
                        && shipperId.equals(candidate.getPickupShipperId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu thu hồi hàng hoàn với id: " + returnId));
        Order order = orderRepository.findById(row.getOrderId())
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng liên quan"));
        Map<Long, ProductVariantSummaryDto> variants = orderMapper.loadItemVariants(Stream.of(order));
        return toView(row, order, variants);
    }

    @Override
    @Transactional
    public ShipperReturnView startPickup(Long returnId, Long shipperId) {
        LockedContext ctx = lockOwnedPickup(returnId, shipperId);
        ProductReturn row = ctx.productReturn();
        Order order = ctx.order();

        if (!ProductReturnService.RETURN_METHOD_SHIPPER_PICKUP.equals(row.getReturnMethod())
                || !"PICKUP_ASSIGNED".equals(row.getStatus())) {
            throw new BusinessException("Chỉ có thể bắt đầu thu hồi khi phiếu đang chờ lấy hàng hoàn (PICKUP_ASSIGNED)");
        }
        if (row.getPickupStartedAt() != null) {
            return toView(row, order);
        }

        row.setPickupStartedAt(Instant.now());
        appendOrderHistory(order, "Tài xế đang trên đường đến thu hồi hàng hoàn (Yêu cầu #" + row.getId() + ")");
        returnRepository.save(row);
        orderRepository.save(order);
        return toView(row, order);
    }

    @Override
    @Transactional
    public ShipperReturnView completePickup(Long returnId, Long shipperId, MultipartFile proofImage, String notes) {
        LockedContext ctx = lockOwnedPickup(returnId, shipperId);
        ProductReturn row = ctx.productReturn();
        Order order = ctx.order();

        if ("PICKED_UP".equals(row.getStatus()) && row.getPickupProofUrl() != null) {
            return toView(row, order);
        }
        if (!ProductReturnService.RETURN_METHOD_SHIPPER_PICKUP.equals(row.getReturnMethod())
                || !"PICKUP_ASSIGNED".equals(row.getStatus())) {
            throw new BusinessException("Chỉ có thể xác nhận lấy hàng hoàn khi phiếu đang ở trạng thái chờ thu hồi");
        }
        if (row.getPickupStartedAt() == null) {
            throw new BusinessException("Vui lòng bắt đầu thu hồi trước khi chụp ảnh xác nhận lấy hàng");
        }
        if (proofImage == null || proofImage.isEmpty()) {
            throw new BusinessException("Cần ảnh xác nhận đã lấy hàng hoàn");
        }
        String cleanNotes = notes == null ? "" : notes.trim();
        if (cleanNotes.length() > 500) {
            throw new BusinessException("Ghi chú tối đa 500 ký tự");
        }

        String proofUrl;
        try {
            proofUrl = mediaStorageService.upload(proofImage).url();
        } catch (RuntimeException e) {
            log.warn("Không thể lưu ảnh thu hồi hàng hoàn cho phiếu {}", returnId, e);
            throw new BusinessException("Không thể lưu ảnh xác nhận lấy hàng. Vui lòng thử lại.");
        }
        if (proofUrl == null || proofUrl.isBlank() || proofUrl.length() > 500) {
            throw new BusinessException("Ảnh xác nhận lấy hàng chưa được lưu hợp lệ. Vui lòng thử lại.");
        }

        row.setPickupProofUrl(proofUrl);
        row.setPickupNotes(cleanNotes.isEmpty() ? null : cleanNotes);
        row.setPickedUpAt(Instant.now());
        row.setPickupFailureReason(null);
        row.setStatus("PICKED_UP");

        appendOrderHistory(order,
                "Tài xế đã thu hồi hàng hoàn từ khách cho yêu cầu #" + row.getId()
                        + (cleanNotes.isEmpty() ? " (có ảnh bằng chứng)" : ": " + cleanNotes));

        returnRepository.save(row);
        orderRepository.save(order);

        if (order.getUserId() != null) {
            customerNotifications.publish(
                    order.getUserId(),
                    "return:" + row.getId() + ":PICKED_UP",
                    "ORDER",
                    "Tài xế đã lấy hàng hoàn #" + row.getId(),
                    "BeautyShop đã thu hồi hàng hoàn cho đơn #" + order.getOrderNumber() + " và đang chuyển về kho kiểm định.",
                    "profile?tab=orders&order=" + order.getId());
        }

        return toView(row, order);
    }

    @Override
    @Transactional
    public ShipperReturnView reportPickupFailure(Long returnId, Long shipperId, String reason) {
        LockedContext ctx = lockOwnedPickup(returnId, shipperId);
        ProductReturn row = ctx.productReturn();
        Order order = ctx.order();

        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException("Nhập lý do thu hồi thất bại, tối đa 500 ký tự");
        }
        if (!"PICKUP_ASSIGNED".equals(row.getStatus())) {
            throw new BusinessException("Phiếu thu hồi không ở trạng thái đang chờ lấy hàng");
        }
        if (row.getPickupStartedAt() == null) {
            throw new BusinessException("Vui lòng bắt đầu thu hồi trước khi báo kết quả thất bại");
        }

        String cleanReason = reason.trim();
        row.setPickupAttemptCount(row.getPickupAttemptCount() + 1);
        row.setPickupFailureReason(cleanReason);
        row.setPickupStartedAt(null);
        row.setStatus("APPROVED");

        appendOrderHistory(order,
                "Thu hồi hàng hoàn chưa thành công (Yêu cầu #" + row.getId() + ", lần "
                        + row.getPickupAttemptCount() + "): " + cleanReason);

        returnRepository.save(row);
        orderRepository.save(order);

        if (order.getUserId() != null) {
            customerNotifications.publish(
                    order.getUserId(),
                    "return:" + row.getId() + ":PICKUP_FAILED:" + row.getPickupAttemptCount(),
                    "ORDER",
                    "Chưa thể thu hồi hàng hoàn #" + row.getId(),
                    "Tài xế chưa thu hồi được hàng hoàn cho đơn #" + order.getOrderNumber()
                            + ". Lý do: " + cleanReason + ". BeautyShop sẽ liên hệ sắp xếp lại.",
                    "profile?tab=orders&order=" + order.getId());
        }

        return toView(row, order);
    }

    @Override
    @Transactional
    public ShipperReturnView requestHandover(Long returnId, Long shipperId, String notes) {
        LockedContext ctx = lockOwnedPickup(returnId, shipperId);
        ProductReturn row = ctx.productReturn();
        Order order = ctx.order();

        if (!"PICKED_UP".equals(row.getStatus())) {
            throw new BusinessException("Chỉ có thể báo bàn giao về kho khi đã thu hồi hàng hoàn (PICKED_UP)");
        }
        String cleanNotes = notes == null ? "" : notes.trim();
        if (cleanNotes.length() > 500) {
            throw new BusinessException("Ghi chú bàn giao tối đa 500 ký tự");
        }
        if (row.getHandoverRequestedAt() != null && (cleanNotes.isEmpty() || cleanNotes.equals(row.getHandoverNotes()))) {
            return toView(row, order);
        }

        row.setHandoverRequestedAt(Instant.now());
        row.setHandoverNotes(cleanNotes.isEmpty() ? null : cleanNotes);
        appendOrderHistory(order,
                "Tài xế báo đã bàn giao hàng hoàn về kho cho yêu cầu #" + row.getId()
                        + (cleanNotes.isEmpty() ? "" : ": " + cleanNotes));

        returnRepository.save(row);
        orderRepository.save(order);
        return toView(row, order);
    }

    private record LockedContext(Order order, ProductReturn productReturn) {}

    private LockedContext lockOwnedPickup(Long returnId, Long shipperId) {
        Long orderId = returnRepository.findOrderId(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu thu hồi hàng hoàn với id: " + returnId));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()) && o.getStatus() == OrderStatus.DELIVERED)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng hợp lệ cho phiếu thu hồi #" + returnId));
        ProductReturn row = returnRepository.lock(returnId)
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getIsDeleted())
                        && shipperId.equals(candidate.getPickupShipperId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu thu hồi hàng hoàn với id: " + returnId));
        return new LockedContext(order, row);
    }

    private static Collection<String> resolveStatusFilter(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank() || "ALL".equalsIgnoreCase(rawStatus.trim())) {
            return DEFAULT_PICKUP_STATUSES;
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING_PICKUP" -> List.of("PICKUP_ASSIGNED");
            case "IN_CUSTODY" -> List.of("PICKED_UP");
            case "COMPLETED" -> List.of("RECEIVED", "REFUNDED");
            case "PICKUP_ASSIGNED", "PICKED_UP", "RECEIVED", "REFUNDED" -> List.of(normalized);
            default -> throw new BusinessException("Bộ lọc trạng thái thu hồi không hợp lệ: " + rawStatus);
        };
    }

    private void appendOrderHistory(Order order, String notes) {
        if (order.getStatusHistories() == null) {
            order.setStatusHistories(new ArrayList<>());
        }
        order.getStatusHistories().add(OrderStatusHistory.builder()
                .order(order)
                .status(order.getStatus())
                .shipperId(order.getShipperId())
                .shipperName(order.getShipperName())
                .notes(notes)
                .build());
    }

    private ShipperReturnView toView(ProductReturn row, Order order) {
        Map<Long, ProductVariantSummaryDto> variants = order == null
                ? Map.of()
                : orderMapper.loadItemVariants(Stream.of(order));
        return toView(row, order, variants);
    }

    private ShipperReturnView toView(
            ProductReturn row,
            Order order,
            Map<Long, ProductVariantSummaryDto> variants) {
        List<ProductReturnService.Line> returnLines = readLines(row.getLinesJson());
        List<String> evidenceUrls = readEvidence(row.getEvidenceJson());
        Map<Long, OrderItem> itemMap = new LinkedHashMap<>();
        if (order != null && order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getId() != null) {
                    itemMap.put(item.getId(), item);
                }
            }
        }

        List<ShipperReturnView.ShipperReturnItemView> itemViews = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        for (ProductReturnService.Line line : returnLines) {
            OrderItem item = itemMap.get(line.orderItemId());
            if (item == null) {
                summaryLines.add(line.quantity() + "x Mặt hàng #" + line.orderItemId());
                itemViews.add(ShipperReturnView.ShipperReturnItemView.builder()
                        .orderItemId(line.orderItemId())
                        .productName("Mặt hàng #" + line.orderItemId())
                        .returnQuantity(line.quantity())
                        .purchasedQuantity(line.quantity())
                        .build());
                continue;
            }
            ProductVariantSummaryDto variant = item.getProductVariantId() == null ? null : variants.get(item.getProductVariantId());
            String imageUrl = item.getImageUrl() != null && !item.getImageUrl().isBlank()
                    ? item.getImageUrl()
                    : (variant != null ? variant.getProductThumbnailUrl() : null);
            String label = line.quantity() + "x " + (item.getProductName() != null ? item.getProductName() : "Sản phẩm")
                    + (item.getVariantName() != null && !item.getVariantName().isBlank() ? " (" + item.getVariantName() + ")" : "");
            summaryLines.add(label);
            itemViews.add(ShipperReturnView.ShipperReturnItemView.builder()
                    .orderItemId(item.getId())
                    .productId(variant != null ? variant.getProductId() : null)
                    .variantId(item.getProductVariantId())
                    .sku(item.getSku())
                    .productName(item.getProductName())
                    .variantName(item.getVariantName())
                    .imageUrl(imageUrl)
                    .returnQuantity(line.quantity())
                    .purchasedQuantity(item.getQuantity() == null ? line.quantity() : item.getQuantity())
                    .unitPrice(item.getPrice() == null ? null : item.getPrice().subtract(item.getDiscount() == null ? java.math.BigDecimal.ZERO : item.getDiscount()))
                    .build());
        }

        String fullAddress = order == null ? "" : Stream.of(
                        order.getShippingAddress(),
                        order.getWard(),
                        order.getDistrict(),
                        order.getCity())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(", "));

        return ShipperReturnView.builder()
                .id(row.getId())
                .orderId(row.getOrderId())
                .orderNumber(order == null ? null : order.getOrderNumber())
                .status(row.getStatus())
                .returnMethod(row.getReturnMethod())
                .reason(row.getReason())
                .decisionReason(row.getDecisionReason())
                .refundAmount(row.getRefundAmount())
                .customerName(order == null ? null : order.getCustomerName())
                .customerPhone(order == null ? null : order.getCustomerPhone())
                .pickupAddress(fullAddress)
                .paymentMethod(order == null ? null : order.getPaymentMethod())
                .pickupShipperId(row.getPickupShipperId())
                .pickupShipperName(row.getPickupShipperName())
                .pickupShipperPhone(row.getPickupShipperPhone())
                .pickupAssignedAt(row.getPickupAssignedAt())
                .pickupStartedAt(row.getPickupStartedAt())
                .pickedUpAt(row.getPickedUpAt())
                .pickupProofUrl(row.getPickupProofUrl())
                .pickupNotes(row.getPickupNotes())
                .pickupFailureReason(row.getPickupFailureReason())
                .pickupAttemptCount(row.getPickupAttemptCount())
                .handoverRequestedAt(row.getHandoverRequestedAt())
                .handoverNotes(row.getHandoverNotes())
                .receivedAt(row.getReceivedAt())
                .createdAt(row.getCreatedAt())
                .evidenceUrls(evidenceUrls)
                .itemsSummary(summaryLines)
                .items(itemViews)
                .build();
    }

    private List<ProductReturnService.Line> readLines(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProductReturnService.Line>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> readEvidence(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

