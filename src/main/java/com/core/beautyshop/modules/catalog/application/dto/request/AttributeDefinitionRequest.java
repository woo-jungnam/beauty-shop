package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.core.beautyshop.modules.catalog.domain.enums.AttributeDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Định nghĩa thuộc tính; name và dataType bắt buộc cả khi tạo/cập nhật")
public class AttributeDefinitionRequest {
    @NotBlank(message = "Tên thuộc tính không được để trống")
    @Schema(description = "Tên không rỗng; tạo mới sinh code bằng chữ thường và thay khoảng trắng bằng _", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    
    @Schema(description = "Mô tả; khi cập nhật, null hoặc bỏ qua giữ nguyên")
    private String description;
    
    @NotNull(message = "Kiểu dữ liệu không được để trống")
    @Schema(description = "Không được đổi kiểu khi thuộc tính đang có giá trị chưa xóa", requiredMode = Schema.RequiredMode.REQUIRED, example = "STRING")
    private AttributeDataType dataType;
}
