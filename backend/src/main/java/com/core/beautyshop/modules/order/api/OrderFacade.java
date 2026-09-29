package com.core.beautyshop.modules.order.api;

import java.math.BigDecimal;
import com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand;
import com.core.beautyshop.modules.order.api.dto.SpaPackageOrderResult;

public interface OrderFacade {
    java.util.Optional<String> findPaymentState(String orderNumber);
    boolean markOrderAsPaid(String orderNumber, BigDecimal transferAmount, String referenceCode);
    SpaPackageOrderResult createSpaPackageOrder(CreateSpaPackageOrderCommand command);
    boolean existsById(Long orderId);
    boolean isPaidOrderForUser(Long orderId, Long userId);
    boolean isDeliveredProductPurchase(Long orderId, Long userId, Long productId);
}
