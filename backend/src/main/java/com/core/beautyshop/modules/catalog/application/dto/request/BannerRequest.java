package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tạo hoặc cập nhật Banner")
public class BannerRequest {

    @NotBlank(message = "Tiêu đề banner không được để trống")
    @Size(max = 200, message = "Tiêu đề không quá 200 ký tự")
    @Schema(description = "Tên quản lý và mô tả thay thế của ảnh; không phủ chữ lên banner", example = "Ưu đãi chăm sóc da")
    private String title;

    @Size(max = 100, message = "Badge không quá 100 ký tự")
    @Schema(description = "Huy hiệu phụ trên banner", example = "BEAUTYSHOP CHÍNH HÃNG 100%")
    private String badge;

    @Size(max = 500, message = "Mô tả không quá 500 ký tự")
    @Schema(description = "Mô tả chi tiết", example = "Cam kết 100% hàng thật, date xa chuẩn FEFO...")
    private String description;

    @NotBlank(message = "Đường dẫn ảnh banner không được để trống")
    @Size(max = 1000, message = "URL ảnh không quá 1000 ký tự")
    @Schema(description = "URL ảnh banner", example = "https://images.unsplash.com/photo-1522337360788-8b13dee7a37e")
    private String imageUrl;

    @NotBlank(message = "Trang đích của banner không được để trống")
    @Size(max = 500, message = "Đường dẫn đích không quá 500 ký tự")
    @Schema(description = "Đường dẫn liên kết khi click", example = "products")
    private String targetUrl;

    @AssertTrue(message = "Trang đích phải là đường dẫn nội bộ hoặc URL http/https hợp lệ")
    public boolean isTargetUrlAllowed() {
        if (targetUrl == null || targetUrl.isBlank()) return true; // Report the field's NotBlank error.
        String target = targetUrl.trim();
        if (target.equals("#") || target.startsWith("//") || target.contains("\\")
                || target.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) return false;
        if (!target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) return true;
        try {
            java.net.URI uri = java.net.URI.create(target);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException error) { return false; }
    }

    @Size(max = 50, message = "Nút bấm không quá 50 ký tự")
    @Schema(description = "Nút kêu gọi hành động", example = "Mua Sắm Ngay")
    private String ctaText;

    @Size(max = 50, message = "Vị trí không quá 50 ký tự")
    @Schema(description = "Vị trí hiển thị: HERO_SLIDE, HERO_SIDE, PROMO", example = "HERO_SLIDE")
    @Builder.Default
    private String position = "HERO_SLIDE";

    @Schema(description = "Thứ tự sắp xếp", example = "1")
    @Builder.Default
    private Integer sortOrder = 0;

    @Schema(description = "Trạng thái hiển thị", example = "true")
    @Builder.Default
    private Boolean isActive = true;
}
