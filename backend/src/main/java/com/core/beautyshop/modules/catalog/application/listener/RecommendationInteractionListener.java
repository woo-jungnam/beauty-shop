package com.core.beautyshop.modules.catalog.application.listener;

import com.core.beautyshop.modules.catalog.api.event.ProductInteractionEvent;
import com.core.beautyshop.modules.catalog.application.service.ProductInteractionRecorder;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationInteractionListener {
    private final ProductInteractionRecorder recorder;
    private final ProductVariantRepository variants;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCart(ProductInteractionEvent event) { safeRecord(event); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaid(OrderEvents.OrderPaidEvent event) {
        purchase(event.getOrderId(), event.getUserId(), event.getSessionId(), event.getItems());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDelivered(OrderEvents.OrderDeliveredEvent event) {
        purchase(event.getOrderId(), event.getUserId(), event.getSessionId(), event.getItems());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReturned(OrderEvents.OrderReturnedEvent event) { remove(event.getOrderId()); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(OrderEvents.OrderCancelledEvent event) { remove(event.getOrderId()); }

    private void remove(Long orderId) {
        try { recorder.removePurchase(orderId); }
        catch (RuntimeException error) { log.warn("Chưa điều chỉnh được tín hiệu mua của đơn {}", orderId); }
    }

    private void purchase(Long orderId, Long userId, String sessionId, java.util.List<OrderEvents.OrderItemSummary> items) {
        if (items == null) return;
        java.util.Set<Long> products = new java.util.HashSet<>();
        for (var item : items) {
            try {
                variants.findById(item.getVariantId()).ifPresent(variant -> {
                    Long productId = variant.getProduct().getId();
                    if (products.add(productId)) safeRecord(new ProductInteractionEvent(userId, sessionId, productId,
                            "PURCHASE", "order:" + orderId + ":product:" + productId));
                });
            } catch (RuntimeException error) { log.warn("Chưa ghi được tín hiệu mua cho đơn {}", orderId); }
        }
    }

    private void safeRecord(ProductInteractionEvent event) {
        try { recorder.record(event); }
        catch (RuntimeException error) { log.warn("Chưa ghi được tương tác {} cho sản phẩm {}", event.actionType(), event.productId()); }
    }
}
