package com.core.beautyshop.modules.catalog.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private Long parentId;
    private String parentName;
    private Integer displayOrder;
    private Boolean isActive;
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

