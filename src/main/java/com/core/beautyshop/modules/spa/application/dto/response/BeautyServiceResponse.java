package com.core.beautyshop.modules.spa.application.dto.response;

import java.math.BigDecimal;

import com.core.beautyshop.modules.spa.domain.BeautyService;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Catalog dịch vụ Spa; giá/thời lượng hiện tại, không viết lại snapshot lịch đã đặt")
public class BeautyServiceResponse {
    private Long id;
    private String name;
    private String slug;
    private String shortDescription;
    private String description;
    @Schema(description = "Giá buổi lẻ VND chưa áp dụng vé", minimum = "0", example = "300000")
    private BigDecimal basePrice;
    @Schema(description = "Phút thực hiện dự kiến, dương", minimum = "1", example = "45")
    private Integer durationMinutes;
    @Schema(description = "Phút chuẩn bị; planned interval = durationMinutes + preparationTimeMinutes", minimum = "0", example = "15")
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
