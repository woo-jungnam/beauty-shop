package com.core.beautyshop.modules.catalog.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class RecommendationClient {

    private final RestClient restClient;
    private final RestClient alternativeClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public RecommendationClient(
            @Value("${chatbot.service.url:http://localhost:8000}") String chatbotBaseUrl,
            ObjectMapper objectMapper,
            @Value("${chatbot.service.timeout-seconds:10}") int timeoutSeconds
    ) {
        this.baseUrl = chatbotBaseUrl.endsWith("/") ? chatbotBaseUrl.substring(0, chatbotBaseUrl.length() - 1) : chatbotBaseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(Math.min(timeoutSeconds * 1000, 10000));
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(factory)
                .build();

        String altUrl = null;
        if (this.baseUrl.contains("localhost:8000") || this.baseUrl.contains("127.0.0.1:8000")) {
            altUrl = this.baseUrl.replace("8000", "18000");
        } else if (this.baseUrl.contains("localhost:18000") || this.baseUrl.contains("127.0.0.1:18000")) {
            altUrl = this.baseUrl.replace("18000", "8000");
        }
        if (altUrl != null) {
            this.alternativeClient = RestClient.builder()
                    .baseUrl(altUrl)
                    .requestFactory(factory)
                    .build();
        } else {
            this.alternativeClient = null;
        }
    }

    private String executeGet(java.util.function.Function<RestClient, String> caller) {
        try {
            return caller.apply(this.restClient);
        } catch (Exception e) {
            if (this.alternativeClient != null) {
                try {
                    log.debug("Primary RecSys endpoint thất bại, thử port thay thế: {}", e.getMessage());
                    return caller.apply(this.alternativeClient);
                } catch (Exception altEx) {
                    log.warn("Cả hai endpoint RecSys đều không phản hồi: {}", altEx.getMessage());
                }
            } else {
                log.warn("Không thể kết nối FastAPI RecSys: {}", e.getMessage());
            }
        }
        return null;
    }

    public List<Long> getSimilarProductIds(Long productId, int limit) {
        if (productId == null || productId <= 0) {
            return Collections.emptyList();
        }
        try {
            log.debug("Gọi FastAPI RecSys lấy similar products cho product_id={}, limit={}", productId, limit);
            String responseStr = executeGet(client -> client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/recommend/similar/{productId}")
                            .queryParam("limit", limit)
                            .build(productId))
                    .retrieve()
                    .body(String.class));

            if (responseStr == null || responseStr.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode idsNode = root.get("recommended_product_ids");
            if (idsNode != null && idsNode.isArray()) {
                List<Long> result = new ArrayList<>();
                for (JsonNode item : idsNode) {
                    result.add(item.asLong());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Lỗi phân tích JSON từ FastAPI RecSys /recommend/similar: {}. Sẽ sử dụng fallback.", e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<Long> getRecommendedProductIdsForUser(Long userId, String sessionId, int limit) {
        try {
            log.debug("Gọi FastAPI RecSys lấy recommendations cho userId={}, sessionId={}, limit={}", userId, sessionId, limit);
            String responseStr = executeGet(client -> client.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/recommend/for-you").queryParam("limit", limit);
                        if (userId != null) {
                            builder.queryParam("user_id", userId);
                        }
                        if (sessionId != null && !sessionId.isBlank()) {
                            builder.queryParam("session_id", sessionId);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(String.class));

            if (responseStr == null || responseStr.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode idsNode = root.get("recommended_product_ids");
            if (idsNode != null && idsNode.isArray()) {
                List<Long> result = new ArrayList<>();
                for (JsonNode item : idsNode) {
                    result.add(item.asLong());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Lỗi phân tích JSON từ FastAPI RecSys /recommend/for-you: {}. Sẽ sử dụng fallback.", e.getMessage());
        }
        return Collections.emptyList();
    }
}
