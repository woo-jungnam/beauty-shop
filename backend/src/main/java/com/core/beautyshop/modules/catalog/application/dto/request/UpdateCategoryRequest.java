package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cập nhật danh mục; field null giữ nguyên ngoại trừ parentId")
public class UpdateCategoryRequest {

    @Size(max = 150)
    @Schema(description = "Tên danh mục", maxLength = 150)
    private String name;

    @Size(max = 200)
    @Schema(description = "Slug duy nhất", maxLength = 200)
    private String slug;

    @Size(max = 500)
    @Schema(description = "Mô tả", maxLength = 500)
    private String description;

    @Size(max = 500)
    @Schema(description = "Đường dẫn ảnh", maxLength = 500)
    private String imageUrl;

    @Schema(description = "Bỏ qua giữ cha; gửi null đưa về danh mục gốc; không được tạo chu kỳ", nullable = true)
    private Long parentId;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean parentIdSpecified;

    @JsonSetter("parentId")
    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdSpecified = true;
    }

    @Schema(description = "Thứ tự hiển thị", example = "0")
    private Integer displayOrder;

    @Schema(description = "Trạng thái kích hoạt")
    private Boolean isActive;
}
