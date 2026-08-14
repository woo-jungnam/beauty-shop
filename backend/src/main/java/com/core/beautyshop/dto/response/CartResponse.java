package com.core.beautyshop.dto.response;

import com.core.beautyshop.entities.cart.Cart;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class CartResponse {
    private Long id;
    private String sessionId;
    private Long userId;
    private List<CartItemResponse> items;
    private BigDecimal totalPrice;

    public static CartResponse fromEntity(Cart entity) {
        if (entity == null) return null;
        
        List<CartItemResponse> itemResponses = entity.getItems() != null 
                ? entity.getItems().stream().map(CartItemResponse::fromEntity).collect(Collectors.toList())
                : List.of();
                
        BigDecimal total = itemResponses.stream()
                .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .items(itemResponses)
                .totalPrice(total)
                .build();
    }
}
