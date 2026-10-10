package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Tạo/cập nhật SKU; khi cập nhật sku, variantName và price vẫn bắt buộc")
public class ProductVariantRequest {
    @NotBlank(message = "Mã SKU không được để trống")
    @Schema(description = "SKU duy nhất không rỗng", requiredMode = Schema.RequiredMode.REQUIRED, example = "SERUM-30ML")
    private String sku;

    @NotBlank(message = "Tên biến thể không được để trống")
    @Schema(description = "Tên SKU không rỗng", requiredMode = Schema.RequiredMode.REQUIRED, example = "Chai 30ml")
    private String variantName;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", message = "Giá phải lớn hơn hoặc bằng 0")
    @Schema(description = "Giá gốc VND", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", example = "350000")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Giá giảm phải lớn hơn hoặc bằng 0")
    @Schema(description = "Giá giảm VND từ 0 đến price; null khi cập nhật xóa giá giảm", minimum = "0", example = "300000")
    private BigDecimal discountPrice;

    @Schema(description = "Dung tích; null khi cập nhật xóa giá trị")
    private String volume;
    @Schema(description = "Màu; null khi cập nhật xóa giá trị")
    private String color;
    @Schema(description = "Mã vạch; null khi cập nhật xóa giá trị")
    private String barcode;
    @Schema(description = "true chọn làm mặc định và bỏ mặc định SKU khác; null tạo mới=false/cập nhật giữ nguyên")
    private Boolean isDefault;
    @Schema(description = "null tạo mới=true/cập nhật giữ nguyên")
    private Boolean isActive;

    @AssertTrue(message = "Giá khuyến mãi phải nhỏ hơn hoặc bằng giá gốc")
    @JsonIgnore
    public boolean isDiscountPriceValid() {
        return price == null || discountPrice == null || discountPrice.compareTo(price) <= 0;
    }
}
