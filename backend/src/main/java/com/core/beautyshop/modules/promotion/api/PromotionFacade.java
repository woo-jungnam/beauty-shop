package com.core.beautyshop.modules.promotion.api;

import java.math.BigDecimal;

public interface PromotionFacade {
    AppliedVoucher validate(String code, Long userId, BigDecimal orderAmount);
    void redeem(AppliedVoucher voucher, Long userId, Long orderId);
    void releaseVoucher(Long orderId);

    record AppliedVoucher(Long id, String code, BigDecimal discountAmount) { }
}
