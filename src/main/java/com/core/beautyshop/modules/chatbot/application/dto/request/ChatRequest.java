package com.core.beautyshop.modules.chatbot.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tư vấn gửi tới Trợ lý Chatbot AI")
public class ChatRequest {

    @JsonProperty("session_id")
    @JsonAlias({"sessionId", "session_id"})
    @Schema(description = "Mã hội thoại do client chọn; tự động sinh nếu bỏ trống; không phải phiên JWT", example = "sess_9a8f2bc1")
    private String sessionId;

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    @Schema(description = "Nội dung tin nhắn không rỗng", requiredMode = Schema.RequiredMode.REQUIRED, example = "Da tôi dầu mụn và nhạy cảm, bạn có thể gợi ý kem chống nắng kiềm dầu tốt không?")
    private String message;

    public String getSessionId() {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        return sessionId;
    }
}
