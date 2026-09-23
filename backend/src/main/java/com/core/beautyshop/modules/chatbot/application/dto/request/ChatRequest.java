package com.core.beautyshop.modules.chatbot.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tư vấn gửi tới Trợ lý Chatbot AI")
public class ChatRequest {

    @NotBlank(message = "Mã phiên hội thoại không được để trống")
    @JsonProperty("session_id")
    @JsonAlias({"sessionId", "session_id"})
    @Schema(description = "Mã phiên hội thoại định danh ngữ cảnh khách hàng", example = "sess_9a8f2bc1")
    private String sessionId;

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    @Schema(description = "Nội dung câu hỏi hoặc nhu cầu chăm sóc da cần tư vấn", example = "Da tôi dầu mụn và nhạy cảm, bạn có thể gợi ý kem chống nắng kiềm dầu tốt không?")
    private String message;
}
