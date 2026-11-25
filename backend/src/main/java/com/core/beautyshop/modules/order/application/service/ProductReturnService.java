package com.core.beautyshop.modules.order.application.service;

import com.core.beautyshop.modules.catalog.application.service.MediaStorageService;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.notification.api.CustomerNotificationFacade;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.promotion.api.PromotionFacade;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProductReturnService {
    public static final String RETURN_METHOD_SELF_SHIP = "SELF_SHIP";
    public static final String RETURN_METHOD_SHIPPER_PICKUP = "SHIPPER_PICKUP";

    private final OrderRepository orders;
    private final ProductReturnRepository returns;
    private final RefundConfirmationRepository refunds;
    private final InventoryFacade inventory;
    private final MediaStorageService mediaStorage;
    private final CustomerNotificationFacade customerNotifications;
    private final PromotionFacade promotions;
    private final IdentityFacade identityFacade;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    @org.springframework.beans.factory.annotation.Value("${app.order.return-window-days:14}")
    private int returnWindowDays = 14;

    public record Line(Long orderItemId, int quantity) {}

    public record Request(
            String reason,
            List<Line> lines,
            List<String> evidenceUrls,
            String refundMethod,
            String bankBin,
            String bankName,
            String accountNumber,
            String accountName,
            String returnMethod) {
        public Request(String reason, List<Line> lines, List<String> evidenceUrls) {
            this(reason, lines, evidenceUrls, null, null, null, null, null, null);
        }

        public Request(
                String reason,
                List<Line> lines,
                List<String> evidenceUrls,
                String refundMethod,
                String bankBin,
                String bankName,
                String accountNumber,
                String accountName) {
            this(reason, lines, evidenceUrls, refundMethod, bankBin, bankName, accountNumber, accountName, null);
        }
    }

    public record View(
            Long id,
            Long orderId,
            String status,
            String reason,
            String decisionReason,
            BigDecimal refundAmount,
            List<Line> lines,
            List<String> evidenceUrls,
            Instant createdAt,
            Long replacementOrderId,
            String replacementReason,
            Instant replacementLinkedAt,
            String refundMethod,
            String bankBin,
            String bankName,
            String accountNumber,
            String accountName,
            String refundVoucherCode,
            String returnMethod,
            Long pickupShipperId,
            String pickupShipperName,
            String pickupShipperPhone,
            Instant pickupAssignedAt,
            Instant pickupStartedAt,
            Instant pickedUpAt,
            String pickupProofUrl,
            String pickupNotes,
            String pickupFailureReason,
            int pickupAttemptCount,
            Instant handoverRequestedAt,
            String handoverNotes,
            Instant receivedAt,
            String orderNumber,
            String customerName,
            String customerPhone,
            String pickupAddress) {}

    public record Replacement(Long replacementOrderId, String reason) {}

    public record Decision(boolean approved, String reason, String returnMethod) {
        public Decision(boolean approved, String reason) {
            this(approved, reason, null);
        }
    }

    public record AssignPickup(Long shipperId, String notes) {}

    public record Confirmation(String reference, String reason) {}

    public record Balances(
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal remainingOverpayment,
            BigDecimal returnLiability,
            BigDecimal unsettledCod) {}

    public record OrderReturnSummary(int totalCount, int activeCount, String latestStatus) {}

    @Transactional
    public View linkReplacement(Long id, Replacement c) {
        var original = orderForReturn(id);
        var row = lockedReturn(id);
        if (c == null || c.replacementOrderId() == null || c.reason() == null || c.reason().isBlank() || c.reason().length() > 500) {
            throw new BusinessException("Replacement order and reason required");
        }
        if (!"REFUNDED".equals(row.getStatus())) {
            throw new BusinessException("Confirm the real return refund before linking a separate replacement sale");
        }
        if (row.getReplacementOrderId() != null) {
            if (!row.getReplacementOrderId().equals(c.replacementOrderId()) || !row.getReplacementReason().equals(c.reason())) {
                throw new BusinessException("Replacement sale already linked differently");
            }
            return view(row, original);
        }
        var replacement = orders.findById(c.replacementOrderId())
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .orElseThrow(() -> new BusinessException("Replacement order not found"));
        boolean sameOwner = original.getUserId() != null
                ? original.getUserId().equals(replacement.getUserId())
                : replacement.getUserId() == null && original.getGuestSessionId() != null
                        && original.getGuestSessionId().equals(replacement.getGuestSessionId());
        if (!sameOwner || original.getId().equals(replacement.getId()) || replacement.getAppointmentId() != null
                || replacement.getServicePackageId() != null || replacement.getStatus() == OrderStatus.CANCELLED
                || replacement.getItems() == null || replacement.getItems().isEmpty()) {
            throw new BusinessException("Replacement must be a separate active product sale belonging to the same customer or guest session");
        }
        row.setReplacementOrderId(replacement.getId());
        row.setReplacementReason(c.reason());
        row.setReplacementLinkedAt(Instant.now());
        row.setReplacementLinkedBy(SecurityUtils.getCurrentUserId());
        return view(row, original);
    }

    @Transactional(readOnly = true)
    public Balances balances(Long id) {
        var order = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        BigDecimal remaining = order.getPaidAmount().subtract(order.getRefundedAmount()).max(BigDecimal.ZERO);
        BigDecimal excess = order.getPaidAmount().subtract(order.getTotalAmount())
                .subtract(refunds.sumOverpaymentRefunds(id)).max(BigDecimal.ZERO).min(remaining);
        BigDecimal liability = returns.findByOrderIdOrderByIdDesc(id).stream()
                .filter(r -> r.getStatus().equals("RECEIVED"))
                .map(ProductReturn::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Balances(order.getPaidAmount(), order.getRefundedAmount(), excess, liability, order.unsettledCod());
    }

    @Transactional(readOnly = true)
    public List<View> customerList(Long orderId, String session) {
        var order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        owned(order, session);
        return returns.findByOrderIdOrderByIdDesc(orderId).stream().map(row -> view(row, order)).toList();
    }

    @Transactional(readOnly = true)
    public List<View> adminOrderReturns(Long orderId) {
        var order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return returns.findByOrderIdOrderByIdDesc(orderId).stream().map(row -> view(row, order)).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, OrderReturnSummary> summarizeByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Map.of();
        Set<String> activeStatuses = Set.of("REQUESTED", "APPROVED", "PICKUP_ASSIGNED", "PICKED_UP", "RECEIVED");
        Map<Long, List<ProductReturn>> grouped = new LinkedHashMap<>();
        for (ProductReturn r : returns.findByOrderIdInOrderByIdDesc(orderIds)) {
            grouped.computeIfAbsent(r.getOrderId(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, OrderReturnSummary> result = new LinkedHashMap<>();
        for (var entry : grouped.entrySet()) {
            List<ProductReturn> items = entry.getValue();
            int activeCount = (int) items.stream().filter(r -> activeStatuses.contains(r.getStatus())).count();
            String latestStatus = items.isEmpty() ? null : items.get(0).getStatus();
            result.put(entry.getKey(), new OrderReturnSummary(items.size(), activeCount, latestStatus));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<View> list(Pageable paging) {
        return list(null, null, null, paging);
    }

    @Transactional(readOnly = true)
    public Page<View> list(String status, String returnMethod, Long pickupShipperId, Pageable paging) {
        String cleanStatus = trimToNull(status);
        String cleanMethod = trimToNull(returnMethod);
        if (cleanStatus != null) cleanStatus = cleanStatus.toUpperCase(Locale.ROOT);
        if (cleanMethod != null) cleanMethod = normalizeReturnMethod(cleanMethod);

        Page<ProductReturn> page = (cleanStatus == null && cleanMethod == null && pickupShipperId == null)
                ? returns.findAll(paging)
                : returns.searchAdminReturns(cleanStatus, cleanMethod, pickupShipperId, paging);
        if (page.isEmpty()) return page.map(row -> view(row, (Order) null));
        Set<Long> orderIds = page.getContent().stream().map(ProductReturn::getOrderId).collect(Collectors.toSet());
        Map<Long, Order> orderMap = orders.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));
        return page.map(row -> view(row, orderMap.get(row.getOrderId())));
    }

    @Transactional
    public MediaStorageService.MediaView uploadEvidence(Long orderId, String session, MultipartFile file) {
        var order = orders.findById(orderId).filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        owned(order, session);
        if (order.getStatus() != OrderStatus.DELIVERED || order.getAppointmentId() != null || order.getServicePackageId() != null) {
            throw new BusinessException("Chỉ đơn hàng mỹ phẩm đã giao mới hỗ trợ tải ảnh đổi trả");
        }
        if (order.getDeliveredAt() != null && order.getDeliveredAt().plus(Duration.ofDays(Math.max(0, returnWindowDays))).isBefore(Instant.now())) {
            throw new BusinessException("Đã hết thời hạn gửi yêu cầu đổi trả cho đơn hàng này");
        }
        return mediaStorage.upload(file);
    }

    @Transactional
    public View request(Long orderId, String session, String key, Request command) {
        var order = lockedOrder(orderId);
        owned(order, session);
        if (order.getStatus() != OrderStatus.DELIVERED || order.getAppointmentId() != null || order.getServicePackageId() != null) {
            throw new BusinessException("Only delivered product orders accept return requests");
        }
        if (order.getDeliveredAt() != null && order.getDeliveredAt().plus(Duration.ofDays(Math.max(0, returnWindowDays))).isBefore(Instant.now())) {
            throw new BusinessException("The configured return request window has ended; contact customer care");
        }
        if (command == null || command.reason() == null || command.reason().isBlank()
                || command.reason().length() > 1000 || command.lines() == null || command.lines().isEmpty()
                || command.lines().size() > 100) {
            throw new BusinessException("Return reason and item quantities required");
        }
        var evidence = command.evidenceUrls() == null ? List.<String>of() : command.evidenceUrls();
        if (evidence.size() > 8 || evidence.stream().anyMatch(url -> !isValidEvidenceUrl(url))) {
            throw new BusinessException("Provide at most 8 valid image paths or http(s) evidence links");
        }
        String refundMethod = normalizeRefundMethod(command.refundMethod(), order);
        String returnMethod = normalizeReturnMethod(command.returnMethod());
        String bankBin = trimToNull(command.bankBin());
        String bankName = trimToNull(command.bankName());
        String accountNumber = trimToNull(command.accountNumber());
        String accountName = trimToNull(command.accountName());
        validateBankDetails(refundMethod, bankBin, bankName, accountNumber, accountName);

        String requestKey = key == null ? null : orderId + ":" + key;
        if (requestKey != null && (key.isBlank() || requestKey.length() > 100)) {
            throw new BusinessException("Invalid return request key");
        }
        if (requestKey != null) {
            var previous = returns.findByRequestKey(requestKey);
            if (previous.isPresent()) {
                var row = previous.get();
                if (!json(command.lines()).equals(row.getLinesJson())
                        || !command.reason().equals(row.getReason())
                        || !json(evidence).equals(row.getEvidenceJson())
                        || !Objects.equals(refundMethod, row.getRefundMethod())
                        || !Objects.equals(returnMethod, row.getReturnMethod())
                        || !Objects.equals(bankBin, row.getRefundBankBin())
                        || !Objects.equals(bankName, row.getRefundBankName())
                        || !Objects.equals(accountNumber, row.getRefundAccountNumber())
                        || !Objects.equals(accountName, row.getRefundAccountName())) {
                    throw new BusinessException("Request key reused with different data");
                }
                return view(row, order);
            }
        }
        var prior = returns.findByOrderIdOrderByIdDesc(orderId);
        Set<Long> ids = new HashSet<>();
        BigDecimal selected = BigDecimal.ZERO;
        for (var line : command.lines()) {
            if (line == null || line.orderItemId() == null || line.quantity() <= 0 || !ids.add(line.orderItemId())) {
                throw new BusinessException("Unique return items with positive quantities required");
            }
            var item = order.getItems().stream()
                    .filter(i -> i.getId().equals(line.orderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Return item does not belong to this order"));
            int committed = prior.stream()
                    .filter(r -> !r.getStatus().equals("REJECTED"))
                    .flatMap(r -> lines(r).stream())
                    .filter(l -> l.orderItemId().equals(line.orderItemId()))
                    .mapToInt(Line::quantity)
                    .sum();
            if (line.quantity() > item.getQuantity() - committed) {
                throw new BusinessException("Return quantity exceeds unreturned purchased quantity");
            }
            selected = selected.add(item.getPrice().subtract(item.getDiscount()).multiply(BigDecimal.valueOf(line.quantity())));
        }
        BigDecimal amount = proratedProductRefund(order, selected);
        int previouslyRequested = prior.stream()
                .filter(r -> !r.getStatus().equals("REJECTED"))
                .flatMap(r -> lines(r).stream())
                .mapToInt(Line::quantity)
                .sum();
        int nowRequested = command.lines().stream().mapToInt(Line::quantity).sum();
        if (previouslyRequested + nowRequested == order.getItems().stream().mapToInt(OrderItem::getQuantity).sum()) {
            BigDecimal committedAmount = prior.stream()
                    .filter(r -> !r.getStatus().equals("REJECTED"))
                    .map(ProductReturn::getRefundAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            amount = proratedProductRefund(order, order.getSubTotal()).subtract(committedAmount).max(BigDecimal.ZERO);
        }
        var row = new ProductReturn();
        row.setOrderId(orderId);
        row.setReason(command.reason());
        row.setRefundAmount(amount);
        row.setLinesJson(json(command.lines()));
        row.setEvidenceJson(json(evidence));
        row.setRequestKey(requestKey);
        row.setRefundMethod(refundMethod);
        row.setReturnMethod(returnMethod);
        row.setRefundBankBin(bankBin);
        row.setRefundBankName(bankName);
        row.setRefundAccountNumber(accountNumber);
        row.setRefundAccountName(accountName);
        return view(returns.saveAndFlush(row), order);
    }

    static BigDecimal proratedProductRefund(Order order, BigDecimal selected) {
        BigDecimal products = order.getTotalAmount().subtract(order.getShippingFee()).max(BigDecimal.ZERO).min(order.getSubTotal());
        return order.getSubTotal().signum() == 0
                ? BigDecimal.ZERO
                : selected.multiply(products).divide(order.getSubTotal(), 0, RoundingMode.DOWN);
    }

    @Transactional
    public View decide(Long id, Decision command) {
        var order = orderForReturn(id);
        var row = lockedReturn(id);
        if (command == null || command.reason() == null || command.reason().isBlank() || command.reason().length() > 1000) {
            throw new BusinessException("Decision reason required");
        }
        String target = command.approved() ? "APPROVED" : "REJECTED";
        String requestedMethod = command.returnMethod() != null && !command.returnMethod().isBlank()
                ? normalizeReturnMethod(command.returnMethod())
                : null;
        if (row.getStatus().equals(target)
                && command.reason().equals(row.getDecisionReason())
                && (requestedMethod == null || requestedMethod.equals(row.getReturnMethod()))) {
            return view(row, order);
        }
        if (!row.getStatus().equals("REQUESTED")) {
            throw new BusinessException("Return request already decided");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException("Order no longer supports this return");
        }
        if (command.approved() && requestedMethod != null) {
            row.setReturnMethod(requestedMethod);
        }
        row.setStatus(target);
        row.setDecisionReason(command.reason());
        if (command.approved()) {
            String message = RETURN_METHOD_SHIPPER_PICKUP.equals(row.getReturnMethod())
                    ? "Yêu cầu đổi trả cho đơn #" + order.getOrderNumber() + " đã được chấp thuận. BeautyShop sẽ điều phối tài xế đến thu hồi hàng hoàn."
                    : "Yêu cầu đổi trả cho đơn #" + order.getOrderNumber() + " đã được chấp thuận. Vui lòng gửi hàng hoàn về BeautyShop để kiểm định.";
            notifyCustomer(order, row, "APPROVED",
                    "Yêu cầu đổi trả #" + row.getId() + " đã được duyệt",
                    message);
        } else {
            notifyCustomer(order, row, "REJECTED",
                    "Yêu cầu đổi trả #" + row.getId() + " bị từ chối",
                    "Yêu cầu đổi trả cho đơn #" + order.getOrderNumber() + " chưa được chấp thuận. Lý do: " + command.reason().trim());
        }
        return view(row, order);
    }

    @Transactional
    public View assignPickupShipper(Long id, AssignPickup command) {
        var order = orderForReturn(id);
        var row = lockedReturn(id);
        if (command == null || command.shipperId() == null) {
            throw new BusinessException("Vui lòng chọn tài xế thu hồi hàng hoàn");
        }
        if (order.getStatus() != OrderStatus.DELIVERED || order.getAppointmentId() != null || order.getServicePackageId() != null) {
            throw new BusinessException("Chỉ điều phối thu hồi hàng hoàn cho đơn mỹ phẩm đã giao");
        }
        if (!RETURN_METHOD_SHIPPER_PICKUP.equals(row.getReturnMethod())) {
            throw new BusinessException("Yêu cầu đổi trả này sử dụng hình thức tự gửi hàng (SELF_SHIP), không cần điều phối tài xế");
        }
        if (!"APPROVED".equals(row.getStatus()) && !"PICKUP_ASSIGNED".equals(row.getStatus())) {
            throw new BusinessException("Chỉ có thể phân công tài xế thu hồi khi yêu cầu đang ở trạng thái Đã chấp thuận hoặc Chờ lấy hàng hoàn");
        }
        var shipper = identityFacade.findActiveUserWithRole(command.shipperId(), "ROLE_SHIPPER")
                .orElseThrow(() -> new BusinessException("Chọn tài khoản tài xế đang hoạt động"));

        boolean changedShipper = !command.shipperId().equals(row.getPickupShipperId());
        if ("PICKUP_ASSIGNED".equals(row.getStatus()) && !changedShipper) {
            return view(row, order);
        }
        if ("PICKUP_ASSIGNED".equals(row.getStatus()) && changedShipper && row.getPickupStartedAt() != null) {
            throw new BusinessException("Tài xế hiện tại đang trên đường đến thu hồi hàng hoàn; vui lòng đợi kết quả trước khi đổi tài xế");
        }

        String shipperName = shipper.getFullName() == null || shipper.getFullName().isBlank()
                ? shipper.getUsername()
                : shipper.getFullName();
        row.setStatus("PICKUP_ASSIGNED");
        row.setPickupShipperId(shipper.getId());
        row.setPickupShipperName(shipperName);
        row.setPickupShipperPhone(shipper.getPhone());
        row.setPickupAssignedAt(Instant.now());
        row.setPickupStartedAt(null);
        row.setPickupFailureReason(null);
        returns.flush();

        String cleanNotes = trimToNull(command.notes());
        if (cleanNotes != null && cleanNotes.length() > 500) {
            throw new BusinessException("Ghi chú điều phối tối đa 500 ký tự");
        }
        appendOrderHistory(order,
                "Điều phối tài xế " + shipperName + " thu hồi hàng hoàn cho yêu cầu #" + row.getId()
                        + (cleanNotes != null ? " (" + cleanNotes + ")" : ""),
                shipper.getId(),
                shipperName);

        customerNotifications.publish(
                shipper.getId(),
                "shipper-return-pickup:" + row.getId() + ":" + UUID.randomUUID(),
                "SHIPPER_RETURN_PICKUP",
                "Yêu cầu thu hồi hàng hoàn mới",
                "Bạn được phân công thu hồi hàng hoàn đơn #" + order.getOrderNumber() + " (Yêu cầu #" + row.getId() + ")",
                "/api/v1/shipper/returns/" + row.getId());

        String phoneSuffix = (shipper.getPhone() != null && !shipper.getPhone().isBlank())
                ? " (" + shipper.getPhone() + ")"
                : "";
        notifyCustomer(order, row, "PICKUP_ASSIGNED",
                "Đã điều phối tài xế thu hồi #" + row.getId(),
                "Tài xế " + shipperName + phoneSuffix + " đã được phân công đến nhận hàng hoàn cho đơn #" + order.getOrderNumber() + ".");

        return view(row, order);
    }

    @Transactional
    public View receive(Long id, Confirmation command) {
        var order = orderForReturn(id);
        var row = lockedReturn(id);
        requireReference(command);
        if ((row.getStatus().equals("RECEIVED") || row.getStatus().equals("REFUNDED"))
                && command.reference().equals(row.getReceiptReference())) {
            return view(row, order);
        }
        if (RETURN_METHOD_SHIPPER_PICKUP.equals(row.getReturnMethod())) {
            if (!"PICKED_UP".equals(row.getStatus())) {
                throw new BusinessException("Tài xế cần xác nhận đã thu hồi hàng hoàn từ khách (PICKED_UP) trước khi kho kiểm nhận");
            }
        } else if (!"APPROVED".equals(row.getStatus())) {
            throw new BusinessException("Return must be approved before goods are received");
        }
        for (var line : lines(row)) {
            var item = order.getItems().stream()
                    .filter(i -> i.getId().equals(line.orderItemId()))
                    .findFirst()
                    .orElseThrow();
            inventory.returnPartialStock(order.getOrderNumber(), item.getProductVariantId(), line.quantity());
        }
        row.setStatus("RECEIVED");
        row.setReceiptReference(command.reference());
        row.setReceivedAt(Instant.now());
        row.setReceivedBy(SecurityUtils.getCurrentUserIdOptional().orElse(null));
        returns.flush();
        syncOrderOnReturnReceive(order, row);
        financialEvent(order);
        notifyCustomer(order, row, "RECEIVED",
                "Đã nhận hàng hoàn #" + row.getId(),
                "BeautyShop đã nhận hàng trả về cho đơn #" + order.getOrderNumber() + " và đang xử lý hoàn tiền.");
        return view(row, order);
    }

    @Transactional
    public View refund(Long id, Confirmation command) {
        if (!SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Administrator required for refund confirmation");
        }
        var order = orderForReturn(id);
        var row = lockedReturn(id);
        boolean isVoucher = "VOUCHER".equals(row.getRefundMethod());
        if (isVoucher) {
            if (command == null || command.reason() == null || command.reason().isBlank() || command.reason().length() > 250) {
                throw new BusinessException("Lý do xác nhận cấp voucher hoàn tiền (tối đa 250 ký tự) là bắt buộc");
            }
            if (row.getStatus().equals("REFUNDED")) return view(row, order);
            if (!row.getStatus().equals("RECEIVED")) {
                throw new BusinessException("Receive returned goods before refund confirmation");
            }
            if (order.getUserId() == null) {
                throw new BusinessException("Chỉ tài khoản đã đăng nhập mới hỗ trợ cấp voucher hoàn tiền");
            }
            if (row.getRefundAmount().compareTo(order.getPaidAmount().subtract(order.getRefundedAmount())) > 0) {
                throw new BusinessException("Refund exceeds actual unrefunded receipts");
            }
            String voucherCode = row.getRefundVoucherCode();
            if (voucherCode == null && row.getRefundAmount().signum() > 0) {
                voucherCode = promotions.issueRefundVoucher(order.getUserId(), row.getRefundAmount(), order.getId(), row.getId());
            }
            String ref = (command.reference() != null && !command.reference().isBlank())
                    ? command.reference().trim()
                    : (voucherCode != null ? voucherCode : "ZERO-VOUCHER-" + row.getId());
            if (ref.length() > 100) ref = ref.substring(0, 100);
            recordRefund(order, row.getRefundAmount(), ref, command.reason(), "VOUCHER_RETURN", row.getId());
            row.setRefundVoucherCode(voucherCode);
            row.setRefundReference(ref);
            row.setStatus("REFUNDED");
            returns.flush();
            syncOrderOnReturnRefund(order, row);
            financialEvent(order);
            notifyCustomer(order, row, "REFUNDED",
                    "Đã cấp Voucher hoàn tiền #" + row.getId(),
                    voucherCode != null
                            ? "Mã voucher hoàn tiền của bạn cho đơn #" + order.getOrderNumber() + " là " + voucherCode + " (trị giá " + row.getRefundAmount().toPlainString() + " VND)."
                            : "Yêu cầu hoàn trả #" + row.getId() + " cho đơn #" + order.getOrderNumber() + " đã được hoàn tất.");
            return view(row, order);
        }

        requireReference(command);
        if (row.getStatus().equals("REFUNDED") && command.reference().equals(row.getRefundReference())) {
            return view(row, order);
        }
        if (!row.getStatus().equals("RECEIVED")) {
            throw new BusinessException("Receive returned goods before refund confirmation");
        }
        recordRefund(order, row.getRefundAmount(), command.reference(), command.reason(), "PRODUCT_RETURN", row.getId());
        row.setRefundReference(command.reference());
        row.setStatus("REFUNDED");
        returns.flush();
        syncOrderOnReturnRefund(order, row);
        financialEvent(order);
        notifyCustomer(order, row, "REFUNDED",
                "Đã hoàn tiền cho yêu cầu #" + row.getId(),
                "Khoản hoàn " + row.getRefundAmount().toPlainString() + " VND cho đơn #" + order.getOrderNumber() + " đã được xác nhận (Mã chứng từ: " + command.reference() + ").");
        return view(row, order);
    }

    @Transactional
    public void overpaymentRefund(Long orderId, BigDecimal amount, String reference, String reason) {
        if (!SecurityUtils.isAdmin()) {
            throw new AccessDeniedException("Administrator required");
        }
        requireReference(new Confirmation(reference, reason));
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessException("Positive whole-VND refund amount required");
        }
        var order = lockedOrder(orderId);
        var previous = refunds.findByReference(reference);
        if (previous.isPresent()) {
            if (!previous.get().getOrderId().equals(orderId)
                    || previous.get().getAmount().compareTo(amount) != 0
                    || !"OVERPAYMENT".equals(previous.get().getRefundKind())) {
                throw new BusinessException("Refund reference conflicts");
            }
            return;
        }
        BigDecimal excess = order.getPaidAmount().subtract(order.getTotalAmount())
                .subtract(refunds.sumOverpaymentRefunds(orderId)).max(BigDecimal.ZERO);
        if (amount.compareTo(excess) > 0) {
            throw new BusinessException("Amount exceeds the remaining overpayment");
        }
        recordRefund(order, amount, reference, reason, "OVERPAYMENT", null);
        financialEvent(order);
    }

    @Transactional(readOnly = true)
    public void ensureWholeReturnAllowed(Long orderId) {
        if (returns.findByOrderIdOrderByIdDesc(orderId).stream().anyMatch(r -> !r.getStatus().equals("REJECTED"))) {
            throw new BusinessException("Process existing item return requests instead of returning the whole order");
        }
    }

    @Transactional
    public void replaceAllocation(Long orderId) {
        var order = lockedOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new BusinessException("Replace batches before shipment only");
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new BusinessException("Only physical product orders support batch replacement");
        }
        Map<Long, Integer> qtyByVariant = new LinkedHashMap<>();
        order.getItems().forEach(i -> qtyByVariant.merge(i.getProductVariantId(), i.getQuantity(), Integer::sum));
        qtyByVariant.forEach((vId, qty) -> inventory.replaceAllocation(order.getOrderNumber(), vId, qty));
        appendOrderHistory(order, "Allocated stock replaced using currently sellable FEFO batches", null, null);
    }

    private void recordRefund(Order order, BigDecimal amount, String reference, String reason, String kind, Long returnId) {
        requireReference(new Confirmation(reference, reason));
        if (amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(order.getPaidAmount().subtract(order.getRefundedAmount())) > 0) {
            throw new BusinessException("Refund exceeds actual unrefunded receipts");
        }
        if (amount.signum() > 0) {
            if (refunds.findByReference(reference).isPresent()) {
                throw new BusinessException("Refund reference already used");
            }
            var confirmation = new RefundConfirmation();
            confirmation.setOrderId(order.getId());
            confirmation.setReference(reference);
            confirmation.setAmount(amount);
            confirmation.setApprovalReason(reason);
            confirmation.setConfirmedByUserId(SecurityUtils.getCurrentUserId());
            confirmation.setRefundKind(kind);
            confirmation.setProductReturnId(returnId);
            refunds.saveAndFlush(confirmation);
            order.setRefundedAmount(order.getRefundedAmount().add(amount));
            order.setRefundReference(reference);
            if (order.getRefundedAmount().compareTo(order.getPaidAmount()) == 0) {
                order.setPaymentStatus(PaymentStatus.REFUNDED);
            } else if (!"OVERPAYMENT".equals(kind)) {
                order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }
        }
    }

    private void syncOrderOnReturnReceive(Order order, ProductReturn row) {
        boolean hasUnrefundedReceipt = row.getRefundAmount() != null
                && row.getRefundAmount().signum() > 0
                && order.getPaidAmount() != null
                && order.getRefundedAmount() != null
                && order.getPaidAmount().compareTo(order.getRefundedAmount()) > 0;
        if (hasUnrefundedReceipt && order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        }
        appendOrderHistory(order,
                "Đã nhận hàng hoàn cho yêu cầu #" + row.getId()
                        + (hasUnrefundedReceipt ? " — chờ xác nhận hoàn tiền" : ""),
                row.getPickupShipperId(),
                row.getPickupShipperName());
    }

    private void syncOrderOnReturnRefund(Order order, ProductReturn row) {
        int totalPurchasedQty = order.getItems() == null ? 0
                : order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        int totalRefundedQty = returns.findByOrderIdOrderByIdDesc(order.getId()).stream()
                .filter(r -> "REFUNDED".equals(r.getStatus()))
                .flatMap(r -> lines(r).stream())
                .mapToInt(Line::quantity)
                .sum();
        if (totalPurchasedQty > 0 && totalRefundedQty >= totalPurchasedQty
                && order.getStatus() == OrderStatus.DELIVERED) {
            order.setStatus(OrderStatus.RETURNED);
            appendOrderHistory(order,
                    "Đã hoàn đủ toàn bộ sản phẩm trong đơn và xác nhận hoàn tiền (Yêu cầu #" + row.getId() + ")",
                    null,
                    null);
            return;
        }
        appendOrderHistory(order, "Đã xác nhận hoàn tiền cho yêu cầu đổi trả #" + row.getId(), null, null);
    }

    private void appendOrderHistory(Order order, String notes, Long shipperId, String shipperName) {
        if (order.getStatusHistories() == null) {
            order.setStatusHistories(new ArrayList<>());
        }
        order.getStatusHistories().add(OrderStatusHistory.builder()
                .order(order)
                .status(order.getStatus())
                .shipperId(shipperId)
                .shipperName(shipperName)
                .notes(notes)
                .build());
    }

    private void financialEvent(Order order) {
        BigDecimal returned = returns.findByOrderIdOrderByIdDesc(order.getId()).stream()
                .filter(r -> r.getStatus().equals("RECEIVED") || r.getStatus().equals("REFUNDED"))
                .map(ProductReturn::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal eligible = order.getTotalAmount().subtract(returned).max(BigDecimal.ZERO)
                .min(order.getPaidAmount().subtract(order.getRefundedAmount()).max(BigDecimal.ZERO));
        events.publishEvent(new OrderEvents.OrderFinancialAdjustmentEvent(order.getId(), order.getUserId(), eligible));
    }

    private void notifyCustomer(Order order, ProductReturn row, String status, String title, String body) {
        if (order.getUserId() == null) return;
        customerNotifications.publish(
                order.getUserId(),
                "return:" + row.getId() + ":" + status,
                "ORDER",
                title,
                body,
                "profile?tab=orders&order=" + order.getId());
    }

    public static String normalizeReturnMethod(String rawMethod) {
        String method = (rawMethod == null || rawMethod.isBlank())
                ? RETURN_METHOD_SELF_SHIP
                : rawMethod.trim().toUpperCase(Locale.ROOT);
        if (!RETURN_METHOD_SELF_SHIP.equals(method) && !RETURN_METHOD_SHIPPER_PICKUP.equals(method)) {
            throw new BusinessException("Phương thức hoàn hàng phải là SELF_SHIP hoặc SHIPPER_PICKUP");
        }
        return method;
    }

    private static String normalizeRefundMethod(String rawMethod, Order order) {
        String method = (rawMethod == null || rawMethod.isBlank()) ? "BANK_TRANSFER" : rawMethod.trim().toUpperCase(Locale.ROOT);
        if (!"BANK_TRANSFER".equals(method) && !"VOUCHER".equals(method)) {
            throw new BusinessException("Phương thức hoàn tiền phải là BANK_TRANSFER hoặc VOUCHER");
        }
        if ("VOUCHER".equals(method) && order.getUserId() == null) {
            throw new BusinessException("Đơn khách vãng lai chưa đăng nhập chỉ hỗ trợ hoàn tiền qua chuyển khoản ngân hàng");
        }
        return method;
    }

    private static void validateBankDetails(String method, String bankBin, String bankName, String accountNumber, String accountName) {
        if (!"BANK_TRANSFER".equals(method)) {
            if (bankBin != null || bankName != null || accountNumber != null || accountName != null) {
                throw new BusinessException("Không gửi thông tin ngân hàng khi chọn hoàn tiền bằng Voucher");
            }
            return;
        }
        if (bankBin == null && bankName == null && accountNumber == null && accountName == null) {
            return;
        }
        if (bankName == null || accountNumber == null || accountName == null) {
            throw new BusinessException("Vui lòng nhập đầy đủ tên ngân hàng, số tài khoản và tên chủ tài khoản nhận hoàn tiền");
        }
        if (bankName.length() > 100 || accountName.length() > 120 || !accountNumber.matches("[0-9A-Za-z-]{4,40}")) {
            throw new BusinessException("Thông tin tài khoản ngân hàng nhận hoàn tiền không hợp lệ");
        }
        if (bankBin != null && !bankBin.matches("[0-9]{4,12}")) {
            throw new BusinessException("Mã BIN ngân hàng không hợp lệ");
        }
    }

    private static boolean isValidEvidenceUrl(String url) {
        if (url == null || url.length() > 1000) return false;
        return url.matches("https?://[^\\s]+") || url.matches("/uploads/[A-Za-z0-9._-]+");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String formatAddress(Order order) {
        if (order == null) return null;
        return Stream.of(order.getShippingAddress(), order.getWard(), order.getDistrict(), order.getCity())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(", "));
    }

    private void requireReference(Confirmation c) {
        if (c == null || c.reference() == null || c.reference().isBlank() || c.reference().length() > 100
                || c.reason() == null || c.reason().isBlank() || c.reason().length() > 250) {
            throw new BusinessException("Stable reference and confirmation reason (max 250) required");
        }
    }

    private Order lockedOrder(Long id) {
        return orders.findByIdForUpdate(id)
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private Order orderForReturn(Long id) {
        return lockedOrder(returns.findOrderId(id).orElseThrow(() -> new ResourceNotFoundException("Return not found")));
    }

    private ProductReturn lockedReturn(Long id) {
        return returns.lock(id).orElseThrow(() -> new ResourceNotFoundException("Return not found"));
    }

    private void owned(Order order, String session) {
        if (SecurityUtils.isAdmin()) return;
        if (order.getUserId() != null && SecurityUtils.getCurrentUserIdOptional().filter(order.getUserId()::equals).isPresent()) {
            return;
        }
        if (order.getUserId() == null && session != null && order.getGuestSessionId() != null
                && MessageDigest.isEqual(session.getBytes(StandardCharsets.UTF_8), order.getGuestSessionId().getBytes(StandardCharsets.UTF_8))) {
            return;
        }
        throw new AccessDeniedException("Order ownership required");
    }

    private List<Line> lines(ProductReturn row) {
        try {
            return mapper.readValue(row.getLinesJson(), new TypeReference<List<Line>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String json(Object data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private View view(ProductReturn row, Order order) {
        try {
            return new View(
                    row.getId(),
                    row.getOrderId(),
                    row.getStatus(),
                    row.getReason(),
                    row.getDecisionReason(),
                    row.getRefundAmount(),
                    lines(row),
                    mapper.readValue(row.getEvidenceJson(), new TypeReference<List<String>>() {}),
                    row.getCreatedAt(),
                    row.getReplacementOrderId(),
                    row.getReplacementReason(),
                    row.getReplacementLinkedAt(),
                    row.getRefundMethod(),
                    row.getRefundBankBin(),
                    row.getRefundBankName(),
                    row.getRefundAccountNumber(),
                    row.getRefundAccountName(),
                    row.getRefundVoucherCode(),
                    row.getReturnMethod(),
                    row.getPickupShipperId(),
                    row.getPickupShipperName(),
                    row.getPickupShipperPhone(),
                    row.getPickupAssignedAt(),
                    row.getPickupStartedAt(),
                    row.getPickedUpAt(),
                    row.getPickupProofUrl(),
                    row.getPickupNotes(),
                    row.getPickupFailureReason(),
                    row.getPickupAttemptCount(),
                    row.getHandoverRequestedAt(),
                    row.getHandoverNotes(),
                    row.getReceivedAt(),
                    order == null ? null : order.getOrderNumber(),
                    order == null ? null : order.getCustomerName(),
                    order == null ? null : order.getCustomerPhone(),
                    formatAddress(order));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
