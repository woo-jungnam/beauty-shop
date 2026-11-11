package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.domain.ProductSearchRepository;
import com.core.beautyshop.modules.catalog.domain.ProductImageRepository;
import com.core.beautyshop.modules.catalog.domain.ProductImage;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchClient;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchException;
import com.core.beautyshop.shared.time.BusinessTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductImageSearchService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final double MIN_SIMILARITY = 0.65;
    private final ImageSearchClient client;
    private final ProductSearchRepository products;
    private final ProductImageRepository images;

    public record Item(ProductListResponse product, boolean inStock, String detailPath, Double similarity) {
        public Item(ProductListResponse product, boolean inStock, String detailPath) {
            this(product, inStock, detailPath, null);
        }
    }
    public record SearchResult(String matchStatus, String indexVersion, List<Item> items) {}

    public SearchResult search(MultipartFile image) {
        return search(image, null, null, MIN_SIMILARITY);
    }

    public SearchResult search(MultipartFile image, Long categoryId, Long brandId, Double minSimilarity) {
        if (image == null || image.isEmpty()) throw new ImageSearchException(400, "Vui lòng chọn ảnh.");
        if (image.getSize() > MAX_BYTES) throw new ImageSearchException(413, "Ảnh tối đa 5 MiB.");
        byte[] raw;
        try (var stream = image.getInputStream()) {
            raw = stream.readNBytes(MAX_BYTES + 1);
        } catch (IOException error) {
            throw new ImageSearchException(400, "Không đọc được ảnh.");
        }
        if (raw.length > MAX_BYTES) throw new ImageSearchException(413, "Ảnh tối đa 5 MiB.");
        if (!supported(raw)) throw new ImageSearchException(415, "Chỉ hỗ trợ ảnh JPEG, PNG hoặc WebP.");
        double cutoff = (minSimilarity != null && minSimilarity >= 0.0 && minSimilarity <= 1.0) ? minSimilarity : MIN_SIMILARITY;
        return resolve(client.search(raw, categoryId, brandId, cutoff), cutoff);
    }

    public SearchResult resolve(ImageSearchClient.Result result) {
        return resolve(result, MIN_SIMILARITY);
    }

    public SearchResult resolve(ImageSearchClient.Result result, Double minSimilarity) {
        double cutoff = minSimilarity != null ? minSimilarity : MIN_SIMILARITY;
        var validCandidates = result.items().stream()
                .filter(c -> c.similarity() != null && c.similarity() >= cutoff)
                .toList();

        var ids = validCandidates.stream().map(ImageSearchClient.Candidate::productId).toList();
        if (ids.isEmpty()) return new SearchResult("EMPTY", result.indexVersion(), List.of());
        var available = products.findAvailableByIds(ids, BusinessTime.today()).stream()
                .collect(Collectors.toMap(ProductListResponse::getId, Function.identity(), (a, b) -> a));
        var photoIds = validCandidates.stream().map(ImageSearchClient.Candidate::imageId)
                .filter(id -> id > 0).toList();
        var photos = images.findAllById(photoIds).stream()
                .filter(image -> !Boolean.TRUE.equals(image.getIsDeleted()))
                .collect(Collectors.toMap(ProductImage::getId, Function.identity(), (a, b) -> a));
        var items = validCandidates.stream().map(candidate -> {
                    var product = available.get(candidate.productId());
                    if (product == null) return null;
                    String photoUrl = null;
                    if (candidate.imageId() == -candidate.productId()) {
                        photoUrl = product.getThumbnailUrl();
                    } else {
                        var photo = photos.get(candidate.imageId());
                        if (photo != null && photo.getProduct().getId().equals(product.getId())) photoUrl = photo.getImageUrl();
                    }
                    if (!isUploadUrl(photoUrl)) return null;
                    product.setThumbnailUrl(photoUrl);
                    return new Item(product, true, "product/" + product.getId(), candidate.similarity());
                }).filter(Objects::nonNull).limit(5)
                .toList();
        return new SearchResult(items.isEmpty() ? "EMPTY" : "CANDIDATES", result.indexVersion(), items);
    }

    private static boolean isUploadUrl(String url) {
        if (url == null || !url.startsWith("/uploads/") || url.contains("\\")) return false;
        try {
            var path = URI.create(url).getPath();
            return path != null && !path.contains("\\") && Arrays.stream(path.split("/"))
                    .noneMatch(part -> part.equals("..") || part.equals("."));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean supported(byte[] b) {
        return (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
                || (b.length >= 8 && b[0] == (byte) 137 && b[1] == 80 && b[2] == 78 && b[3] == 71
                    && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10)
                || (b.length >= 12 && b[0] == 82 && b[1] == 73 && b[2] == 70 && b[3] == 70
                    && b[8] == 87 && b[9] == 69 && b[10] == 66 && b[11] == 80);
    }
}
