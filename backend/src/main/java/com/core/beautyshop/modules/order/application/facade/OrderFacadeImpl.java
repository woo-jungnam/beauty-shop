package com.core.beautyshop.modules.order.application.facade;

import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.OrderStatusHistory;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacadeImpl implements OrderFacade {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public boolean markOrderAsPaid(String orderNumber, BigDecimal transferAmount, String referenceCode) {
        Optional<Order> orderOptional = orderRepository.findByOrderNumber(orderNumber);
        if (orderOptional.isEmpty()) {
            log.error("Không tìm thấy đơn hàng với mã: {}", orderNumber);
            return false;
        }

        Order order = orderOptional.get();
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Đơn hàng {} đã ở trạng thái ĐÃ THANH TOÁN", orderNumber);
            return true;
        }

        if (transferAmount.compareTo(order.getTotalAmount()) >= 0) {
            log.info("Đã nhận đủ tiền thanh toán cho đơn hàng {}", orderNumber);
            order.setPaymentStatus(PaymentStatus.PAID);

            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PROCESSING);

                OrderStatusHistory history = OrderStatusHistory.builder()
                        .order(order)
                        .status(OrderStatus.PROCESSING)
                        .notes("Thanh toán thành công qua chuyển khoản ngân hàng. Mã GD: " + referenceCode)
                        .build();
                order.getStatusHistories().add(history);

                eventPublisher.publishEvent(OrderEvents.OrderStatusChangedEvent.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .previousStatus(OrderStatus.PENDING)
                        .newStatus(order.getStatus())
                        .build());
            } else if (order.getStatus() == OrderStatus.CANCELLED) {
                log.warn("NGHIÊM TRỌNG: Nhận được tiền thanh toán {} cho đơn hàng ĐÃ HỦY {}. Cần hoàn tiền! Mã GD: {}",
                        transferAmount, orderNumber, referenceCode);

                OrderStatusHistory history = OrderStatusHistory.builder()
                        .order(order)
                        .status(OrderStatus.CANCELLED)
                        .notes("CẢNH BÁO: Đã nhận tiền thanh toán (" + transferAmount + "đ) cho đơn hàng ĐÃ HỦY. Mã GD: "
                                + referenceCode + ". Kế toán cần thực hiện hoàn tiền cho khách hàng!")
                        .build();
                order.getStatusHistories().add(history);
            }

            orderRepository.save(order);
            return true;
        } else {
            log.warn("Số tiền thanh toán không đủ cho đơn hàng {}. Cần thanh toán {}, nhận được {}",
                    orderNumber, order.getTotalAmount(), transferAmount);
            return false;
        }
    }

    @Override
    public boolean existsById(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return orderRepository.existsById(orderId);
    }
}
