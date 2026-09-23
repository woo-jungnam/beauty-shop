package com.core.beautyshop.modules.catalog.application.dto.request;

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

    @JsonIgnore
    private boolean parentIdSpecified;

    @JsonSetter("parentId")
    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdSpecified = true;
    }

    private Integer displayOrder;

    private Boolean isActive;
}
