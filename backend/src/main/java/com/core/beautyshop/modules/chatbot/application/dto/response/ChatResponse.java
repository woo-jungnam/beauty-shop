package com.core.beautyshop.modules.chatbot.application.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả phản hồi hoàn chỉnh từ Trợ lý Chatbot AI")
public class ChatResponse {

    @JsonProperty("session_id")
    @JsonAlias({"sessionId", "session_id"})
    @Schema(description = "Mã phiên hội thoại định danh", example = "sess_9a8f2bc1")
    private String sessionId;

    @Schema(description = "Ý định người dùng được hệ thống phân tích xác định", example = "PRODUCT_RECOMMENDATION")
    private String intent;

    @Schema(description = "Câu trả lời tự nhiên, giàu chuyên môn da liễu và chăm sóc sắc đẹp từ AI",
            example = "Chào bạn, với tình trạng da dầu mụn nhạy cảm, bạn nên ưu tiên các dòng kem chống nắng quang phổ rộng dạng gel mỏng nhẹ...")
    private String answer;

    @Schema(description = "Bí danh tương thích ngược cho trường answer",
            example = "Chào bạn, với tình trạng da dầu mụn nhạy cảm...")
    private String message;

    @Schema(description = "Danh sách thẻ sản phẩm tối ưu được hệ thống đề xuất")
    private List<ProductCardResponse> products;

    @Schema(description = "Danh sách nguồn thông tin thực tế được AI trích dẫn")
    private List<SourceRefResponse> sources;

    @JsonProperty("needs_clarification")
    @JsonAlias({"needsClarification", "needs_clarification"})
    @Schema(description = "Đánh dấu câu hỏi hiện tại có cần khách hàng cung cấp thêm thông tin làm rõ hay không", example = "false")
    private Boolean needsClarification;

    @Schema(description = "Chi tiết kết quả phân tích thấu hiểu truy vấn (Observability)")
    private Object understanding;

    @JsonProperty("stage_latencies_ms")
    @JsonAlias({"stageLatenciesMs", "stage_latencies_ms"})
    @Schema(description = "Thời gian xử lý của từng công đoạn trong pipeline RAG (milliseconds)")
    private Map<String, Object> stageLatenciesMs;
}
