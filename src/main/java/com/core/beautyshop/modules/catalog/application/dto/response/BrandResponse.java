package com.core.beautyshop.modules.catalog.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của thương hiệu")
public class BrandResponse {

    @Schema(description = "ID định danh của thương hiệu", example = "1")
    private Long id;

    @Schema(description = "Tên thương hiệu", example = "La Roche-Posay")
    private String name;

    @Schema(description = "Đường dẫn slug", example = "la-roche-posay")
    private String slug;

    @Schema(description = "URL ảnh logo", example = "https://cdn.beautyshop.com/brands/larocheposay.png")
    private String logoUrl;

    @Schema(description = "Mô tả chi tiết", example = "Thương hiệu dược mỹ phẩm hàng đầu từ Pháp.")
    private String description;

    @Schema(description = "Quốc gia xuất xứ", example = "Pháp")
    private String originCountry;

    @Schema(description = "Đánh dấu thương hiệu chính hãng", example = "true")
    private Boolean isOfficial;
}
