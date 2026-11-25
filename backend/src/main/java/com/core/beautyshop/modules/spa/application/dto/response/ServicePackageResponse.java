package com.core.beautyshop.modules.spa.application.dto.response;

import com.core.beautyshop.modules.spa.domain.ServicePackage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

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
    private BigDecimal originalPrice;
    private BigDecimal savingsAmount;
    private Integer totalSessions;
    private Integer totalDurationMinutes;
    private List<Item> items;
    private String purchaseToken;
    public static String token(ServicePackage entity) {
        String value = entity.getId() + "|" + entity.getName() + "|" + entity.getPrice() + "|" + entity.getValidityDays() + "|" +
                entity.getItems().stream().sorted(Comparator.comparing(i -> i.getService().getId()))
                .map(i -> i.getService().getId() + ":" + i.getService().getName() + ":" + i.getQuantity() + ":" + i.getService().getBasePrice()).toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
        private String thumbnailUrl;
        private String shortDescription;
        private String description;
        private BigDecimal basePrice;
        private Integer durationMinutes;
        private Integer preparationTimeMinutes;
        private String categoryName;
        private boolean requiresCertification;
    }

    public static ServicePackageResponse fromEntity(ServicePackage entity) {
        List<Item> itemDtos = entity.getItems().stream().map(item -> {
            var svc = item.getService();
            return Item.builder()
                    .serviceId(svc.getId())
                    .serviceName(svc.getName())
                    .quantity(item.getQuantity())
                    .thumbnailUrl(svc.getThumbnailUrl())
                    .shortDescription(svc.getShortDescription())
                    .description(svc.getDescription())
                    .basePrice(svc.getBasePrice())
                    .durationMinutes(svc.getDurationMinutes())
                    .preparationTimeMinutes(svc.getPreparationTimeMinutes())
                    .categoryName(svc.getCategory() != null ? svc.getCategory().getName() : null)
                    .requiresCertification(Boolean.TRUE.equals(svc.getRequiresCertification()))
                    .build();
        }).toList();
        BigDecimal originalPrice = itemDtos.stream()
                .filter(i -> i.getBasePrice() != null && i.getQuantity() != null)
                .map(i -> i.getBasePrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalSessions = itemDtos.stream()
                .filter(i -> i.getQuantity() != null)
                .mapToInt(Item::getQuantity)
                .sum();
        int totalDurationMinutes = itemDtos.stream()
                .filter(i -> i.getDurationMinutes() != null && i.getQuantity() != null)
                .mapToInt(i -> i.getDurationMinutes() * i.getQuantity())
                .sum();
        BigDecimal savings = entity.getPrice() != null && originalPrice.compareTo(entity.getPrice()) > 0
                ? originalPrice.subtract(entity.getPrice())
                : BigDecimal.ZERO;
        String thumb = entity.getThumbnailUrl();
        if (thumb == null || thumb.isBlank()) {
            thumb = itemDtos.stream().map(Item::getThumbnailUrl).filter(u -> u != null && !u.isBlank()).findFirst().orElse(null);
        }
        return ServicePackageResponse.builder()
                .id(entity.getId())
                .purchaseToken(token(entity))
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .validityDays(entity.getValidityDays())
                .thumbnailUrl(thumb)
                .isActive(entity.getIsActive())
                .originalPrice(originalPrice.signum() > 0 ? originalPrice : null)
                .savingsAmount(savings.signum() > 0 ? savings : BigDecimal.ZERO)
                .totalSessions(totalSessions)
                .totalDurationMinutes(totalDurationMinutes)
                .items(itemDtos)
                .build();
    }
}
