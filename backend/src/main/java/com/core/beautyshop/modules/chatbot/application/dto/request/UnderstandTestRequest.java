package com.core.beautyshop.modules.chatbot.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu kiểm tra phân tích và thấu hiểu truy vấn (NLU / Query Understanding)")
public class UnderstandTestRequest {

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Schema(description = "Nội dung câu nói hoặc yêu cầu từ người dùng", example = "Tìm serum chứa Niacinamide trị thâm giá dưới 500k")
    private String message;

    @JsonProperty("current_state")
    @JsonAlias({"currentState", "current_state"})
    @Schema(description = "Trạng thái ngữ cảnh tích lũy của cuộc hội thoại hiện tại")
    private Map<String, Object> currentState;
}
