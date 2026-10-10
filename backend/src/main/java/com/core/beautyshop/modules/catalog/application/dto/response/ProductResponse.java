package com.core.beautyshop.modules.catalog.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.core.beautyshop.modules.catalog.domain.enums.ProductImageType;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.modules.catalog.domain.enums.ProductType;
import com.core.beautyshop.modules.catalog.domain.enums.SkinType;
import com.core.beautyshop.modules.catalog.domain.enums.TargetGender;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết của sản phẩm")
public class ProductResponse {

    @Schema(description = "ID sản phẩm", example = "101")
    private Long id;

    @Schema(description = "Tên sản phẩm", example = "Kem Chống Nắng La Roche-Posay Anthelios XL SPF 50+")
    private String name;

    @Schema(description = "Đường dẫn slug", example = "kem-chong-nang-la-roche-posay-anthelios-xl-spf-50")
    private String slug;

    @Schema(description = "Mô tả ngắn gọn", example = "Kem chống nắng kiểm soát dầu hiệu quả dành cho da dầu mụn.")
    private String shortDescription;

    @Schema(description = "Mô tả chi tiết định dạng Markdown hoặc HTML", example = "Sản phẩm chứa màng lọc độc quyền Mexoplex...")
    private String description;

    @Schema(description = "URL ảnh đại diện chính", example = "https://cdn.beautyshop.com/products/lrp-anthelios.png")
    private String thumbnailUrl;

    @Schema(description = "Giá thấp nhất dùng discountPrice nếu có, nếu không dùng price của SKU active chưa xóa (VND); 0 khi không có SKU", example = "380000")
    private BigDecimal minPrice;

    @Schema(description = "Giá cao nhất dùng discountPrice nếu có, nếu không dùng price của SKU active chưa xóa (VND)", example = "480000")
    private BigDecimal maxPrice;

