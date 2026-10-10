package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Giá trị thuộc tính gửi dưới dạng chuỗi, chuyển kiểu theo định nghĩa")
public class AttributeValueRequest {
    @Schema(description = "Field tương thích cho internal API; endpoint HTTP dùng ID trong path và ghi đè field này", example = "1")
    private Long attributeDefinitionId;
    
    @NotNull(message = "ID sản phẩm không được để trống")
    @Schema(description = "ID sản phẩm chưa xóa", requiredMode = Schema.RequiredMode.REQUIRED, example = "101")
    private Long productId;
    
    @Schema(description = "ID SKU thuộc đúng sản phẩm; bỏ qua áp dụng cho sản phẩm")
    private Long productVariantId;
    
    @NotBlank(message = "Giá trị không được để trống")
    @Schema(description = "Không rỗng. NUMBER: số hữu hạn; BOOLEAN: true/false; DATE: yyyy-MM-dd; STRING: tối đa 500 ký tự; TEXT_AREA: văn bản", requiredMode = Schema.RequiredMode.REQUIRED, example = "12.5")
    private String value;
}
