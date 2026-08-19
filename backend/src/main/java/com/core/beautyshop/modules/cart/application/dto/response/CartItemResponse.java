package com.core.beautyshop.modules.cart.application.dto.response;

import com.core.beautyshop.modules.cart.domain.CartItem;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long id;
    private Long variantId;
    private String sku;
    private String variantName;
    private Integer quantity;
    private BigDecimal price;
    private String imageUrl;

    public static CartItemResponse of(CartItem entity, ProductVariantSummaryDto variant) {
        if (entity == null) return null;
        BigDecimal price = BigDecimal.ZERO;
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
                .build();
    }
}
