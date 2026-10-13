package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.api.event.ProductInteractionEvent;
import com.core.beautyshop.modules.catalog.domain.UserProductInteraction;
import com.core.beautyshop.modules.catalog.domain.UserProductInteractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductInteractionRecorder {
    private final UserProductInteractionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ProductInteractionEvent event) {
        if (event.productId() == null || event.productId() <= 0
                || (event.userId() == null && (event.sessionId() == null || event.sessionId().isBlank()))) return;
        if (event.sourceKey() != null && repository.existsBySourceKey(event.sourceKey())) return;
        if (event.sourceKey() == null && repository.existsByUserIdAndSessionIdAndProductIdAndActionTypeAndCreatedAtAfter(
                event.userId(), event.sessionId(), event.productId(), event.actionType(), java.time.LocalDateTime.now().minusSeconds(30))) return;
        repository.saveAndFlush(UserProductInteraction.builder().userId(event.userId()).sessionId(event.sessionId())
                .productId(event.productId()).actionType(event.actionType()).sourceKey(event.sourceKey()).build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removePurchase(Long orderId) {
        repository.deleteBySourceKeyStartingWith("order:" + orderId + ":product:");
    }
}
