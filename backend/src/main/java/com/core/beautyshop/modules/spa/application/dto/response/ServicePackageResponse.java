package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.ServicePackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicePackageResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer validityDays;
    private String thumbnailUrl;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long serviceId;
        private String serviceName;
        private Integer quantity;
    }

    public static ServicePackageResponse fromEntity(ServicePackage entity) {
        return ServicePackageResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .validityDays(entity.getValidityDays())
                .thumbnailUrl(entity.getThumbnailUrl())
                .items(entity.getItems().stream().map(item -> Item.builder()
                        .serviceId(item.getService().getId())
                        .serviceName(item.getService().getName())
                        .quantity(item.getQuantity())
                        .build()).toList())
                .build();
    }
}
