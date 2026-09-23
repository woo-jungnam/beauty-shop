package com.core.beautyshop.modules.catalog.application.dto.response;

import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
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

    @Schema(description = "Giá niêm yết thấp nhất", example = "425000")
    private BigDecimal basePrice;

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

    public ProductListResponse(Long id, String name, String slug, String shortDescription,
                               String thumbnailUrl, BigDecimal basePrice, ProductStatus status,
                               Boolean isFeatured, Double averageRating, Integer totalReviews,
                               Long totalSold, String brandName) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.shortDescription = shortDescription;
        this.thumbnailUrl = thumbnailUrl;
        this.basePrice = basePrice;
        this.status = status;
        this.isFeatured = isFeatured;
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
        this.totalSold = totalSold;
        this.brandName = brandName;
    }
}
