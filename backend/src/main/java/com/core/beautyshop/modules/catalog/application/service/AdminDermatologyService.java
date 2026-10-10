package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductSkinCompatibilityItemRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.ProductSkinConcernItemRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductUsageRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDermatologyService {

    private final ProductRepository productRepository;
    private final SkinTypeEntityRepository skinTypeRepository;
    private final SkinConcernRepository skinConcernRepository;
    private final ProductUsageDetailRepository usageDetailRepository;
    private final ProductSkinCompatibilityRepository compatibilityRepository;
    private final ProductSkinConcernRepository concernRepository;

    @Transactional(readOnly = true)
    public List<SkinTypeEntity> getAllSkinTypes() {
        return skinTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SkinConcern> getAllSkinConcerns() {
        return skinConcernRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ProductDermatologyProfileView getProfile(Long productId) {
        Product product = productRepository.findByIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId));

        ProductUsageDetail usage = usageDetailRepository.findByProductId(productId).orElse(null);
        List<ProductSkinCompatibility> compatibilities = compatibilityRepository.findByProductId(productId);
        List<ProductSkinConcern> concerns = concernRepository.findByProductId(productId);

        return ProductDermatologyProfileView.builder()
                .productId(product.getId())
                .productName(product.getName())
                .hasFragrance(Boolean.TRUE.equals(product.getHasFragrance()))
                .hasAlcohol(Boolean.TRUE.equals(product.getHasAlcohol()))
                .keyActivesSummary(product.getKeyActivesSummary())
                .usage(usage != null ? ProductResponse.UsageDetailResponse.builder()
                        .whenToUse(usage.getWhenToUse())
                        .frequency(usage.getFrequency())
                        .instructions(usage.getInstructions())
                        .warnings(usage.getWarnings())
                        .build() : null)
                .compatibilities(compatibilities.stream().map(c -> SkinCompatibilityItemView.builder()
                        .skinTypeId(c.getSkinType().getId())
                        .skinTypeCode(c.getSkinType().getCode())
                        .skinTypeName(c.getSkinType().getName())
                        .isRecommended(c.getIsRecommended())
                        .score(c.getScore())
                        .contraindicationReason(c.getContraindicationReason())
                        .build()).toList())
                .concerns(concerns.stream().map(sc -> SkinConcernItemView.builder()
                        .concernId(sc.getConcern().getId())
                        .concernCode(sc.getConcern().getCode())
                        .concernName(sc.getConcern().getName())
                        .score(sc.getScore())
                        .notes(sc.getNotes())
                        .build()).toList())
                .build();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public ProductResponse.UsageDetailResponse updateUsageDetail(Long productId, UpdateProductUsageRequest request) {
        Product product = productRepository.findByIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId));

        ProductUsageDetail detail = usageDetailRepository.findByProductId(productId)
                .orElseGet(() -> ProductUsageDetail.builder().product(product).build());

        detail.setWhenToUse(request.getWhenToUse() != null ? new ArrayList<>(request.getWhenToUse()) : new ArrayList<>());
        detail.setFrequency(request.getFrequency().trim());
        detail.setInstructions(request.getInstructions() != null ? new ArrayList<>(request.getInstructions()) : new ArrayList<>());
        detail.setWarnings(request.getWarnings() != null ? new ArrayList<>(request.getWarnings()) : new ArrayList<>());

        ProductUsageDetail saved = usageDetailRepository.save(detail);

        return ProductResponse.UsageDetailResponse.builder()
                .whenToUse(saved.getWhenToUse())
                .frequency(saved.getFrequency())
                .instructions(saved.getInstructions())
                .warnings(saved.getWarnings())
                .build();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public List<SkinCompatibilityItemView> updateSkinCompatibility(Long productId, List<ProductSkinCompatibilityItemRequest> items) {
        Product product = productRepository.findByIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId));

        List<ProductSkinCompatibility> existing = compatibilityRepository.findByProductId(productId);
        compatibilityRepository.deleteAll(existing);
        compatibilityRepository.flush();

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> seenIds = new HashSet<>();
        List<ProductSkinCompatibility> toSave = new ArrayList<>();

        for (ProductSkinCompatibilityItemRequest item : items) {
            if (!seenIds.add(item.getSkinTypeId())) {
                throw new BusinessException("Trùng lặp loại da trong danh sách tương thích: " + item.getSkinTypeId());
            }
            SkinTypeEntity skinType = skinTypeRepository.findById(item.getSkinTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Loại da không tồn tại: " + item.getSkinTypeId()));

            BigDecimal score = item.getScore() != null ? item.getScore() : new BigDecimal("0.80");

            toSave.add(ProductSkinCompatibility.builder()
                    .product(product)
                    .skinType(skinType)
                    .isRecommended(item.getIsRecommended() != null ? item.getIsRecommended() : true)
                    .score(score)
                    .contraindicationReason(item.getContraindicationReason())
                    .build());
        }

        List<ProductSkinCompatibility> saved = compatibilityRepository.saveAll(toSave);
        return saved.stream().map(c -> SkinCompatibilityItemView.builder()
                .skinTypeId(c.getSkinType().getId())
                .skinTypeCode(c.getSkinType().getCode())
                .skinTypeName(c.getSkinType().getName())
                .isRecommended(c.getIsRecommended())
                .score(c.getScore())
                .contraindicationReason(c.getContraindicationReason())
                .build()).toList();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public List<SkinConcernItemView> updateSkinConcerns(Long productId, List<ProductSkinConcernItemRequest> items) {
        Product product = productRepository.findByIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + productId));

        List<ProductSkinConcern> existing = concernRepository.findByProductId(productId);
        concernRepository.deleteAll(existing);
        concernRepository.flush();

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> seenIds = new HashSet<>();
        List<ProductSkinConcern> toSave = new ArrayList<>();

        for (ProductSkinConcernItemRequest item : items) {
            if (!seenIds.add(item.getConcernId())) {
                throw new BusinessException("Trùng lặp vấn đề da trong danh sách: " + item.getConcernId());
            }
            SkinConcern concern = skinConcernRepository.findById(item.getConcernId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vấn đề da không tồn tại: " + item.getConcernId()));

            BigDecimal score = item.getScore() != null ? item.getScore() : new BigDecimal("1.00");

            toSave.add(ProductSkinConcern.builder()
                    .product(product)
                    .concern(concern)
                    .score(score)
                    .notes(item.getNotes())
                    .build());
        }

        List<ProductSkinConcern> saved = concernRepository.saveAll(toSave);
        return saved.stream().map(sc -> SkinConcernItemView.builder()
                .concernId(sc.getConcern().getId())
                .concernCode(sc.getConcern().getCode())
                .concernName(sc.getConcern().getName())
                .score(sc.getScore())
                .notes(sc.getNotes())
                .build()).toList();
    }

    @Data
    @Builder
    @Schema(description = "Hồ sơ da liễu tổng hợp của sản phẩm")
    public static class ProductDermatologyProfileView {
        private Long productId;
        private String productName;
        private Boolean hasFragrance;
        private Boolean hasAlcohol;
        private String keyActivesSummary;
        private ProductResponse.UsageDetailResponse usage;
        private List<SkinCompatibilityItemView> compatibilities;
        private List<SkinConcernItemView> concerns;
    }

    @Data
    @Builder
    @Schema(description = "Thông tin tương thích một loại da")
    public static class SkinCompatibilityItemView {
        private Long skinTypeId;
        private String skinTypeCode;
        private String skinTypeName;
        private Boolean isRecommended;
        private BigDecimal score;
        private String contraindicationReason;
    }

    @Data
    @Builder
    @Schema(description = "Thông tin giải quyết một vấn đề da")
    public static class SkinConcernItemView {
        private Long concernId;
        private String concernCode;
        private String concernName;
        private BigDecimal score;
        private String notes;
    }
}
