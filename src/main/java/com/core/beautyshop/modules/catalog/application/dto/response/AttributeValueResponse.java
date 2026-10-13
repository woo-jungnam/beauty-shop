package com.core.beautyshop.modules.catalog.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.core.beautyshop.modules.catalog.domain.enums.AttributeDataType;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@Schema(description = "Dữ liệu AttributeValueResponse")
public class AttributeValueResponse {
    @Schema(description = "ID bản ghi")
    private Long id;
    @Schema(description = "ID định nghĩa thuộc tính")
    private Long attributeDefinitionId;
    @Schema(description = "Tên định nghĩa")
    private String attributeDefinitionName;
    @Schema(description = "Giá trị đã chuẩn hóa dạng chuỗi")
    private String value;
    private AttributeDataType dataType;
    private Long productVariantId;
    @Schema(description = "Thời điểm tạo UTC", format = "date-time")
    private Instant createdAt;
    @Schema(description = "Thời điểm cập nhật UTC", format = "date-time")
    private Instant updatedAt;
}
