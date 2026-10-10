package com.core.beautyshop.modules.catalog.application.dto.request;

import com.core.beautyshop.modules.catalog.domain.enums.ProductType;
import com.core.beautyshop.modules.catalog.domain.enums.SkinType;
import com.core.beautyshop.modules.catalog.domain.enums.TargetGender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Optional public catalog filters. Dimensions are combined with AND; IDs within a dimension use OR. */
@Getter
@Setter
public class ProductSearchRequest {
    @Size(max = 200)
    @Schema(description = "Từ khóa không phân biệt hoa thường, trim khoảng trắng; tên, slug, mô tả, thương hiệu, thành phần/hoạt chất, nhãn hoặc SKU/barcode/tên biến thể active. %, _ được tìm như ký tự thường. Bỏ trống để chỉ lọc.", example = "chống nắng")
    private String keyword;

    @Positive
    @Schema(description = "Một ID thương hiệu; hợp với brandIds nếu gửi cả hai", example = "1")
    private Long brandId;

    @Size(max = 50)
    @Schema(description = "Các ID thương hiệu (OR), dạng brandIds=1,2 hoặc lặp tham số")
    private List<@NotNull @Positive Long> brandIds;

    @Positive
    @Schema(description = "Một ID danh mục chính xác, không tự bao gồm danh mục con; hợp với categoryIds", example = "1")
    private Long categoryId;

    @Size(max = 50)
    @Schema(description = "Các ID danh mục chính xác (OR); chỉ danh mục active chưa xóa")
    private List<@NotNull @Positive Long> categoryIds;

    @Size(max = 50)
    @Schema(description = "Các ID nhãn sản phẩm (OR); chỉ nhãn chưa xóa")
    private List<@NotNull @Positive Long> tagIds;

    @Schema(description = "Loại sản phẩm theo enum của catalog")
    private ProductType productType;

    @Schema(description = "Loại da: dữ liệu được khuyến nghị hoặc fallback skinType/ALL_SKIN; chống chỉ định rõ ràng cho loại da yêu cầu chặn kết quả. Khi lọc ALL_SKIN, bất kỳ chống chỉ định nào cũng chặn kết quả.")
    private SkinType skinType;

    @Schema(description = "Giới tính mục tiêu chính xác theo enum")
    private TargetGender targetGender;

    @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
    @Schema(description = "Giá SKU hiệu lực tối thiểu (VND), bao gồm biên. discountPrice nếu có, ngược lại price. Hai biên phải khớp cùng SKU active chưa xóa.", example = "100000")
    private BigDecimal minPrice;

    @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
    @Schema(description = "Giá SKU hiệu lực tối đa (VND), bao gồm biên; phải >= minPrice", example = "500000")
    private BigDecimal maxPrice;

    @DecimalMin("0") @DecimalMax("5")
    @Schema(description = "Điểm đánh giá trung bình tối thiểu, từ 0 đến 5", example = "4")
    private BigDecimal minRating;

    @Schema(description = "Lọc cờ sản phẩm nổi bật chính xác; bỏ trống không lọc")
    private Boolean isFeatured;

    @Schema(description = "true: có SKU discountPrice < price; false: có SKU không giảm giá. Kết hợp giá/tồn kho true trên cùng SKU. Đây là giá catalog, chưa tính voucher.")
    private Boolean onSale;

    @Schema(description = "true: có SKU còn tồn khả dụng, cùng SKU thỏa giá/onSale; false: không có SKU active còn tồn khả dụng. Loại lượng đã giữ chỗ, lô hết hạn và kho ngừng hoạt động/đã xóa.")
    private Boolean inStock;

    @Schema(description = "Lọc cờ có hương liệu chính xác")
    private Boolean hasFragrance;

    @Schema(description = "Lọc cờ có cồn chính xác")
    private Boolean hasAlcohol;

    @Size(max = 100)
    @Schema(description = "Xuất xứ chính xác, trim và không phân biệt hoa thường; bỏ trống không lọc", example = "France")
    private String originCountry;

    @Size(max = 50)
    @Schema(description = "Các ID thành phần có trong công thức (OR)")
    private List<@NotNull @Positive Long> ingredientIds;

    @Size(max = 50)
    @Schema(description = "Các ID vấn đề da được sản phẩm hỗ trợ (OR)")
    private List<@NotNull @Positive Long> skinConcernIds;

    // Validate raw pagination rather than accepting Spring's silent clamping of invalid values.
    // Pageable supplies these parameters in OpenAPI and supports repeated sort fields.
    @NotNull @Min(0)
    @Schema(hidden = true)
    private Integer page = 0;

    @NotNull @Min(1) @Max(100)
    @Schema(hidden = true)
    private Integer size = 20;

    @AssertTrue(message = "maxPrice phải lớn hơn hoặc bằng minPrice")
    @Schema(hidden = true)
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }
}
