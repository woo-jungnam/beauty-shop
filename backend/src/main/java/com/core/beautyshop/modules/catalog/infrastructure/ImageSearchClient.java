package com.core.beautyshop.modules.catalog.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ImageSearchClient {
    private final RestClient client;
    private final String token;
    private final boolean enabled;

    public record Candidate(@JsonProperty("product_id") Long productId,
                            @JsonProperty("image_id") Long imageId,
                            @JsonProperty("image_url") String imageUrl,
                            @JsonProperty("product_name") String productName,
                            @JsonProperty("category_ids") List<Long> categoryIds,
                            @JsonProperty("brand_id") Long brandId,
                            Double similarity) {
        public Candidate(Long productId, Long imageId, Double similarity) {
            this(productId, imageId, null, null, null, null, similarity);
        }
        public Candidate(Long productId, Long imageId, String imageUrl, String productName, Double similarity) {
            this(productId, imageId, imageUrl, productName, null, null, similarity);
        }
    }

    public record Result(String status, @JsonProperty("match_status") String matchStatus,
                         @JsonProperty("index_version") String indexVersion, List<Candidate> items) {}

    public ImageSearchClient(@Value("${image-search.service-url:http://image-search:8001}") String url,
                             @Value("${image-search.internal-token:}") String token,
                             @Value("${image-search.enabled:false}") boolean enabled,
                             @Value("${image-search.timeout-seconds:30}") int timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(Math.max(1, Math.min(timeout, 180)) * 1000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.token = token;
        this.enabled = enabled;
        log.info("ImageSearchClient khoi tao: url={}, enabled={}", url, enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Result search(byte[] image) {
        return search(image, null, null, 0.65);
    }

    public Result search(byte[] image, Long categoryId, Long brandId, Double minSimilarity) {
        if (!enabled || token.isBlank()) throw unavailable();
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("image", new ByteArrayResource(image) {
            @Override public String getFilename() { return "query-image"; }
        });
        try {
            var uriBuilder = new StringBuilder("/image-search/search?limit=20");
            if (minSimilarity != null) {
                uriBuilder.append("&min_similarity=").append(minSimilarity);
            }
            if (categoryId != null) {
                uriBuilder.append("&category_id=").append(categoryId);
            }
            if (brandId != null) {
                uriBuilder.append("&brand_id=").append(brandId);
            }
            var result = client.post().uri(uriBuilder.toString())
                    .header("X-Image-Search-Token", token)
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(parts)
                    .retrieve().body(Result.class);
            return validate(result);
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            if (status == 400 || status == 413 || status == 415)
                throw new ImageSearchException(status, status == 413
                        ? "Ảnh tối đa 5 MiB." : "Ảnh không hợp lệ. Chỉ hỗ trợ JPEG, PNG, WEBP, AVIF.");
            throw unavailable();
        } catch (ImageSearchException error) {
            throw error;
        } catch (Exception error) {
            log.error("Loi khi tim kiem anh sang Image Search Service: {}", error.getMessage());
            throw unavailable();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getStatus() {
        if (!enabled) {
            return Map.of("enabled", false, "ready", false, "total_vectors", 0,
                    "message", "Chưa bật tìm kiếm ảnh trong file .env (IMAGE_SEARCH_ENABLED=true)");
        }
        if (token.isBlank()) {
            return Map.of("enabled", true, "ready", false, "total_vectors", 0,
                    "message", "Chưa cấu hình khóa bí mật IMAGE_SEARCH_INTERNAL_TOKEN trong file .env");
        }
        try {
            return client.get().uri("/image-search/status")
                    .header("X-Image-Search-Token", token)
                    .retrieve().body(Map.class);
        } catch (Exception error) {
            log.warn("Khong the lay status Image Search: {}", error.getMessage());
            return Map.of("enabled", true, "ready", false, "total_vectors", 0,
                    "error", "Không thể kết nối đến dịch vụ AI (image-search:8001): " + error.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerReindex() {
        if (!enabled || token.isBlank()) throw unavailable();
        try {
            return client.post().uri("/image-search/admin/reindex-all")
                    .header("X-Image-Search-Token", token)
                    .retrieve().body(Map.class);
        } catch (Exception error) {
            log.error("Loi khi trigger reindex-all: {}", error.getMessage());
            throw new ImageSearchException(500, "Không thể bắt đầu đồng bộ toàn bộ ảnh: " + error.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getReindexStatus() {
        if (!enabled || token.isBlank()) throw unavailable();
        try {
            return client.get().uri("/image-search/admin/reindex-status")
                    .header("X-Image-Search-Token", token)
                    .retrieve().body(Map.class);
        } catch (Exception error) {
            log.error("Loi khi lay reindex-status: {}", error.getMessage());
            throw new ImageSearchException(500, "Không thể lấy tiến trình đồng bộ: " + error.getMessage());
        }
    }

    public void syncImage(Long productId, Long imageId, String imageUrl, String action, String productName, Boolean isThumbnail) {
        syncImage(productId, imageId, imageUrl, action, productName, isThumbnail, null, null);
    }

    public void syncImage(Long productId, Long imageId, String imageUrl, String action, String productName,
                          Boolean isThumbnail, List<Long> categoryIds, Long brandId) {
        if (!enabled || token.isBlank() || productId == null || imageId == null) return;
        try {
            if ("DELETE".equalsIgnoreCase(action)) {
                client.delete().uri("/image-search/sync/{productId}/{imageId}", productId, imageId)
                        .header("X-Image-Search-Token", token)
                        .retrieve().toBodilessEntity();
            } else {
                var body = new java.util.HashMap<String, Object>();
                body.put("product_id", productId);
                body.put("image_id", imageId);
                body.put("image_url", imageUrl != null ? imageUrl : "");
                body.put("product_status", "ACTIVE");
                body.put("is_thumbnail", Boolean.TRUE.equals(isThumbnail));
                body.put("product_name", productName != null ? productName : "");
                if (categoryIds != null) body.put("category_ids", categoryIds);
                if (brandId != null) body.put("brand_id", brandId);

                client.post().uri("/image-search/sync")
                        .header("X-Image-Search-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve().toBodilessEntity();
            }
        } catch (Exception error) {
            log.warn("Dong bo vector anh cho product {} image {} that bai (non-blocking): {}",
                    productId, imageId, error.getMessage());
        }
    }

    static Result validate(Result result) {
        if (result == null || !"success".equals(result.status()) || !"CANDIDATES".equals(result.matchStatus())
                || result.indexVersion() == null || result.indexVersion().isBlank()
                || result.items() == null || result.items().size() > 50) throw unavailable();
        var seen = new HashSet<Long>();
        double previous = Double.POSITIVE_INFINITY;
        for (var candidate : result.items()) {
            if (candidate == null || candidate.productId() == null || candidate.productId() <= 0
                    || candidate.imageId() == null || candidate.imageId() == 0
                    || candidate.similarity() == null || !Double.isFinite(candidate.similarity())
                    || candidate.similarity() < -1.01 || candidate.similarity() > 1.01
                    || candidate.similarity() > previous || !seen.add(candidate.productId())) throw unavailable();
            previous = candidate.similarity();
        }
        return result;
    }

    private static ImageSearchException unavailable() {
        return new ImageSearchException(503, "Tìm kiếm bằng ảnh tạm thời chưa sẵn sàng. Vui lòng thử lại sau.");
    }
}
