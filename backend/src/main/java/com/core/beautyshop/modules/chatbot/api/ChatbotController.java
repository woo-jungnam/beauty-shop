package com.core.beautyshop.modules.chatbot.api;

import com.core.beautyshop.modules.chatbot.application.dto.request.ChatRequest;
import com.core.beautyshop.modules.chatbot.application.dto.request.UnderstandTestRequest;
import com.core.beautyshop.modules.chatbot.application.dto.response.ChatResponse;
import com.core.beautyshop.modules.chatbot.application.service.ChatbotService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Trợ lý Chatbot AI", description = "Giao tiếp với dịch vụ Chatbot, kiểm tra kết nối và quản trị đồng bộ dữ liệu")
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @Value("${jwt.secret:}")
    private String contextSecret;

    private void scopeContext(ChatRequest request, String guestSession) {
        if (contextSecret == null || contextSecret.isBlank()) return;
        String owner = SecurityUtils.getCurrentUserIdOptional()
                .map(id -> "user:" + id)
                .orElseGet(() -> guestSession != null && guestSession.matches("[a-zA-Z0-9_-]{24,128}")
                        ? "guest:" + guestSession : "ephemeral:" + UUID.randomUUID());
        String key = sign("scope:" + owner + "\0" + request.getSessionId());
        request.setContextId(key + "." + sign("context:" + key));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(contextSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot sign chatbot context", exception);
        }
    }

    @Operation(
            summary = "Trò chuyện tư vấn sản phẩm (Non-streaming)",
            description = "API công khai. Gửi message và session_id tới dịch vụ AI; trả JSON trong ApiResponse. session_id là mã hội thoại do client cung cấp, không phải phiên xác thực JWT."
    )
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Guest-Session-Id", required = false) String guestSession
    ) {
        scopeContext(request, guestSession);
        ChatResponse response = chatbotService.chat(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Nhận phản hồi tư vấn thành công"));
    }

    @Operation(
            summary = "Trò chuyện tư vấn sản phẩm (Streaming SSE)",
            description = "API công khai. Trả luồng SSE gốc từ dịch vụ AI, không bọc ApiResponse. Client phải đọc text/event-stream. Lỗi sau khi bắt đầu luồng có thể được phát bằng event type=error trong HTTP 200."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Luồng Server-Sent Events", content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "string", example = "data: {\"type\":\"error\",\"detail\":\"Chatbot service unavailable\"}\n\n")))
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Guest-Session-Id", required = false) String guestSession
    ) {
        scopeContext(request, guestSession);
        StreamingResponseBody responseBody = outputStream -> chatbotService.streamChat(request, outputStream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(responseBody);
    }

    @Operation(
            summary = "Kiểm tra phân tích ý định người dùng (NLU / Understanding)",
            description = "Chỉ ADMIN. Proxy yêu cầu phân tích message/current_state tới dịch vụ AI; data là JSON động theo phản hồi upstream."
    )
    @PostMapping("/test/understand")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> testUnderstand(
            @Valid @RequestBody UnderstandTestRequest request
    ) {
        Object result = chatbotService.testUnderstand(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Phân tích hiểu ý định thành công"));
    }

    @Operation(
            summary = "Kích hoạt đồng bộ dữ liệu từ MySQL vào kho vector RAG",
            description = "Chỉ ADMIN. Gọi thao tác đồng bộ database của dịch vụ AI; backend chuyển tiếp limit và trả data JSON động theo kết quả upstream."
    )
    @PostMapping("/sync/database")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncDatabase(
            @Parameter(description = "Giới hạn chuyển tới dịch vụ AI; bỏ trống dùng chính sách mặc định của dịch vụ AI", example = "50")
            @RequestParam(required = false) Integer limit
    ) {
        Map<String, Object> result = chatbotService.syncDatabase(limit);
        return ResponseEntity.ok(ApiResponse.success(result, "Đồng bộ dữ liệu sản phẩm vào RAG thành công"));
    }

    @Operation(
            summary = "Kiểm tra tình trạng hoạt động của dịch vụ Chatbot AI",
            description = "API công khai. HTTP 200 với ApiResponse; khi không kết nối được AI, data.status=DOWN. Kiểm tra trường status trong data thay vì chỉ mã HTTP."
    )
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> status = chatbotService.checkHealth();
        return ResponseEntity.ok(ApiResponse.success(status, "Kiểm tra sức khỏe dịch vụ Chatbot AI"));
    }

    @Operation(
            summary = "Tải đặc tả kỹ thuật OpenAPI JSON gốc của Chatbot",
            description = "API công khai. Trả trực tiếp JSON OpenAPI của dịch vụ AI, không bọc ApiResponse; khác với đặc tả /v3/api-docs của backend."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đặc tả JSON gốc", content = @Content(mediaType = "application/json", schema = @Schema(type = "object")))
    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOpenApiJson() {
        String json = chatbotService.getOpenApiJson();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }
}
