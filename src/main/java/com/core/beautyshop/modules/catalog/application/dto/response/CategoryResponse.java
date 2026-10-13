package com.core.beautyshop.modules.catalog.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dữ liệu CategoryResponse")
public class CategoryResponse {
    @Schema(description = "ID bản ghi")
    private Long id;
    @Schema(description = "Tên hiển thị")
    private String name;
    @Schema(description = "Slug")
    private String slug;
    @Schema(description = "Mô tả")
    private String description;
    @Schema(description = "Đường dẫn ảnh")
    private String imageUrl;
    @Schema(description = "ID danh mục cha, null khi là gốc")
    private Long parentId;
    @Schema(description = "Tên danh mục cha")
    private String parentName;
    @Schema(description = "Thứ tự hiển thị")
    private Integer displayOrder;
    @Schema(description = "Trạng thái kích hoạt")
    private Boolean isActive;
    @Schema(description = "Danh sách con; chỉ có dữ liệu khi API dựng cây")
    private List<CategoryResponse> children;

    public CategoryResponse(Long id, String name, String slug, String description, String imageUrl,
                            Long parentId, String parentName, Integer displayOrder, Boolean isActive) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.imageUrl = imageUrl;
        this.parentId = parentId;
        this.parentName = parentName;
        this.displayOrder = displayOrder;
        this.isActive = isActive;
    }
}

