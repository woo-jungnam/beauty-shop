package com.core.beautyshop.modules.order.api;

import java.math.BigDecimal;

public interface OrderFacade {
    boolean markOrderAsPaid(String orderNumber, BigDecimal transferAmount, String referenceCode);
    boolean existsById(Long orderId);
}