    @Schema(description = "Alias tương thích của minPrice, không phải field nhập giá sản phẩm", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public BigDecimal getBasePrice() {
        return minPrice;
    }

    @Schema(description = "Trạng thái hiển thị sản phẩm", example = "ACTIVE")
    private ProductStatus status;

    @Schema(description = "Loại sản phẩm (PRODUCT, SERVICE, COMBO)", example = "PRODUCT")
    private ProductType productType;

    @Schema(description = "Đối tượng giới tính mục tiêu (UNISEX, FEMALE, MALE)", example = "UNISEX")
    private TargetGender targetGender;

    @Schema(description = "Loại da phù hợp (ALL_SKIN, OILY, DRY, COMBINATION, SENSITIVE, NORMAL)", example = "OILY")
    private SkinType skinType;

    @Schema(description = "Bảng thành phần hóa học", example = "Aqua, Homosalate, Silica, Ethylhexyl Salicylate...")
    private String ingredients;

    @Schema(description = "Hướng dẫn sử dụng", example = "Thoa trước khi ra nắng 20 phút. Thoa lại sau mỗi 2-3 tiếng.")
    private String howToUse;

    @Schema(description = "Quốc gia xuất xứ", example = "Pháp")
    private String originCountry;

    @Schema(description = "Dung tích / trọng lượng", example = "50ml")
    private String volume;

    @Schema(description = "Đánh dấu sản phẩm nổi bật trên trang chủ", example = "true")
    private Boolean isFeatured;

    @Schema(description = "Điểm trung bình đánh giá APPROVED; 0 khi chưa có đánh giá", example = "4.8")
    private Double averageRating;

    @Schema(description = "Tổng số lượt đánh giá", example = "128")
    private Integer totalReviews;

    @Schema(description = "Tổng số lượng đã bán", example = "1540")
    private Long totalSold;

    @Schema(description = "Thời gian tạo sản phẩm (UTC Instant)", example = "2026-08-01T08:00:00Z")
    private Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất (UTC Instant)", example = "2026-08-20T14:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Thông tin thương hiệu sản xuất")
    private BrandResponse brand;

    @Schema(description = "Danh mục sản phẩm được phân loại")
    private List<CategoryResponse> categories;

    @Schema(description = "Danh sách các biến thể (SKU, màu sắc, kích cỡ)")
    private List<VariantResponse> variants;

    @Schema(description = "Bộ sưu tập hình ảnh sản phẩm")
    private List<ImageResponse> images;

    @Schema(description = "Danh sách tag nhãn gắn với sản phẩm")
    private List<TagResponse> tags;

    private List<AttributeValueResponse> attributeValues;

    @Schema(description = "Có chứa hương liệu không")
    private Boolean hasFragrance;

    @Schema(description = "Có chứa cồn khô không")
    private Boolean hasAlcohol;

    @Schema(description = "Tóm tắt các hoạt chất nổi bật")
    private String keyActivesSummary;

    @Schema(description = "Danh sách chi tiết các thành phần hóa mỹ phẩm")
    private List<IngredientDetailResponse> ingredientsList;

    @Schema(description = "Tóm tắt thành phần")
    private IngredientSummaryResponse ingredientSummary;

    @Schema(description = "Khả năng tương thích loại da và cảnh báo chống chỉ định")
    private SkinCompatibilityResponse skinCompatibility;

    @Schema(description = "Các vấn đề về da giải quyết kèm điểm số")
    private List<SkinConcernResponse> skinConcerns;

    @Schema(description = "Hướng dẫn sử dụng và cảnh báo an toàn")
    private UsageDetailResponse usage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailVariantResponse", description = "Thông tin biến thể sản phẩm (SKU)")
    public static class VariantResponse {
        @Schema(description = "ID biến thể", example = "201")
        private Long id;

        @Schema(description = "Mã SKU quản lý kho", example = "LRP-ANTHELIOS-50ML")
        private String sku;

        @Schema(description = "Tên hiển thị biến thể", example = "Chai 50ml (Không màu)")
        private String variantName;

        @Schema(description = "Giá bán tiêu chuẩn (VND)", example = "425000")
        private BigDecimal price;

        @Schema(description = "Giá gốc trước khuyến mãi (VND)", example = "480000")
        private BigDecimal originalPrice;

        @Schema(description = "Giá sau giảm giá (VND, null nếu không giảm)", example = "399000")
        private BigDecimal discountPrice;

        @Schema(description = "Đơn vị tiền tệ", example = "VND")
        private String currency;

        @Schema(description = "Dung tích text", example = "50ml")
        private String volume;

        @Schema(description = "Giá trị dung tích số", example = "50.0")
        private BigDecimal volumeValue;

        @Schema(description = "Đơn vị đo dung tích", example = "ml")
        private String volumeUnit;

        @Schema(description = "Màu sắc", example = "Trong suốt")
        private String color;

        @Schema(description = "Mã vạch Barcode", example = "3337875546431")
        private String barcode;

        @Schema(description = "Có phải biến thể mặc định khi vào trang chi tiết không", example = "true")
        private Boolean isDefault;

        @Schema(description = "Trạng thái đang hoạt động / kinh doanh", example = "true")
        private Boolean isActive;

        @Schema(description = "Số lượng tồn kho khả dụng sau khi trừ số lượng đã giữ")
        private Integer stockQuantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailImageResponse", description = "Hình ảnh minh họa sản phẩm")
    public static class ImageResponse {
        @Schema(description = "ID ảnh", example = "301")
        private Long id;

        @Schema(description = "URL hình ảnh", example = "https://cdn.beautyshop.com/products/lrp-anthelios-1.png")
        private String imageUrl;

        @Schema(description = "Văn bản thay thế (SEO Alt)", example = "Mặt trước sản phẩm chống nắng La Roche-Posay")
        private String altText;

        @Schema(description = "Loại ảnh", example = "PRODUCT_FRONT")
        private ProductImageType imageType;

        @Schema(description = "Thứ tự sắp xếp hiển thị", example = "1")
        private Integer displayOrder;

        @Schema(description = "Có phải ảnh chính của sản phẩm không", example = "true")
        private Boolean isPrimary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailTagResponse", description = "Thẻ từ khóa / Tag")
    public static class TagResponse {
        @Schema(description = "ID thẻ", example = "1")
        private Long id;

        @Schema(description = "Tên nhãn hiển thị", example = "Chống nắng kiểm soát dầu")
        private String name;

        @Schema(description = "Slug nhãn", example = "chong-nang-kiem-soat-dau")
        private String slug;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailIngredientDetailResponse", description = "Chi tiết thành phần mỹ phẩm")
    public static class IngredientDetailResponse {
        @Schema(description = "ID thành phần")
        private Long ingredientId;
        @Schema(description = "Tên hiển thị")
        private String name;
        @Schema(description = "Tên INCI")
        private String inciName;
        @Schema(description = "Nồng độ, có thể null")
        private BigDecimal concentration;
        @Schema(description = "Đơn vị nồng độ")
        private String concentrationUnit;
        @Schema(description = "Thành phần nổi bật")
        private Boolean isKeyActive;
        @Schema(description = "Chức năng đã lưu")
        private List<String> function;
        @Schema(description = "Lợi ích đã lưu")
        private List<String> benefits;
        @Schema(description = "Lưu ý đã lưu")
        private List<String> potentialConcerns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailIngredientSummaryResponse", description = "Tóm tắt các nhóm thành phần chính")
    public static class IngredientSummaryResponse {
        @Schema(description = "Tên các hoạt chất chính")
        private List<String> keyActives;
        @Schema(description = "Nhóm cấp ẩm")
        private List<String> hydratingIngredients;
        @Schema(description = "Nhóm tẩy tế bào chết")
        private List<String> exfoliatingIngredients;
        @Schema(description = "Dữ liệu có hương liệu")
        private Boolean fragrance;
        @Schema(description = "Dữ liệu có cồn")
        private Boolean alcohol;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailSkinCompatibilityResponse", description = "Độ tương thích loại da và cảnh báo")
    public static class SkinCompatibilityResponse {
        @Schema(description = "Mapping loại da phù hợp đã lưu")
        private List<RecommendedSkinType> recommendedSkinTypes;
        @Schema(description = "Mapping loại da không lý tưởng đã lưu")
        private List<NotIdealForSkinType> notIdealFor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailRecommendedSkinType", description = "Loại da khuyên dùng kèm điểm số")
    public static class RecommendedSkinType {
        @Schema(description = "ID loại da")
        private Long skinTypeId;
        @Schema(description = "Mã loại da hoặc vấn đề da")
        private String code;
        @Schema(description = "Tên hiển thị")
        private String name;
        @Schema(description = "Điểm phù hợp đã lưu, có thể null")
        private BigDecimal score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailNotIdealForSkinType", description = "Loại da chống chỉ định kèm lý do")
    public static class NotIdealForSkinType {
        @Schema(description = "ID loại da")
        private Long skinTypeId;
        @Schema(description = "Tên loại da")
        private String skinType;
        @Schema(description = "Lý do đã lưu")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailSkinConcernResponse", description = "Vấn đề về da giải quyết")
    public static class SkinConcernResponse {
        @Schema(description = "ID vấn đề da")
        private Long concernId;
        @Schema(description = "Mã loại da hoặc vấn đề da")
        private String code;
        @Schema(description = "Tên hiển thị")
        private String name;
        @Schema(description = "Điểm phù hợp đã lưu, có thể null")
        private BigDecimal score;
        @Schema(description = "Ghi chú đã lưu")
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ProductDetailUsageDetailResponse", description = "Hướng dẫn sử dụng và cảnh báo an toàn")
    public static class UsageDetailResponse {
        @Schema(description = "Thời điểm dùng")
        private List<String> whenToUse;
        @Schema(description = "Tần suất")
        private String frequency;
        @Schema(description = "Hướng dẫn đã lưu")
        private List<String> instructions;
        @Schema(description = "Cảnh báo đã lưu")
        private List<String> warnings;
    }
}
