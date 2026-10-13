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
import java.util.Map;
import com.core.beautyshop.shared.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;

@Component
@Slf4j
public class RecommendationClient {

    private final RestClient restClient;
    private final RestClient alternativeClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    @Value("${recommendation.internal-token:${RECOMMENDATION_INTERNAL_TOKEN:}}")
    private String internalToken;

    public record RecommendationResult(List<Long> ids, boolean available) {}

    private RecommendationResult result(String path, Long userId, String sessionId, int limit) {
        try {
            String json = executeGet(client -> client.get().uri(builder -> {
                var uri = builder.path(path).queryParam("limit", limit);
                if (userId != null) uri.queryParam("user_id", userId);
                if (sessionId != null && !sessionId.isBlank()) uri.queryParam("session_id", sessionId);
                return uri.build();
            }).header("X-Recommendation-Token", internalToken == null ? "" : internalToken).retrieve().body(String.class));
            if (json == null) return new RecommendationResult(List.of(), false);
            JsonNode root = objectMapper.readTree(json);
            if (!"success".equals(root.path("status").asText()) || !root.path("recommended_product_ids").isArray())
                return new RecommendationResult(List.of(), false);
            List<Long> ids = new ArrayList<>();
            for (JsonNode id : root.get("recommended_product_ids")) {
                if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0)
                    return new RecommendationResult(List.of(), false);
                if (!ids.contains(id.asLong())) ids.add(id.asLong());
            }
            return new RecommendationResult(ids, true);
        } catch (Exception error) {
            log.warn("Chưa đọc được phản hồi hệ thống gợi ý");
            return new RecommendationResult(List.of(), false);
        }
    }

    public RecommendationResult similar(Long productId, int limit) {
        return result("/recommend/similar/" + productId, null, null, limit);
    }

    public RecommendationResult forUser(Long userId, String sessionId, int limit) {
        return result("/recommend/for-you", userId, sessionId, limit);
    }

    private Map<String, Object> readMap(String json) {
        try {
            if (json == null) throw new IllegalArgumentException();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception error) {
            throw new BusinessException("Chưa kết nối được hệ thống gợi ý. Vui lòng thử lại");
        }
    }

    public Map<String, Object> status() {
        return readMap(executeGet(client -> client.get().uri("/recommend/admin/status")
                .header("X-Recommendation-Token", internalToken == null ? "" : internalToken).retrieve().body(String.class)));
    }

    public Map<String, Object> refresh() {
        return readMap(executeGet(client -> client.post().uri("/recommend/admin/refresh")
                .header("X-Recommendation-Token", internalToken == null ? "" : internalToken).retrieve().body(String.class)));
    }

    public Map<String, Object> preview(Long productId, Long userId, String sessionId, int limit) {
        String path = productId == null ? "/recommend/for-you" : "/recommend/similar/" + productId;
        return readMap(executeGet(client -> client.get().uri(builder -> {
            var uri = builder.path(path).queryParam("limit", limit);
            if (userId != null) uri.queryParam("user_id", userId);
            if (sessionId != null && !sessionId.isBlank()) uri.queryParam("session_id", sessionId);
            return uri.build();
        }).header("X-Recommendation-Token", internalToken == null ? "" : internalToken).retrieve().body(String.class)));
    }

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
        return similar(productId, limit).ids();
    }

    public List<Long> getRecommendedProductIdsForUser(Long userId, String sessionId, int limit) {
        return forUser(userId, sessionId, limit).ids();
    }
}
