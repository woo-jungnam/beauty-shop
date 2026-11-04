package com.core.beautyshop.modules.chatbot.application.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Hành động tương tác nhanh do Chatbot AI sinh ra")
public class ChatActionResponse {

    @JsonProperty("action_type")
    @JsonAlias({"actionType", "action_type"})
    @Schema(description = "Loại hành động (ADD_TO_CART, BOOKING_PROPOSAL, VIEW_ORDER)", example = "ADD_TO_CART")
    private String actionType;

    @Schema(description = "Nhãn hiển thị trên nút bấm tương tác", example = "Thêm Vào Giỏ Ngay")
    private String label;

    @Schema(description = "Dữ liệu payload đi kèm hành động")
    private Map<String, Object> payload;
}
