package com.core.beautyshop.modules.cart.application.dto.response;

import com.core.beautyshop.modules.cart.domain.Cart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private Long id;
    private String sessionId;
    private Long userId;
    private List<CartItemResponse> items;
    private BigDecimal totalPrice;

    public static CartResponse of(Cart entity, List<CartItemResponse> itemResponses) {
        if (entity == null) return null;

        List<CartItemResponse> safeItems = itemResponses != null ? itemResponses : List.of();

        BigDecimal total = safeItems.stream()
                .filter(item -> item.getPrice() != null && item.getQuantity() != null)
                .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .userId(entity.getUserId())
                .items(safeItems)
                .totalPrice(total)
                .build();
    }
}
