package com.core.beautyshop.service.payment;

import com.core.beautyshop.dto.request.SePayWebhookRequest;
import com.core.beautyshop.entities.order.Order;
import com.core.beautyshop.entities.order.OrderStatusHistory;
import com.core.beautyshop.entities.order.enums.OrderStatus;
import com.core.beautyshop.entities.order.enums.PaymentStatus;
import com.core.beautyshop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final OrderRepository orderRepository;

    // Pattern to match ORD- followed by exactly 8 uppercase alphanumeric characters
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("(ORD-[A-Z0-9]{8})");

    @Override
    @Transactional
    public void processSePayWebhook(SePayWebhookRequest request) {
        log.info("Received SePay webhook: {}", request);

        // 1. Only process IN (nhận tiền)
        if (!"in".equalsIgnoreCase(request.getTransferType())) {
            log.info("Ignored webhook because transferType is not 'in': {}", request.getTransferType());
            return;
        }

        // 2. Parse Order Number from content
        String content = request.getContent();
        if (content == null || content.trim().isEmpty()) {
            log.warn("Ignored webhook because content is empty");
            return;
        }

        Matcher matcher = ORDER_NUMBER_PATTERN.matcher(content);
        if (!matcher.find()) {
            log.warn("Ignored webhook because no matching ORD-XXXXXXXX found in content: {}", content);
            return;
        }

        String orderNumber = matcher.group(1);
        log.info("Found order number: {} in transfer content", orderNumber);

        // 3. Find Order in DB
        Optional<Order> orderOptional = orderRepository.findByOrderNumber(orderNumber);
        if (orderOptional.isEmpty()) {
            log.error("Không tìm thấy đơn hàng with orderNumber: {}", orderNumber);
            return;
        }

        Order order = orderOptional.get();

        // 4. Check if already PAID
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Order {} is already PAID", orderNumber);
            return;
        }

        // 5. Verify Amount
        BigDecimal transferAmount = request.getTransferAmount();
        BigDecimal totalAmount = order.getTotalAmount();
        
        // Handle cases where customer transferred more or less.
        // For simplicity, we just check if transferAmount >= totalAmount.
        if (transferAmount.compareTo(totalAmount) >= 0) {
            log.info("Payment fully received for order {}", orderNumber);
            order.setPaymentStatus(PaymentStatus.PAID);
            
            // Optionally update order status to PROCESSING since payment is received
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PROCESSING);
                
                OrderStatusHistory history = OrderStatusHistory.builder()
                        .order(order)
                        .status(OrderStatus.PROCESSING)
                        .notes("Thanh toán thành công qua chuyển khoản ngân hàng (SePay). Mã GD: " + request.getReferenceCode())
                        .build();
                order.getStatusHistories().add(history);
            }
            
            orderRepository.save(order);
        } else {
            log.warn("Insufficient payment amount for order {}. Expected {}, but got {}", orderNumber, totalAmount, transferAmount);
            // Optionally, mark as PARTIALLY_PAID or leave it pending and notify admin.
            // Here we just log a warning and don't mark as PAID.
        }
    }
}
