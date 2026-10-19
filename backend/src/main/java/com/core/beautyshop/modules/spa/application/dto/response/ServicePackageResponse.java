package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.ServicePackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Gói catalog hiện tại; quyền đã mua được lưu riêng bằng purchase snapshot/ticket")
public class ServicePackageResponse {
    private Long id;
    private String name;
    private String description;
    @Schema(description = "Giá gói VND, làm tròn khi tạo order BANK", minimum = "1", example = "900000")
    private BigDecimal price;
    @Schema(description = "Số ngày tính từ paidAt khi cấp vé; null là không hết hạn", minimum = "1", nullable = true, example = "30")
    private Integer validityDays;
    private String thumbnailUrl;
    private Boolean isActive;
    private List<Item> items;
    private String purchaseToken;
    public static String token(ServicePackage entity) {
        String value = entity.getId()+"|"+entity.getName()+"|"+entity.getPrice()+"|"+entity.getValidityDays()+"|"+
                entity.getItems().stream().sorted(java.util.Comparator.comparing(i -> i.getService().getId()))
                .map(i -> i.getService().getId()+":"+i.getService().getName()+":"+i.getQuantity()+":"+i.getService().getBasePrice()).toList();
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "SpaPackageServiceItem", description = "Quota theo một dịch vụ trong catalog gói hiện tại")
    public static class Item {
        private Long serviceId;
        private String serviceName;
        @Schema(description = "Số lượt của dịch vụ này", minimum = "1", example = "3")
        private Integer quantity;
    }

    public static ServicePackageResponse fromEntity(ServicePackage entity) {
        return ServicePackageResponse.builder()
                .id(entity.getId())
                .purchaseToken(token(entity))
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .validityDays(entity.getValidityDays())
                .thumbnailUrl(entity.getThumbnailUrl())
                .isActive(entity.getIsActive())
                .items(entity.getItems().stream().map(item -> Item.builder()
                        .serviceId(item.getService().getId())
                        .serviceName(item.getService().getName())
                        .quantity(item.getQuantity())
                        .build()).toList())
                .build();
    }
}
