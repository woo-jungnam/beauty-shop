package com.core.beautyshop.modules.catalog.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin tóm tắt sản phẩm trong danh sách và kết quả tìm kiếm")
public class ProductListResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID sản phẩm", example = "101")
    private Long id;

    @Schema(description = "Tên sản phẩm", example = "Kem Chống Nắng La Roche-Posay Anthelios XL")
    private String name;

    @Schema(description = "Đường dẫn slug", example = "kem-chong-nang-la-roche-posay-anthelios-xl")
    private String slug;

    @Schema(description = "Mô tả ngắn", example = "Kiểm soát dầu nhờn suốt 8 giờ.")
    private String shortDescription;

    @Schema(description = "URL ảnh thu nhỏ thumbnail", example = "https://cdn.beautyshop.com/products/lrp-thumb.png")
    private String thumbnailUrl;

    @Schema(description = "Giá nhỏ nhất dùng discountPrice nếu có, nếu không dùng price của SKU active chưa xóa (VND); 0 nếu không có SKU", example = "380000")
    private BigDecimal minPrice;

    @Schema(description = "Giá lớn nhất dùng discountPrice nếu có, nếu không dùng price của SKU active chưa xóa (VND)", example = "480000")
    private BigDecimal maxPrice;

    @Schema(description = "Trạng thái hiển thị", example = "ACTIVE")
    private ProductStatus status;

    @Schema(description = "Đánh dấu sản phẩm nổi bật", example = "true")
    private Boolean isFeatured;

    @Schema(description = "Điểm đánh giá trung bình", example = "4.8")
    private Double averageRating;

    @Schema(description = "Tổng số lượt đánh giá", example = "128")
    private Integer totalReviews;

    @Schema(description = "Tổng số lượng đã bán", example = "1540")
    private Long totalSold;

    @Schema(description = "Tên thương hiệu", example = "La Roche-Posay")
    private String brandName;

    @Schema(description = "ID thương hiệu", example = "1")
    private Long brandId;

    @Schema(description = "ID nhỏ nhất trong các danh mục liên kết; có thể null", example = "1")
    private Long categoryId;

    @Schema(description = "Hạn sử dụng sớm nhất của lô hàng cận date (nếu có)", example = "2026-11-20")
    private LocalDate earliestExpirationDate;

    @Schema(description = "Số ngày còn lại đến hạn sử dụng", example = "45")
    private Integer daysRemaining;

    @Schema(description = "Số lượng hàng cận date còn khả dụng", example = "25")
    private Integer clearanceStock;

    // JPQL Constructor for ProductRepository (15 args)
    public ProductListResponse(Long id, String name, String slug, String shortDescription,
                               String thumbnailUrl, BigDecimal minPrice, BigDecimal maxPrice,
                               ProductStatus status, Boolean isFeatured, Double averageRating,
                               Integer totalReviews, Long totalSold, String brandName,
                               Long brandId, Long categoryId) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.shortDescription = shortDescription;
        this.thumbnailUrl = thumbnailUrl;
        this.minPrice = minPrice != null ? minPrice : BigDecimal.ZERO;
        this.maxPrice = maxPrice != null ? maxPrice : this.minPrice;
        this.status = status;
        this.isFeatured = isFeatured;
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
        this.totalSold = totalSold;
        this.brandName = brandName;
        this.brandId = brandId;
        this.categoryId = categoryId;
    }

    // Backward-compatible JPQL Constructor (13 args)
    public ProductListResponse(Long id, String name, String slug, String shortDescription,
                               String thumbnailUrl, BigDecimal minPrice, BigDecimal maxPrice,
                               ProductStatus status, Boolean isFeatured, Double averageRating,
                               Integer totalReviews, Long totalSold, String brandName) {
        this(id, name, slug, shortDescription, thumbnailUrl, minPrice, maxPrice, status, isFeatured, averageRating, totalReviews, totalSold, brandName, null, null);
    }

    // Backward-compatible getters
    @Schema(description = "Alias tương thích của minPrice", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public BigDecimal getBasePrice() {
        return minPrice;
    }

    @Schema(description = "Alias tương thích của minPrice", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public BigDecimal getPrice() {
        return minPrice;
    }
}
