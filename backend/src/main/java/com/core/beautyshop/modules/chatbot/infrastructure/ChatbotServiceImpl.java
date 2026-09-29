package com.core.beautyshop.modules.chatbot.infrastructure;

import com.core.beautyshop.modules.chatbot.application.dto.request.ChatRequest;
import com.core.beautyshop.modules.chatbot.application.dto.request.UnderstandTestRequest;
import com.core.beautyshop.modules.chatbot.application.dto.response.ChatResponse;
import com.core.beautyshop.modules.chatbot.application.service.ChatbotService;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
@Service
@Slf4j
public class ChatbotServiceImpl implements ChatbotService {

    private final String chatbotBaseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final Semaphore streams;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("chat-deadlines").factory());

    @jakarta.annotation.PreDestroy
    public void shutdown() { deadlines.shutdownNow(); }

    public ChatbotServiceImpl(
            @Value("${chatbot.service.url:http://localhost:8000}") String chatbotBaseUrl,
            ObjectMapper objectMapper,
            @Value("${chatbot.service.timeout-seconds:60}") int timeoutSeconds,
            @Value("${chatbot.service.max-concurrent-streams:32}") int maxStreams
    ) {
        this.chatbotBaseUrl = chatbotBaseUrl.endsWith("/") ? chatbotBaseUrl.substring(0, chatbotBaseUrl.length() - 1) : chatbotBaseUrl;
        this.objectMapper = objectMapper;
        if (timeoutSeconds <= 0 || maxStreams <= 0) throw new IllegalArgumentException("Invalid chatbot limits");
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.streams = new java.util.concurrent.Semaphore(maxStreams);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.httpClient);
        factory.setReadTimeout(requestTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(this.chatbotBaseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            log.info("Chuyển tiếp yêu cầu chat (non-streaming) sang Chatbot AI [session_id: {}]", request.getSessionId());
            return restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (Exception e) {
            log.error("Lỗi khi gọi API chat của Chatbot AI: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHATBOT_SERVICE_UNAVAILABLE, "Không thể kết nối đến máy chủ Chatbot AI: ");
        }
    }

    @Override
    public void streamChat(ChatRequest request, OutputStream outputStream) {
        if (!streams.tryAcquire()) {
            throw new BusinessException(ErrorCode.CHATBOT_SERVICE_UNAVAILABLE, "Chat service is busy; please retry");
        }
        java.util.concurrent.ScheduledFuture<?> deadline = null;
        try {
            log.info("Khởi tạo luồng SSE stream sang Chatbot AI [session_id: {}]", request.getSessionId());
            String requestJson = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(chatbotBaseUrl + "/chat/stream"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            deadline = deadlines.schedule(() -> {
                try { response.body().close(); } catch (java.io.IOException ignored) { }
            }, requestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (response.statusCode() >= 400) {
                response.body().close();
                log.error("Chatbot stream trả về HTTP error code: {}", response.statusCode());
                String errorPayload = "data: {\"type\": \"error\", \"detail\": \"Chatbot service returned error: " + response.statusCode() + "\"}\n\n";
                outputStream.write(errorPayload.getBytes());
                outputStream.flush();
                return;
            }

            try (InputStream is = response.body()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            }
        } catch (Exception e) {
            log.error("Lỗi trong quá trình truyền dữ liệu luồng từ Chatbot AI: {}", e.getMessage(), e);
            try {
                String errorPayload = "data: {\"type\": \"error\", \"detail\": \"Lỗi kết nối streaming: " + "Chat service unavailable" + "\"}\n\n";
                outputStream.write(errorPayload.getBytes());
                outputStream.flush();
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo lỗi SSE tới client: {}", ex.getMessage());
            }
        } finally {
            if (deadline != null) deadline.cancel(false);
            streams.release();
        }
    }

    @Override
    public Map<String, Object> syncDatabase(Integer limit) {
        try {
            log.info("Kích hoạt đồng bộ MySQL sang kho RAG [limit: {}]", limit);
            return restClient.post()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/sync/database");
                        if (limit != null) {
                            uriBuilder.queryParam("limit", limit);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Lỗi khi gọi API đồng bộ database của Chatbot AI: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHATBOT_SERVICE_UNAVAILABLE, "Đồng bộ RAG thất bại: ");
        }
    }

    @Override
    public Object testUnderstand(UnderstandTestRequest request) {
        try {
            log.info("Gửi yêu cầu kiểm tra NLU hiểu truy vấn: {}", request.getMessage());
            return restClient.post()
                    .uri("/test/understand")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Lỗi khi gọi API kiểm tra hiểu truy vấn của Chatbot AI: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHATBOT_SERVICE_UNAVAILABLE, "Kiểm tra NLU thất bại: ");
        }
    }

    @Override
    public Map<String, Object> checkHealth() {
        try {
            return restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Chatbot AI healthcheck thất bại: {}", e.getMessage());
            return Map.of("status", "DOWN");
        }
    }

    @Override
    public String getOpenApiJson() {
        try {
            return restClient.get()
                    .uri("/openapi.json")
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Lỗi khi tải openapi.json từ Chatbot AI: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.CHATBOT_SERVICE_UNAVAILABLE, "Không thể tải OpenAPI spec của Chatbot: ");
        }
    }
}
