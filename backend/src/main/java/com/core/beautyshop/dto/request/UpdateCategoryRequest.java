package com.core.beautyshop.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryRequest {

    @Size(max = 150)
    private String name;

    @Size(max = 200)
    private String slug;

    @Size(max = 500)
    private String description;

    @Size(max = 500)
    private String imageUrl;

    private Long parentId;

    private Integer displayOrder;

    private Boolean isActive;
}
