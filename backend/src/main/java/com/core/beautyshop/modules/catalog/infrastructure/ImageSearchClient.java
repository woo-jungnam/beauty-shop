package com.core.beautyshop.modules.catalog.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
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

@Component
public class ImageSearchClient {
    private final RestClient client;
    private final String token;
    private final boolean enabled;

    public record Candidate(@JsonProperty("product_id") Long productId,
                            @JsonProperty("image_id") Long imageId, Double similarity) {}
    public record Result(String status, @JsonProperty("match_status") String matchStatus,
                         @JsonProperty("index_version") String indexVersion, List<Candidate> items) {}

    public ImageSearchClient(@Value("${chatbot.service.url:http://localhost:8000}") String url,
                             @Value("${image-search.internal-token:}") String token,
                             @Value("${image-search.enabled:false}") boolean enabled,
                             @Value("${image-search.timeout-seconds:30}") int timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(Math.max(1, Math.min(timeout, 120)) * 1000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.token = token;
        this.enabled = enabled;
    }

    public Result search(byte[] image) {
        if (!enabled || token.isBlank()) throw unavailable();
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("image", new ByteArrayResource(image) {
            @Override public String getFilename() { return "query-image"; }
        });
        try {
            var result = client.post().uri("/image-search/search?limit=20")
                    .header("X-Image-Search-Token", token)
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(parts)
                    .retrieve().body(Result.class);
            return validate(result);
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            if (status == 400 || status == 413 || status == 415)
                throw new ImageSearchException(status, status == 413
                        ? "Ảnh tối đa 5 MiB." : "Ảnh không hợp lệ. Chỉ hỗ trợ JPEG, PNG hoặc WebP.");
            throw unavailable();
        } catch (ImageSearchException error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    static Result validate(Result result) {
        if (result == null || !"success".equals(result.status()) || !"CANDIDATES".equals(result.matchStatus())
                || result.indexVersion() == null || result.indexVersion().isBlank()
                || result.items() == null || result.items().size() > 20) throw unavailable();
        var seen = new HashSet<Long>();
        double previous = Double.POSITIVE_INFINITY;
        for (var candidate : result.items()) {
            if (candidate == null || candidate.productId() == null || candidate.productId() <= 0
                    || candidate.imageId() == null || candidate.imageId() == 0
                    || candidate.similarity() == null || !Double.isFinite(candidate.similarity())
                    || candidate.similarity() < -1 || candidate.similarity() > 1
                    || candidate.similarity() > previous || !seen.add(candidate.productId())) throw unavailable();
            previous = candidate.similarity();
        }
        return result;
    }

    private static ImageSearchException unavailable() {
        return new ImageSearchException(503, "Tìm kiếm bằng ảnh tạm thời chưa sẵn sàng. Vui lòng thử lại sau.");
    }
}
