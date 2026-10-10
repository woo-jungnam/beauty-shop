package com.core.beautyshop.modules.order.application.dto.response;

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
@Schema(description = "Chi tiết từng món hàng trong đơn hàng")
public class OrderItemResponse {

    @Schema(description = "ID dòng sản phẩm trong đơn", example = "501")
    private Long id;

    @Schema(description = "ID biến thể SKU", example = "201")
    private Long variantId;

    @Schema(description = "Mã SKU", example = "LRP-ANTHELIOS-50ML")
    private String sku;

    @Schema(description = "Tên sản phẩm snapshot lúc mua", example = "Kem Chống Nắng La Roche-Posay Anthelios XL")
    private String productName;

    @Schema(description = "Tên biến thể snapshot lúc mua", example = "Chai 50ml")
    private String variantName;

    @Schema(description = "URL ảnh snapshot lúc mua; legacy có thể null")
    private String imageUrl;

    @Schema(description = "Số lượng mua", example = "2")
    private Integer quantity;

    @Schema(description = "Đơn giá tại thời điểm mua (VND)", example = "425000")
    private BigDecimal price;

    @Schema(description = "Số tiền giảm giá trên mỗi sản phẩm (VND)", example = "26000")
    private BigDecimal discount;
}
