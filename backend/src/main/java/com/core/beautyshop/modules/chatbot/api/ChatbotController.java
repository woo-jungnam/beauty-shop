package com.core.beautyshop.modules.chatbot.api;

import com.core.beautyshop.modules.chatbot.application.dto.request.ChatRequest;
import com.core.beautyshop.modules.chatbot.application.dto.request.UnderstandTestRequest;
import com.core.beautyshop.modules.chatbot.application.dto.response.ChatResponse;
import com.core.beautyshop.modules.chatbot.application.service.ChatbotService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

@Tag(name = "7. Trợ lý Chatbot AI", description = "Các API giao tiếp với trợ lý tư vấn mỹ phẩm & làm đẹp thông minh (AI RAG)")
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @Operation(
            summary = "Trò chuyện tư vấn sản phẩm (Non-streaming)",
            description = "Gửi tin nhắn của khách hàng tới trợ lý AI RAG đa tầng để nhận câu trả lời tư vấn chuyên sâu cùng danh sách sản phẩm và tài liệu trích dẫn phù hợp."
    )
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request
    ) {
        ChatResponse response = chatbotService.chat(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Nhận phản hồi tư vấn thành công"));
    }

    @Operation(
            summary = "Trò chuyện tư vấn sản phẩm (Streaming SSE)",
            description = "Hội thoại tư vấn sản phẩm truyền dòng Server-Sent Events (SSE) theo thời gian thực giúp hiển thị câu trả lời từng từ một mượt mà với độ trễ thấp."
    )
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(
            @Valid @RequestBody ChatRequest request
    ) {
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
            description = "Phân tích câu hỏi người dùng thành các khía cạnh: ý định (Intent), danh mục sản phẩm (Category), yêu cầu ràng buộc (Constraints) và sở thích (Preferences)."
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
            description = "Truy xuất danh mục sản phẩm từ cơ sở dữ liệu MySQL chính ở chế độ chỉ đọc và nạp chỉ mục vào hệ thống tìm kiếm vector RAG của Chatbot."
    )
    @PostMapping("/sync/database")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncDatabase(
            @Parameter(description = "Giới hạn số lượng sản phẩm cần đồng bộ (để trống nếu muốn đồng bộ toàn bộ)", example = "50")
            @RequestParam(required = false) Integer limit
    ) {
        Map<String, Object> result = chatbotService.syncDatabase(limit);
        return ResponseEntity.ok(ApiResponse.success(result, "Đồng bộ dữ liệu sản phẩm vào RAG thành công"));
    }

    @Operation(
            summary = "Kiểm tra tình trạng hoạt động của dịch vụ Chatbot AI",
            description = "Kiểm tra kết nối và trạng thái của server AI RAG Chatbot."
    )
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> status = chatbotService.checkHealth();
        return ResponseEntity.ok(ApiResponse.success(status, "Kiểm tra sức khỏe dịch vụ Chatbot AI"));
    }

    @Operation(
            summary = "Tải đặc tả kỹ thuật OpenAPI JSON gốc của Chatbot",
            description = "Lấy trực tiếp schema OpenAPI / Swagger dạng JSON gốc do FastAPI Chatbot tự động tạo ra."
    )
    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOpenApiJson() {
        String json = chatbotService.getOpenApiJson();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }
}
