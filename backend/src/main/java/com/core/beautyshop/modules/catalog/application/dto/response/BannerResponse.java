package com.core.beautyshop.modules.catalog.application.dto.response;

import com.core.beautyshop.modules.catalog.domain.Banner;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết Banner")
public class BannerResponse {

    private Long id;
    private String title;
    private String badge;
    private String description;
    private String imageUrl;
    private String targetUrl;
    private String ctaText;
    private String position;
    private Integer sortOrder;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static BannerResponse fromEntity(Banner entity) {
        if (entity == null) return null;
        return BannerResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .badge(entity.getBadge())
                .description(entity.getDescription())
                .imageUrl(entity.getImageUrl())
                .targetUrl(entity.getTargetUrl())
                .ctaText(entity.getCtaText())
                .position(entity.getPosition())
                .sortOrder(entity.getSortOrder())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
