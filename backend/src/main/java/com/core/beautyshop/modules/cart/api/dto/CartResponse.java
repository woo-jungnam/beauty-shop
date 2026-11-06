package com.core.beautyshop.modules.cart.api.dto;

import com.core.beautyshop.modules.cart.domain.Cart;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Thông tin giỏ hàng mua sắm")
public class CartResponse {

    @Schema(description = "ID giỏ hàng", example = "10")
    private Long id;

    @Schema(description = "ID phiên khách vãng lai", example = "guest-session-uuid-12345")
    private String sessionId;

    @Schema(description = "ID người dùng sở hữu (nếu đã đăng nhập)", example = "1")
    private Long userId;

    @Schema(description = "Danh sách các mặt hàng có trong giỏ")
    private List<CartItemResponse> items;

    @Schema(description = "Tổng quantity×giá SKU hiện tại (VND), chưa ship/voucher và chưa giữ tồn; dòng không có giá không cộng", example = "850000")
    private BigDecimal totalPrice;

    @Schema(description = "Hạng thành viên hiện tại của người dùng, null nếu khách vãng lai", example = "GOLD")
    private String membershipTier;

    @Schema(description = "Tỷ lệ chiết khấu theo hạng thành viên (%)", example = "5")
    private Integer membershipDiscountPercentage;

    @Schema(description = "Số tiền chiết khấu thành viên ước tính (VND)", example = "42500")
    private BigDecimal estimatedMembershipDiscount;

    @Schema(description = "Ngưỡng miễn phí vận chuyển (VND)", example = "249000")
    private BigDecimal freeShippingThreshold;

    @Schema(description = "Phí vận chuyển mặc định (VND)", example = "25000")
    private BigDecimal defaultShippingFee;

    @Schema(description = "Phí vận chuyển ước tính cho giỏ hàng hiện tại (VND)", example = "0")
    private BigDecimal estimatedShippingFee;

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
