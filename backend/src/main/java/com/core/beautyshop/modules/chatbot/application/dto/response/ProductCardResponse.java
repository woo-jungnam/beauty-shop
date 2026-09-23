package com.core.beautyshop.modules.chatbot.application.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thẻ thông tin sản phẩm đề xuất bởi AI")
public class ProductCardResponse {

    @Schema(description = "Mã định danh sản phẩm", example = "prod_001")
    private String id;

    @Schema(description = "Tên sản phẩm", example = "Serum Phục Hồi Da La Roche-Posay Hyalu B5")
    private String name;

    @Schema(description = "Thương hiệu", example = "La Roche-Posay")
    private String brand;

    @Schema(description = "Danh mục sản phẩm", example = "Serum & Tinh chất")
    private String category;

    @Schema(description = "Giá niêm yết (VNĐ)", example = "850000")
    private Double price;

    @JsonProperty("skin_type")
    @JsonAlias({"skinType", "skin_type"})
    @Schema(description = "Danh sách loại da phù hợp", example = "[\"Da khô\", \"Da nhạy cảm\"]")
    private List<String> skinType;

    @Schema(description = "Các vấn đề da giải quyết", example = "[\"Phục hồi\", \"Cấp ẩm\", \"Chống lão hóa\"]")
    private List<String> concerns;

    @JsonProperty("key_ingredients")
    @JsonAlias({"keyIngredients", "key_ingredients"})
    @Schema(description = "Thành phần hoạt chất nổi bật", example = "Hyaluronic Acid, Vitamin B5, Madecassoside")
    private String keyIngredients;

    @Schema(description = "Điểm đánh giá trung bình sao", example = "4.8")
    private Double rating;

    @JsonProperty("total_reviews")
    @JsonAlias({"totalReviews", "total_reviews"})
    @Schema(description = "Tổng số lượt đánh giá từ người dùng", example = "128")
    private Integer totalReviews;

    @JsonProperty("total_sold")
    @JsonAlias({"totalSold", "total_sold"})
    @Schema(description = "Tổng số sản phẩm đã bán", example = "540")
    private Integer totalSold;

    @JsonProperty("reason_for_recommendation")
    @JsonAlias({"reasonForRecommendation", "reason_for_recommendation"})
    @Schema(description = "Lý do chuyên môn đề xuất sản phẩm này cho tình trạng da của bạn",
            example = "Sản phẩm chứa nồng độ Vitamin B5 cao kết hợp HA giúp phục hồi màng bảo vệ da đang bị tổn thương nhanh chóng.")
    private String reasonForRecommendation;

    @JsonProperty("image_url")
    @JsonAlias({"imageUrl", "image_url"})
    @Schema(description = "Đường dẫn ảnh đại diện sản phẩm", example = "https://beautyshop.com/images/larocheposay-b5.jpg")
    private String imageUrl;
}
