package com.core.beautyshop.modules.spa.application.dto.response;

import java.math.BigDecimal;

import com.core.beautyshop.modules.spa.domain.BeautyService;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BeautyServiceResponse {
    private Long id;
    private String name;
    private String slug;
    private String shortDescription;
    private String description;
    private BigDecimal basePrice;
    private Integer durationMinutes;
    private Integer preparationTimeMinutes;
    private String thumbnailUrl;
    private Boolean isActive;
    private Long categoryId;
    private String categoryName;

    public static BeautyServiceResponse fromEntity(BeautyService entity) {
        if (entity == null) return null;
        return BeautyServiceResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .slug(entity.getSlug())
                .shortDescription(entity.getShortDescription())
                .description(entity.getDescription())
                .basePrice(entity.getBasePrice())
                .durationMinutes(entity.getDurationMinutes())
                .preparationTimeMinutes(entity.getPreparationTimeMinutes())
                .thumbnailUrl(entity.getThumbnailUrl())
                .isActive(entity.getIsActive())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .build();
    }
}
