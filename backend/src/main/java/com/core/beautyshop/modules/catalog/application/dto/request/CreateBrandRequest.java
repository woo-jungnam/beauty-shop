package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dữ liệu yêu cầu tạo thương hiệu mới")
public class CreateBrandRequest {

    @NotBlank(message = "Tên thương hiệu không được để trống")
    @Size(max = 150, message = "Tên thương hiệu không được vượt quá 150 ký tự")
    @Schema(description = "Tên thương hiệu mỹ phẩm", example = "La Roche-Posay", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 200, message = "Slug không được vượt quá 200 ký tự")
    @Schema(description = "Đường dẫn định danh slug (duy nhất)", example = "la-roche-posay", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slug;

    @Size(max = 500, message = "Đường dẫn ảnh logo không được vượt quá 500 ký tự")
    @Schema(description = "URL ảnh logo thương hiệu", example = "https://cdn.beautyshop.com/brands/larocheposay.png")
    private String logoUrl;

    @Schema(description = "Mô tả giới thiệu về thương hiệu", example = "Thương hiệu dược mỹ phẩm hàng đầu từ Pháp, chuyên gia cho da nhạy cảm.")
    private String description;

    @Size(max = 100, message = "Tên quốc gia xuất xứ không được vượt quá 100 ký tự")
    @Schema(description = "Quốc gia xuất xứ của thương hiệu", example = "Pháp")
    private String originCountry;

    @Schema(description = "Có phải gian hàng/thương hiệu chính hãng hay không", example = "true")
    private Boolean isOfficial;
}
