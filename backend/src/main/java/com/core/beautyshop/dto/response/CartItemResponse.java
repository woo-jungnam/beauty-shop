package com.core.beautyshop.dto.response;

import com.core.beautyshop.entities.cart.CartItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartItemResponse {
    private Long id;
    private Long variantId;
    private String sku;
    private String variantName;
    private Integer quantity;
    private BigDecimal price;
    private String imageUrl;

    public static CartItemResponse fromEntity(CartItem entity) {
        if (entity == null) return null;
        return CartItemResponse.builder()
                .id(entity.getId())
                .variantId(entity.getProductVariant().getId())
                .sku(entity.getProductVariant().getSku())
                .variantName(entity.getProductVariant().getVariantName())
                .quantity(entity.getQuantity())
                .price(entity.getProductVariant().getDiscountPrice() != null 
                        ? entity.getProductVariant().getDiscountPrice() 
                        : entity.getProductVariant().getPrice())
                .build();
    }
}
