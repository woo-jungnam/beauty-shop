package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dữ liệu yêu cầu cập nhật thương hiệu")
public class UpdateBrandRequest {

    @Size(max = 150, message = "Tên thương hiệu không được vượt quá 150 ký tự")
    @Schema(description = "Tên thương hiệu mỹ phẩm mới", example = "La Roche-Posay Official")
    private String name;

    @Size(max = 200, message = "Slug không được vượt quá 200 ký tự")
    @Schema(description = "Slug mới của thương hiệu", example = "la-roche-posay-official")
    private String slug;

    @Size(max = 500, message = "Đường dẫn ảnh logo không được vượt quá 500 ký tự")
    @Schema(description = "URL ảnh logo mới", example = "https://cdn.beautyshop.com/brands/larocheposay-new.png")
    private String logoUrl;

    @Schema(description = "Mô tả mới", example = "Cập nhật thông tin đại lý độc quyền tại Việt Nam.")
    private String description;

    @Size(max = 100, message = "Tên quốc gia xuất xứ không được vượt quá 100 ký tự")
    @Schema(description = "Quốc gia xuất xứ mới", example = "Pháp")
    private String originCountry;

    @Schema(description = "Trạng thái chính hãng", example = "true")
    private Boolean isOfficial;
}
