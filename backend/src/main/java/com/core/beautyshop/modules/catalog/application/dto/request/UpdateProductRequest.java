package com.core.beautyshop.modules.catalog.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.modules.catalog.domain.enums.ProductType;
import com.core.beautyshop.modules.catalog.domain.enums.SkinType;
import com.core.beautyshop.modules.catalog.domain.enums.TargetGender;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Field null/omitted giữ nguyên; danh sách gửi [] sẽ gỡ toàn bộ liên kết")
public class UpdateProductRequest {

    @Size(max = 255)
    @Schema(description = "Tên không rỗng khi gửi", maxLength = 255)
    private String name;

    @Size(max = 255)
    @Schema(description = "Slug duy nhất không rỗng khi gửi", maxLength = 255)
    private String slug;

    @Size(max = 500)
    @Schema(description = "Mô tả ngắn", maxLength = 500)
    private String shortDescription;

    @Schema(description = "Mô tả chi tiết")
    private String description;

    @Size(max = 500)
    @Schema(description = "Đường dẫn ảnh; chuỗi rỗng gỡ thumbnail", maxLength = 500)
    private String thumbnailUrl;

    @Schema(description = "Trạng thái sản phẩm", example = "ACTIVE")
    private ProductStatus status;
    @Schema(description = "Loại sản phẩm", example = "PRODUCT")
    private ProductType productType;
    @Schema(description = "Giới tính mục tiêu", example = "UNISEX")
    private TargetGender targetGender;
    @Schema(description = "Loại da", example = "ALL_SKIN")
    private SkinType skinType;

    @Schema(description = "Thành phần dạng văn bản")
    private String ingredients;
    @Schema(description = "Hướng dẫn sử dụng")
    private String howToUse;

    @Size(max = 100)
    @Schema(description = "Xuất xứ", maxLength = 100)
    private String originCountry;

    @Size(max = 50)
    @Schema(description = "Dung tích", maxLength = 50)
    private String volume;

    @Schema(description = "Có chứa hương liệu không")
    private Boolean hasFragrance;

    @Schema(description = "Có chứa cồn khô không")
    private Boolean hasAlcohol;

    @Size(max = 500)
    @Schema(description = "Tóm tắt các hoạt chất nổi bật", maxLength = 500)
    private String keyActivesSummary;

    @Schema(description = "Đánh dấu nổi bật")
    private Boolean isFeatured;

    @Schema(description = "ID thương hiệu chưa xóa; null giữ nguyên liên kết")
    private Long brandId;
    @Schema(description = "Thay toàn bộ danh mục; [] gỡ mọi danh mục", example = "[1, 3]")
    private List<Long> categoryIds;
    @Schema(description = "Thay toàn bộ tag; [] gỡ mọi tag", example = "[2]")
    private List<Long> tagIds;
}
