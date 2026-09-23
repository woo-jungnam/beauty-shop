package com.core.beautyshop.modules.order.application.facade;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.OrderStatusHistory;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.modules.payment.api.PaymentFacade;
import com.core.beautyshop.modules.payment.api.dto.PaymentInstruction;
import com.core.beautyshop.modules.payment.api.dto.PaymentOrderDto;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import com.core.beautyshop.shared.exception.BusinessException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacadeImpl implements OrderFacade {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentFacade paymentFacade;
    private final com.core.beautyshop.modules.order.application.service.OrderExpirationService expirationService;
    @org.springframework.beans.factory.annotation.Value("${app.order.payment-timeout-minutes:30}")
    private long paymentTimeoutMinutes = 30;

    @Override
    @Transactional
    public SpaPackageOrderResult createSpaPackageOrder(CreateSpaPackageOrderCommand command) {
        if (command == null || command.getUserId() == null || command.getServicePackageId() == null
                || command.getAmount() == null || command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Thông tin đơn mua gói Spa không hợp lệ");
        }

        String checkoutKey = spaCheckoutKey(command);
        String checkoutHash = spaCheckoutHash(command);
        if (checkoutKey != null) {
            Optional<Order> previous = orderRepository.findByCheckoutKey(checkoutKey);
            if (previous.isPresent()) {
                if (!checkoutHash.equals(previous.get().getCheckoutHash())) {
                    throw new BusinessException("Idempotency-Key was already used with a different Spa purchase");
                }
                return toSpaPackageOrderResult(previous.get());
            }
        }

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .checkoutKey(checkoutKey)
                .checkoutHash(checkoutHash)
                .userId(command.getUserId())
                .servicePackageId(command.getServicePackageId())
                .customerName(defaultText(command.getCustomerName(), "Khách hàng Spa"))
                .customerPhone(defaultText(command.getCustomerPhone(), "N/A"))
                .shippingAddress("SPA_SERVICE")
                .paymentMethod(PaymentMethod.BANK)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentDeadline(java.time.Instant.now().plusSeconds(paymentTimeoutMinutes * 60))
                .status(OrderStatus.PENDING)
                .subTotal(command.getAmount())
                .shippingFee(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(command.getAmount())
                .paidAmount(BigDecimal.ZERO)
                .notes(command.getNotes())
                .items(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();

        order.getStatusHistories().add(OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.PENDING)
                .notes("Tạo đơn thanh toán gói Spa: " + command.getPackageName())
                .build());

        Order saved = orderRepository.save(order);
        PaymentInstruction instruction = paymentFacade.processPayment(
                PaymentMethod.BANK,
                PaymentOrderDto.builder()
                        .orderNumber(saved.getOrderNumber())
                        .totalAmount(saved.getTotalAmount())
                        .customerName(saved.getCustomerName())
                        .build());

        return SpaPackageOrderResult.builder()
                .orderId(saved.getId())
                .orderNumber(saved.getOrderNumber())
                .servicePackageId(saved.getServicePackageId())
                .totalAmount(saved.getTotalAmount())
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
        if ((order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED) && order.getPaymentDeadline() != null
                && !order.getPaymentDeadline().isAfter(java.time.Instant.now())) {
            expiredNow = expirationService.expire(order.getId());
            if (expiredNow) {
                order.setStatus(OrderStatus.CANCELLED);
            }
        }
        BigDecimal accumulatedAmount = Optional.ofNullable(order.getPaidAmount())
                .orElse(BigDecimal.ZERO)
                .add(transferAmount);
        order.setPaidAmount(accumulatedAmount);

        if (expiredNow || order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.RETURNED) {
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
            eventPublisher.publishEvent(OrderEvents.OrderPaidEvent.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .servicePackageId(order.getServicePackageId())
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
        return orderRepository.findByOrderNumber(orderNumber).map(order -> order.getStatus().name());
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
                .servicePackageId(order.getServicePackageId())
                .totalAmount(order.getTotalAmount())
                .paymentInstruction(instruction)
                .build();
    }

    private String spaCheckoutKey(CreateSpaPackageOrderCommand command) {
        String key = command.getIdempotencyKey();
        if (key == null) return null;
        if (key.isBlank() || key.length() > 128) {
            throw new BusinessException("Invalid Idempotency-Key");
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

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
