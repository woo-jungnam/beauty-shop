package com.core.beautyshop.modules.chatbot.application.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Nguồn tài liệu trích dẫn dùng làm căn cứ trả lời của AI")
public class SourceRefResponse {

    @JsonProperty("product_id")
    @JsonAlias({"productId", "product_id"})
    @Schema(description = "Mã định danh sản phẩm trích dẫn", example = "prod_001")
    private String productId;

    @JsonProperty("product_name")
    @JsonAlias({"productName", "product_name"})
    @Schema(description = "Tên sản phẩm trích dẫn", example = "Serum Phục Hồi Da La Roche-Posay Hyalu B5")
    private String productName;

    @JsonProperty("chunk_type")
    @JsonAlias({"chunkType", "chunk_type"})
    @Schema(description = "Khía cạnh dữ liệu (mô tả, thành phần hoạt chất, công dụng)", example = "product_details")
    private String chunkType;

    @Schema(description = "Trích đoạn thông tin từ kho tri thức dùng làm căn cứ suy luận",
            example = "La Roche-Posay - Serum Hyalu B5: Hỗ trợ tái tạo màng bảo vệ da với Panthenol và Hyaluronic Acid đa kích thước phân tử...")
    private String snippet;
}
