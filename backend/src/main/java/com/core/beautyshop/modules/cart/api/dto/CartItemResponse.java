package com.core.beautyshop.modules.cart.api.dto;

import com.core.beautyshop.modules.cart.domain.CartItem;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mặt hàng trong giỏ hàng")
public class CartItemResponse {

    @Schema(description = "ID dòng trong giỏ hàng", example = "1")
    private Long id;

    @Schema(description = "ID biến thể SKU", example = "201")
    private Long variantId;

    @Schema(description = "Mã SKU", example = "LRP-ANTHELIOS-50ML")
    private String sku;

    @Schema(description = "Tên hiển thị biến thể", example = "Chai 50ml")
    private String variantName;

    @Schema(description = "Số lượng trong giỏ", example = "2")
    private Integer quantity;

    @Schema(description = "Đơn giá hiện tại (VND)", example = "425000")
    private BigDecimal price;

    @Schema(description = "URL ảnh minh họa", example = "https://cdn.beautyshop.com/products/lrp-anthelios.png")
    private String imageUrl;

    @Schema(description = "Biến thể còn được kinh doanh hay không")
    private Boolean available;

    public static CartItemResponse of(CartItem entity, ProductVariantSummaryDto variant) {
        if (entity == null) return null;
        BigDecimal price = null;
        String sku = null;
        String variantName = null;

        if (variant != null) {
            sku = variant.getSku();
            variantName = variant.getVariantName();
            price = variant.getDiscountPrice() != null ? variant.getDiscountPrice() : variant.getPrice();
        }

        return CartItemResponse.builder()
                .id(entity.getId())
                .variantId(entity.getProductVariantId())
                .sku(sku)
                .variantName(variantName)
                .quantity(entity.getQuantity())
                .price(price)
                .imageUrl(variant != null ? variant.getProductThumbnailUrl() : null)
                .available(variant != null)
                .build();
    }
}
