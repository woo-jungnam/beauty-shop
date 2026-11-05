package com.core.beautyshop.modules.promotion.api;

import java.math.BigDecimal;
import java.util.Optional;

public interface FlashSaleFacade {

    record FlashSalePriceResolution(
            Long flashSaleItemId,
            BigDecimal flashPrice,
            boolean isFlashSale,
            Integer remainingQuota
    ) {}

    /**
     * Tra cứu giá Flash Sale hiệu lực cho biến thể ở thời điểm hiện tại (không giữ quota, dùng cho Cart / Product View)
     */
    Optional<FlashSalePriceResolution> resolveActiveFlashSale(Long variantId);

    /**
     * Cố gắng giữ suất bán Flash Sale nguyên tử cho đơn hàng.
     * Trả về giá Flash Sale nếu thành công, ngược lại Optional.empty()
     */
    Optional<BigDecimal> tryAllocateFlashSale(Long variantId, int quantity, Long orderId, Long userId);

    /**
     * Giải phóng suất Flash Sale khi đơn hàng bị hủy
     */
    void releaseAllocations(Long orderId);
}
