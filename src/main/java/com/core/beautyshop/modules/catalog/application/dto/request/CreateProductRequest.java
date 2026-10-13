package com.core.beautyshop.modules.catalog.application.dto.request;

import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.modules.catalog.domain.enums.ProductType;
import com.core.beautyshop.modules.catalog.domain.enums.SkinType;
import com.core.beautyshop.modules.catalog.domain.enums.TargetGender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Schema(description = "Dữ liệu yêu cầu tạo mới sản phẩm")
public class CreateProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255)
    @Schema(description = "Tên sản phẩm", example = "Kem Chống Nắng La Roche-Posay Anthelios XL SPF 50+", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 255)
    @Schema(description = "Slug duy nhất", example = "kem-chong-nang-la-roche-posay-anthelios-xl-spf-50", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slug;

    @Size(max = 500)
    @Schema(description = "Mô tả tóm tắt", example = "Kiểm soát bóng nhờn hiệu quả dành cho da nhạy cảm.")
    private String shortDescription;

    @Schema(description = "Mô tả chi tiết", example = "Sản phẩm được các bác sĩ da liễu khuyên dùng...")
    private String description;

    @Size(max = 500)
    @Schema(description = "URL ảnh thumbnail đại diện", example = "https://cdn.beautyshop.com/products/lrp-anthelios.png")
    private String thumbnailUrl;

    @Schema(description = "Trạng thái ban đầu; null dùng ACTIVE", example = "ACTIVE")
    private ProductStatus status;

    @Schema(description = "Loại sản phẩm (PRODUCT, SERVICE, COMBO); null dùng PRODUCT", example = "PRODUCT")
    private ProductType productType;

    @Schema(description = "Đối tượng giới tính", example = "UNISEX")
    private TargetGender targetGender;

    @Schema(description = "Loại da thích hợp", example = "OILY")
    private SkinType skinType;

    @Schema(description = "Bảng thành phần hóa mỹ phẩm", example = "Aqua, Homosalate, Silica...")
    private String ingredients;

    @Schema(description = "Cách sử dụng", example = "Thoa trước khi ra nắng 20 phút.")
    private String howToUse;

    @Size(max = 100)
    @Schema(description = "Quốc gia xuất xứ", example = "Pháp")
    private String originCountry;

    @Size(max = 50)
    @Schema(description = "Dung tích đóng gói", example = "50ml")
    private String volume;

    @Schema(description = "Có chứa hương liệu không", example = "false")
    private Boolean hasFragrance;

    @Schema(description = "Có chứa cồn khô không", example = "false")
    private Boolean hasAlcohol;

    @Size(max = 500)
    @Schema(description = "Tóm tắt các hoạt chất nổi bật", example = "BHA 2% + Zinc PCA")
    private String keyActivesSummary;

    @Schema(description = "Đặt làm sản phẩm nổi bật", example = "false")
    private Boolean isFeatured;

    @Schema(description = "ID thương hiệu sản xuất", example = "1")
    private Long brandId;

    @Schema(description = "Danh sách ID danh mục phân loại", example = "[1, 3]")
    private List<Long> categoryIds;

    @Schema(description = "Danh sách ID thẻ tag", example = "[2, 5]")
    private List<Long> tagIds;

    @Schema(description = "Danh sách các biến thể hàng hóa cần khởi tạo")
    private List<@Valid VariantRequest> variants;

    @Schema(description = "Danh sách hình ảnh minh họa đính kèm")
    private List<ImageRequest> images;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductCreateVariantRequest", description = "Dữ liệu tạo biến thể SKU")
    public static class VariantRequest {
        @NotBlank(message = "Mã SKU không được để trống")
        @Schema(description = "Mã SKU duy nhất", example = "LRP-ANTHELIOS-50ML", requiredMode = Schema.RequiredMode.REQUIRED)
        private String sku;

        @NotBlank(message = "Tên biến thể không được để trống")
        @Schema(description = "Tên biến thể", example = "Chai 50ml", requiredMode = Schema.RequiredMode.REQUIRED)
        private String variantName;

        @NotNull(message = "Giá không được để trống")
        @DecimalMin(value = "0.0", message = "Giá phải lớn hơn hoặc bằng 0")
        @Schema(description = "Giá bán", example = "425000", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal price;

        @Schema(description = "Giá khuyến mãi không âm và không vượt price", example = "399000")
        @DecimalMin(value = "0.0", message = "Giá khuyến mãi phải lớn hơn hoặc bằng 0")
        private BigDecimal discountPrice;

        @Schema(description = "Dung tích", example = "50ml")
        private String volume;

        @Schema(description = "Màu sắc", example = "Trắng sữa")
        private String color;

        @Schema(description = "Mã vạch Barcode", example = "3337875546431")
        private String barcode;

        @Schema(description = "Đặt làm biến thể mặc định", example = "true")
        private Boolean isDefault;

        @AssertTrue(message = "Giá khuyến mãi phải nhỏ hơn hoặc bằng giá gốc")
        @JsonIgnore
        public boolean isDiscountPriceValid() {
            return price == null || discountPrice == null || discountPrice.compareTo(price) <= 0;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductCreateImageRequest", description = "Gắn ảnh bằng URL, không phải upload nhị phân")
    public static class ImageRequest {
        @NotBlank(message = "Đường dẫn hình ảnh không được để trống")
        @Schema(description = "URL hình ảnh", example = "https://cdn.beautyshop.com/products/lrp-anthelios-1.png", requiredMode = Schema.RequiredMode.REQUIRED)
        private String imageUrl;

        @Schema(description = "Mô tả alt cho hình ảnh", example = "Chai kem chống nắng La Roche-Posay")
        private String altText;

        @Schema(description = "Thứ tự sắp xếp", example = "1")
        private Integer displayOrder;

        @Schema(description = "Đặt làm ảnh chính", example = "true")
        private Boolean isPrimary;
    }
}
