package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.BeautyService;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BeautyServiceResponse {
    private Long id;
    private String name;
    private String slug;
    private String shortDescription;
    private BigDecimal basePrice;
    private Integer durationMinutes;
    private String thumbnailUrl;
    private String categoryName;

    public static BeautyServiceResponse fromEntity(BeautyService entity) {
        if (entity == null) return null;
        return BeautyServiceResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .slug(entity.getSlug())
                .shortDescription(entity.getShortDescription())
                .basePrice(entity.getBasePrice())
                .durationMinutes(entity.getDurationMinutes())
                .thumbnailUrl(entity.getThumbnailUrl())
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .build();
    }
}
