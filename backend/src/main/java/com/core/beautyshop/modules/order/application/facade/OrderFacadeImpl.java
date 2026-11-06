package com.core.beautyshop.modules.order.application.facade;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;
import com.core.beautyshop.modules.order.api.dto.SpaRefundBalance;
import com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.OrderStatusHistory;
import com.core.beautyshop.modules.order.domain.RefundConfirmationRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.order.application.service.OrderExpirationService;
import com.core.beautyshop.modules.order.application.service.OrderRefundService;
import com.core.beautyshop.modules.order.application.service.SpaVisitOrderService;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class OrderFacadeImpl implements OrderFacade {
    @Override
    public SpaRefundBalance lockSpaRefundBalance(Long orderId) {
        return orderRefunds.lockSpaBalance(orderId);
    }
    @Override
    public void confirmSpaServiceRefund(Long orderId, BigDecimal amount, String reference, String reason) {
        orderRefunds.confirmSpaServiceRefund(orderId, amount, reference, reason);
    }
    @Override
    public SpaVisitInvoiceResult applySpaVisitAdvance(Long appointmentId, BigDecimal amount, String reference) {
        return visits.applyAdvance(appointmentId, amount, reference);
    }

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentFacade paymentFacade;
    private final RefundConfirmationRepository refunds;
    private final OrderExpirationService expirationService;
    private final SpaVisitOrderService visits;
    private final OrderRefundService orderRefunds;
    private final IdentityFacade identityFacade;

    @Autowired
    public OrderFacadeImpl(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher,
                           PaymentFacade paymentFacade, RefundConfirmationRepository refunds,
                           OrderExpirationService expirationService,
                           SpaVisitOrderService visits,
                           OrderRefundService orderRefunds,
                           com.core.beautyshop.modules.identity.api.IdentityFacade identityFacade) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.paymentFacade = paymentFacade;
        this.refunds = refunds;
        this.expirationService = expirationService;
        this.visits = visits;
        this.orderRefunds = orderRefunds;
        this.identityFacade = identityFacade;
    }

    public OrderFacadeImpl(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher,
                           PaymentFacade paymentFacade, com.core.beautyshop.modules.order.domain.RefundConfirmationRepository refunds,
                           com.core.beautyshop.modules.order.application.service.OrderExpirationService expirationService,
                           com.core.beautyshop.modules.order.application.service.SpaVisitOrderService visits,
                           com.core.beautyshop.modules.order.application.service.OrderRefundService orderRefunds) {
        this(orderRepository, eventPublisher, paymentFacade, refunds, expirationService, visits, orderRefunds, null);
    }

    @Override
    public void confirmTreatmentRefund(Long orderId, BigDecimal amount, String reference, String reason) {
        orderRefunds.confirmTreatmentRefund(orderId, amount, reference, reason);
    }
    @Override
    public void confirmTreatmentRefund(List<Long> orderIds, BigDecimal amount, String reference, String reason) {
        orderRefunds.confirmTreatmentRefund(orderIds, amount, reference, reason);
    }
    @org.springframework.beans.factory.annotation.Value("${app.order.payment-timeout-minutes:30}")
    private long paymentTimeoutMinutes = 30;

    @Override
    public com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult createSpaVisitOrder(
            com.core.beautyshop.modules.order.api.dto.CreateSpaVisitOrderCommand command) { return visits.create(command); }
    @Override
    public com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult getSpaVisitInvoiceForUser(Long appointmentId, Long userId) {
        return visits.forUser(appointmentId, userId);
    }
    @Override
    public com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult getSpaVisitInvoiceForStaff(Long appointmentId) {
        return visits.forStaff(appointmentId);
    }
    @Override
    public com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult collectSpaVisitCash(Long appointmentId, BigDecimal amount, String receiptKey) {
        return visits.collectCash(appointmentId, amount, receiptKey);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getConfirmedRefundAmount(Instant from, Instant to) {
        return refunds.sumConfirmed(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findPaidAtForUser(Long orderId, Long userId) {
        if (orderId == null || userId == null) return Optional.empty();
        return orderRepository.findById(orderId).filter(order -> !Boolean.TRUE.equals(order.getIsDeleted()))
                .filter(order -> userId.equals(order.getUserId()) && order.getPaymentStatus() == PaymentStatus.PAID)
                .map(Order::getPaidAt);
    }

    @Override
    @Transactional
    public SpaPackageOrderResult createSpaPackageOrder(CreateSpaPackageOrderCommand command) {
        if (command == null || command.getUserId() == null || command.getServicePackageId() == null
                || command.getAmount() == null || command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Thông tin đơn mua gói Spa không hợp lệ");
        }
        BigDecimal amount = command.getAmount().setScale(0, RoundingMode.HALF_UP);
        if (amount.signum() == 0) throw new BusinessException("Giá gói dịch vụ Spa sau làm tròn phải từ 1 VNĐ trở lên");

        String checkoutKey = spaCheckoutKey(command);
        String checkoutHash = spaCheckoutHash(command);
        if (checkoutKey != null) {
            Optional<Order> previous = orderRepository.findByCheckoutKey(checkoutKey);
            if (previous.isPresent()) {
                if (!checkoutHash.equals(previous.get().getCheckoutHash())) {
                    throw new BusinessException("Khóa Idempotency-Key đã được sử dụng cho giao dịch mua gói Spa khác");
                }
                return toSpaPackageOrderResult(previous.get());
            }
        }

        BigDecimal subTotal = amount;
        BigDecimal membershipDiscount = BigDecimal.ZERO;
        if (command.getUserId() != null && identityFacade != null) {
            var userSummary = identityFacade.getUserSummaryById(command.getUserId());
            Integer discountPercentage = userSummary != null ? userSummary.getMembershipDiscountPercentage() : null;
            if (discountPercentage != null && discountPercentage > 0) {
                BigDecimal discountPercent = BigDecimal.valueOf(discountPercentage).divide(BigDecimal.valueOf(100));
                membershipDiscount = subTotal.multiply(discountPercent).setScale(0, RoundingMode.HALF_UP);
            }
        }
        BigDecimal discountAmount = membershipDiscount.min(subTotal);
        BigDecimal total = subTotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .checkoutKey(checkoutKey)
                .checkoutHash(checkoutHash)
                .userId(command.getUserId())
                .servicePackageId(command.getServicePackageId())
                .spaBenefitType(command.getBenefitType())
                .customerName(defaultText(command.getCustomerName(), "Khách hàng Spa"))
                .customerPhone(defaultText(command.getCustomerPhone(), "N/A"))
                .shippingAddress("SPA_SERVICE")
                .paymentMethod(PaymentMethod.BANK)
                .paymentStatus(total.signum() == 0 ? PaymentStatus.PAID : PaymentStatus.PENDING)
                .paidAt(total.signum() == 0 ? Instant.now().truncatedTo(ChronoUnit.MICROS) : null)
                .paymentDeadline(total.signum() == 0 ? null : Instant.now().plusSeconds(paymentTimeoutMinutes * 60))
                .status(total.signum() == 0 ? OrderStatus.PROCESSING : OrderStatus.PENDING)
                .subTotal(subTotal)
                .shippingFee(BigDecimal.ZERO)
                .discountAmount(discountAmount)
                .totalAmount(total)
                .paidAmount(BigDecimal.ZERO)
                .notes(command.getNotes())
                .items(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();

        order.getStatusHistories().add(OrderStatusHistory.builder()
                .order(order)
                .status(order.getStatus())
                .notes("Tạo đơn thanh toán gói Spa: " + command.getPackageName())
                .build());

        Order saved = orderRepository.save(order);
        PaymentInstruction instruction = null;
        if (saved.getTotalAmount().signum() > 0) {
            instruction = paymentFacade.processPayment(
                    PaymentMethod.BANK,
                    PaymentOrderDto.builder()
                            .orderNumber(saved.getOrderNumber())
                            .totalAmount(saved.getTotalAmount())
                            .customerName(saved.getCustomerName())
                            .build());
        }

        return SpaPackageOrderResult.builder()
                .orderId(saved.getId())
                .orderNumber(saved.getOrderNumber())
                .servicePackageId(saved.getServicePackageId()).spaBenefitType(saved.getSpaBenefitType())
                .totalAmount(saved.getTotalAmount())
                .status(saved.getStatus())
                .paymentMethod(saved.getPaymentMethod())
                .paymentStatus(saved.getPaymentStatus())
                .paymentDeadline(saved.getPaymentDeadline())
                .createdAt(saved.getCreatedAt())
                .paymentInstruction(instruction)
                .build();
    }

    @Override
    @Transactional
    public boolean markOrderAsPaid(String orderNumber, BigDecimal transferAmount, String referenceCode) {
        Optional<Order> orderOptional = orderRepository.findByOrderNumberForUpdate(orderNumber);
        if (orderOptional.isEmpty()) {
            log.error("Order not found: {}", orderNumber);
            return false;
        }

        if (transferAmount == null || transferAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Ignoring non-positive payment amount for order {}", orderNumber);
            return false;
        }

        Order order = orderOptional.get();
        boolean expiredNow = false;
        if (order.getAppointmentId() == null && (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED) && order.getPaymentDeadline() != null
                && !order.getPaymentDeadline().isAfter(Instant.now())) {
            expiredNow = expirationService.expire(order.getId());
            if (expiredNow) {
                order.setStatus(OrderStatus.CANCELLED);
            }
        }
        BigDecimal accumulatedAmount = Optional.ofNullable(order.getPaidAmount())
                .orElse(BigDecimal.ZERO)
                .add(transferAmount);
        order.setPaidAmount(accumulatedAmount);

        boolean refundedVisit = order.getAppointmentId() != null && (order.getPaymentStatus() == PaymentStatus.REFUNDED
                || order.getPaymentStatus() == PaymentStatus.REFUND_PENDING || order.getRefundedAmount().signum() > 0);
        if (expiredNow || order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.RETURNED || order.getStatus() == OrderStatus.RETURN_TO_SENDER || refundedVisit) {
            order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
            order.getStatusHistories().add(OrderStatusHistory.builder()
                    .order(order)
                    .status(order.getStatus())
                    .notes("Payment received for a " + order.getStatus() + " order. Reference: "
                            + referenceCode + ". A refund is required.")
                    .build());
            orderRepository.save(order);
            log.warn("Payment received for {} order {}. Refund required.", order.getStatus(), orderNumber);
            return true;
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            orderRepository.save(order);
            log.info("Order {} was already paid; recorded the additional transfer", orderNumber);
            return true;
        }

        if (accumulatedAmount.compareTo(order.getTotalAmount()) < 0) {
            orderRepository.save(order);
            log.warn("Partial payment for order {}: required={}, accumulated={}",
                    orderNumber, order.getTotalAmount(), accumulatedAmount);
            return false;
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        if (order.getPaidAt() == null) order.setPaidAt(Instant.now().truncatedTo(ChronoUnit.MICROS));

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PROCESSING);

            OrderStatusHistory history = OrderStatusHistory.builder()
                    .order(order)
                    .status(OrderStatus.PROCESSING)
                    .notes("Bank transfer completed. Reference: " + referenceCode)
                    .build();
            order.getStatusHistories().add(history);

            eventPublisher.publishEvent(OrderEvents.OrderStatusChangedEvent.builder()
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .previousStatus(OrderStatus.PENDING)
                    .newStatus(order.getStatus())
                    .build());
        }

        orderRepository.save(order);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            List<OrderEvents.OrderItemSummary> itemSummaries = order.getItems() != null
                    ? order.getItems().stream()
                    .map(item -> OrderEvents.OrderItemSummary.builder()
                            .variantId(item.getProductVariantId())
                            .quantity(item.getQuantity())
                            .build())
                    .toList()
                    : List.of();

            eventPublisher.publishEvent(OrderEvents.OrderPaidEvent.builder()
                    .orderId(order.getId())
                    .sessionId(order.getGuestSessionId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .totalAmount(order.getTotalAmount())
                    .servicePackageId(order.getServicePackageId())
                    .items(itemSummaries)
                    .build());
        }
        return true;
    }
    @Override
    public boolean existsById(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return orderRepository.existsById(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPaidOrderForUser(Long orderId, Long userId) {
        if (orderId == null || userId == null) {
            return false;
        }
        return orderRepository.findById(orderId)
                .filter(order -> userId.equals(order.getUserId()))
                .filter(order -> order.getPaymentStatus() == PaymentStatus.PAID)
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findPaymentState(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber).map(order -> order.getAppointmentId() != null
                && (order.getPaymentStatus() == PaymentStatus.REFUND_PENDING || order.getPaymentStatus() == PaymentStatus.REFUNDED)
                ? "REFUNDED_VISIT" : order.getStatus().name());
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDeliveredProductPurchase(Long orderId, Long userId, Long productId) {
        return orderId != null && userId != null && productId != null
                && orderRepository.existsDeliveredProductPurchase(orderId, userId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findEligibleDeliveredOrderId(Long userId, Long productId) {
        if (userId == null || productId == null) return Optional.empty();
        List<Long> ids = orderRepository.findDeliveredOrderIdsForProductPurchase(userId, productId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    private SpaPackageOrderResult toSpaPackageOrderResult(Order order) {
        PaymentInstruction instruction = paymentFacade.processPayment(
                PaymentMethod.BANK,
                PaymentOrderDto.builder()
                        .orderNumber(order.getOrderNumber())
                        .totalAmount(order.getTotalAmount())
                        .customerName(order.getCustomerName())
                        .build());
        return SpaPackageOrderResult.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .servicePackageId(order.getServicePackageId()).spaBenefitType(order.getSpaBenefitType())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .paymentDeadline(order.getPaymentDeadline())
                .createdAt(order.getCreatedAt())
                .paymentInstruction(instruction)
                .build();
    }

    private String spaCheckoutKey(CreateSpaPackageOrderCommand command) {
        String key = command.getIdempotencyKey();
        if (key == null) return null;
        if (key.isBlank() || key.length() > 128) {
            throw new BusinessException("Khóa Idempotency-Key không hợp lệ");
        }
        return sha256("spa:" + command.getUserId() + ":" + key);
    }

    private String spaCheckoutHash(CreateSpaPackageOrderCommand command) {
        return sha256(String.join("\u0000",
                String.valueOf(command.getUserId()),
                String.valueOf(command.getServicePackageId()),
                command.getAmount().toPlainString(),
                String.valueOf(command.getNotes())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderRecipientDto> findOrderRecipient(Long orderId) {
        if (orderId == null) return Optional.empty();
        return orderRepository.findById(orderId)
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .map(o -> new OrderRecipientDto(
                        o.getUserId(),
                        o.getCustomerEmail(),
                        o.getCustomerName(),
                        String.valueOf(o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getCreatedAt())
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderEmailDetailDto> findOrderEmailDetail(Long orderId) {
        if (orderId == null) return Optional.empty();
        return orderRepository.findById(orderId)
                .filter(o -> !Boolean.TRUE.equals(o.getIsDeleted()))
                .map(o -> {
                    var items = o.getItems() == null ? List.<OrderEmailItemDto>of() :
                            o.getItems().stream().map(i -> new OrderEmailItemDto(
                                    i.getProductName(), i.getVariantName(),
                                      i.getQuantity(), i.getPrice(), i.getDiscount(), i.getSku(), i.getImageUrl()
                            )).toList();
                    return new OrderEmailDetailDto(
                            o.getOrderNumber(), o.getCustomerName(), o.getCustomerPhone(),
                              o.isSpaOrder() ? null : java.util.stream.Stream.of(o.getShippingAddress(), o.getWard(), o.getDistrict(), o.getCity())
                                      .filter(java.util.Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                                      .collect(java.util.stream.Collectors.joining(", ")),
                            o.getPaymentMethod() != null ? o.getPaymentMethod().name() : null,
                            items, o.getSubTotal(), o.getShippingFee(),
                              o.getDiscountAmount(), o.getTotalAmount(), o.getCreatedAt(),
                              o.getStatus() == null ? null : o.getStatus().name(),
                              o.getPaymentStatus() == null ? null : o.getPaymentStatus().name(),
                              o.getPaidAmount(), o.getRefundedAmount(), o.getPaidAt(), o.getServicePackageId(),
                              o.getSpaBenefitType(), o.getAppointmentId(),
                              o.getSpaVisitItems() == null ? List.of() : o.getSpaVisitItems().stream()
                                      .map(i -> new OrderEmailServiceDto(i.getServiceName(), i.getQuantity() == null ? 1 : i.getQuantity(), i.getUnitPrice())).toList(),
                              o.getCarrierName(), o.getTrackingCode(), o.getNotes()
                    );
                });
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
