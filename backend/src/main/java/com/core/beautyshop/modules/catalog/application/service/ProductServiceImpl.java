package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.domain.enums.ProductType;
import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.ProductSearchRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.*;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.modules.catalog.infrastructure.RecommendationClient;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.dto.CacheablePage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductTagRepository productTagRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryFacade inventoryFacade;
    private final RecommendationClient recommendationClient;
    private final UserProductInteractionRepository userProductInteractionRepository;

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndStatusAndIsDeletedFalse(id, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy sản phẩm với ID: " + id));
        return mapToProductResponse(product, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatusAndIsDeletedFalse(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với slug: " + slug));
        return mapToProductResponse(product, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductByIdForAdmin(Long id) {
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));
        return mapToProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    public Page<ProductListResponse> getAllProducts(Pageable pageable) {
        return CacheablePage.from(productRepository.findAllProductList(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> getFeaturedProducts(Pageable pageable) {
        Page<ProductListResponse> featured = productRepository.findFeaturedProductList(pageable);
        if (featured == null || featured.isEmpty()) {
            return productRepository.findAllProductList(pageable);
        }
        return featured;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> searchProducts(String keyword, Pageable pageable) {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword(keyword);
        return productSearchRepository.search(request, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> searchProducts(ProductSearchRequest request, Pageable pageable) {
        return productSearchRepository.search(request, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        Set<Long> categoryIds = collectCategoryAndDescendantIds(categoryId);
        return productRepository.findProductListByCategoryIds(categoryIds, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> getProductsByBrand(Long brandId, Pageable pageable) {
        return productRepository.findProductListByBrandId(brandId, pageable);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySlug(request.getSlug())) {
            throw new BusinessException("Slug sản phẩm đã tồn tại: " + request.getSlug());
        }

        validateVariants(request.getVariants());

        Product product = Product.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .shortDescription(request.getShortDescription())
                .description(request.getDescription())
                .thumbnailUrl(request.getThumbnailUrl())
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE)
                .productType(request.getProductType() != null ? request.getProductType() : ProductType.PRODUCT)
                .targetGender(request.getTargetGender())
                .skinType(request.getSkinType())
                .ingredients(request.getIngredients())
                .howToUse(request.getHowToUse())
                .originCountry(request.getOriginCountry())
                .volume(request.getVolume())
                .hasFragrance(Boolean.TRUE.equals(request.getHasFragrance()))
                .hasAlcohol(Boolean.TRUE.equals(request.getHasAlcohol()))
                .keyActivesSummary(request.getKeyActivesSummary())
                .isFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false)
                .build();

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findByIdAndIsDeletedFalse(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với id: " + request.getBrandId()));
            product.setBrand(brand);
        }

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            List<Category> categories = resolveCategories(request.getCategoryIds());
            product.setCategories(categories);
        }

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<ProductTag> tags = resolveTags(request.getTagIds());
            product.setTags(tags);
        }

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            List<ProductVariant> variants = request.getVariants().stream()
                    .map(v -> ProductVariant.builder()
                            .product(product)
                            .sku(v.getSku())
                            .variantName(v.getVariantName())
                            .price(v.getPrice())
                            .discountPrice(v.getDiscountPrice())
                            .volume(v.getVolume())
                            .color(v.getColor())
                            .barcode(v.getBarcode())
                            .isDefault(v.getIsDefault() != null ? v.getIsDefault() : false)
                            .build())
                    .collect(Collectors.toList());
            product.setVariants(variants);
        } else {
            product.setVariants(new ArrayList<>());
        }

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<ProductImage> images = request.getImages().stream()
                    .map(img -> ProductImage.builder()
                            .product(product)
                            .imageUrl(img.getImageUrl())
                            .altText(img.getAltText())
                            .displayOrder(img.getDisplayOrder() != null ? img.getDisplayOrder() : 0)
                            .isPrimary(img.getIsPrimary() != null ? img.getIsPrimary() : false)
                            .build())
                    .collect(Collectors.toList());
            product.setImages(images);
        } else {
            product.setImages(new ArrayList<>());
        }

        product.setAttributeValues(new ArrayList<>());
        Product saved = productRepository.save(product);
        return mapToProductResponse(saved);
    }

    private void validateVariants(List<CreateProductRequest.VariantRequest> variants) {
        if (variants == null || variants.isEmpty()) {
            return;
        }

        long defaultCount = variants.stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getIsDefault()))
                .count();
        if (defaultCount > 1) {
            throw new BusinessException("Mỗi sản phẩm chỉ được có một biến thể mặc định");
        }

        for (CreateProductRequest.VariantRequest variant : variants) {
            if (variant.getPrice() == null || variant.getPrice().signum() < 0) {
                throw new BusinessException("Giá biến thể phải lớn hơn hoặc bằng 0");
            }
            if (variant.getDiscountPrice() != null
                    && (variant.getDiscountPrice().signum() < 0
                    || variant.getDiscountPrice().compareTo(variant.getPrice()) > 0)) {
                throw new BusinessException("Giá khuyến mãi phải nằm trong khoảng từ 0 đến giá gốc");
            }
        }
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findByIdForUpdateAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));

        if (request.getName() != null) {
            if (request.getName().isBlank()) throw new BusinessException("Tên sản phẩm không được để trống");
            product.setName(request.getName());
        }
        if (request.getSlug() != null) {
            if (request.getSlug().isBlank()) throw new BusinessException("Slug sản phẩm không được để trống");
            if (!product.getSlug().equals(request.getSlug()) && productRepository.existsBySlug(request.getSlug())) {
                throw new BusinessException("Slug sản phẩm đã tồn tại: " + request.getSlug());
            }
            product.setSlug(request.getSlug());
        }
        if (request.getShortDescription() != null) product.setShortDescription(request.getShortDescription());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getThumbnailUrl() != null) {
            String thumb = request.getThumbnailUrl().trim();
            product.setThumbnailUrl(thumb.isEmpty() ? null : thumb);
        }
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        if (request.getProductType() != null) product.setProductType(request.getProductType());
        if (request.getTargetGender() != null) product.setTargetGender(request.getTargetGender());
        if (request.getSkinType() != null) product.setSkinType(request.getSkinType());
        if (request.getIngredients() != null) product.setIngredients(request.getIngredients());
        if (request.getHowToUse() != null) product.setHowToUse(request.getHowToUse());
        if (request.getOriginCountry() != null) product.setOriginCountry(request.getOriginCountry());
        if (request.getVolume() != null) {
            product.setVolume(request.getVolume());
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                ProductVariant def = product.getVariants().stream()
                        .filter(v -> Boolean.TRUE.equals(v.getIsDefault()) && !Boolean.TRUE.equals(v.getIsDeleted()))
                        .findFirst()
                        .orElse(product.getVariants().get(0));
                if (def != null && (def.getVolume() == null || def.getVolume().isBlank() || product.getVariants().size() == 1)) {
                    def.setVolume(request.getVolume().trim());
                }
            }
        }
        if (request.getHasFragrance() != null) product.setHasFragrance(request.getHasFragrance());
        if (request.getHasAlcohol() != null) product.setHasAlcohol(request.getHasAlcohol());
        if (request.getKeyActivesSummary() != null) product.setKeyActivesSummary(request.getKeyActivesSummary().trim());
        if (request.getIsFeatured() != null) product.setIsFeatured(request.getIsFeatured());

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findByIdAndIsDeletedFalse(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với id: " + request.getBrandId()));
            product.setBrand(brand);
        }

        if (request.getCategoryIds() != null) {
            List<Category> categories = resolveCategories(request.getCategoryIds());
            product.setCategories(categories);
        }

        if (request.getTagIds() != null) {
            List<ProductTag> tags = resolveTags(request.getTagIds());
            product.setTags(tags);
        }

        Product saved = productRepository.saveAndFlush(product);
        return mapToProductResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdForUpdateAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));
        product.setIsDeleted(true);
        productRepository.save(product);
    }

    private ProductResponse mapToProductResponse(Product product) {
        return mapToProductResponse(product, true);
    }

    private ProductResponse mapToProductResponse(Product product, boolean admin) {
        List<ProductVariant> activeVariants = product.getVariants() != null
                ? product.getVariants().stream().filter(v -> !Boolean.TRUE.equals(v.getIsDeleted()) && !Boolean.FALSE.equals(v.getIsActive())).toList()
                : List.of();
        BigDecimal minPrice = activeVariants.stream()
                .map(v -> v.getDiscountPrice() != null ? v.getDiscountPrice() : v.getPrice())
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal maxPrice = activeVariants.stream()
                .map(v -> v.getDiscountPrice() != null ? v.getDiscountPrice() : v.getPrice())
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(minPrice);

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .thumbnailUrl(product.getThumbnailUrl())
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .status(product.getStatus())
                .productType(product.getProductType())
                .targetGender(product.getTargetGender())
                .skinType(product.getSkinType())
                .ingredients(product.getIngredients())
                .howToUse(product.getHowToUse())
                .originCountry(product.getOriginCountry())
                .volume(product.getVolume())
                .isFeatured(product.getIsFeatured())
                .averageRating(product.getAverageRating())
                .totalReviews(product.getTotalReviews())
                .totalSold(product.getTotalSold())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .brand(product.getBrand() != null && !Boolean.TRUE.equals(product.getBrand().getIsDeleted()) ? BrandResponse.builder()
                        .id(product.getBrand().getId())
                        .name(product.getBrand().getName())
                        .slug(product.getBrand().getSlug())
                        .logoUrl(product.getBrand().getLogoUrl())
                        .build() : null)
                .categories(product.getCategories() != null ? product.getCategories().stream()
                        .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                        .filter(c -> admin || Boolean.TRUE.equals(c.getIsActive()))
                        .map(c -> CategoryResponse.builder()
                                .id(c.getId())
                                .name(c.getName())
                                .slug(c.getSlug())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>())
                .hasFragrance(product.getHasFragrance())
                .hasAlcohol(product.getHasAlcohol())
                .keyActivesSummary(product.getKeyActivesSummary())
                .variants(product.getVariants() != null ? product.getVariants().stream()
                        .filter(v -> !Boolean.TRUE.equals(v.getIsDeleted()))
                        .filter(v -> admin || Boolean.TRUE.equals(v.getIsActive()))
                        .sorted(Comparator.comparing((ProductVariant v) -> Boolean.TRUE.equals(v.getIsDefault()) ? 0 : 1)
                                .thenComparing(v -> v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO))
                        .map(v -> ProductResponse.VariantResponse.builder()
                                .id(v.getId())
                                .sku(v.getSku())
                                .variantName(v.getVariantName())
                                .price(v.getPrice())
                                .originalPrice(v.getOriginalPrice() != null ? v.getOriginalPrice() : v.getPrice())
                                .discountPrice(v.getDiscountPrice())
                                .currency(v.getCurrency() != null ? v.getCurrency() : "VND")
                                .volume(v.getVolume())
                                .volumeValue(v.getVolumeValue())
                                .volumeUnit(v.getVolumeUnit())
                                .color(v.getColor())
                                .barcode(v.getBarcode())
                                .isDefault(v.getIsDefault())
                                .isActive(v.getIsActive())
                                .stockQuantity(inventoryFacade.getAvailableQuantity(v.getId()))
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>())
                .images(product.getImages() != null ? product.getImages().stream()
                        .filter(img -> !Boolean.TRUE.equals(img.getIsDeleted()))
                        .map(img -> ProductResponse.ImageResponse.builder()
                                .id(img.getId())
                                .imageUrl(img.getImageUrl())
                                .altText(img.getAltText())
                                .imageType(img.getImageType())
                                .displayOrder(img.getDisplayOrder())
                                .isPrimary(img.getIsPrimary())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>())
                .tags(product.getTags() != null ? product.getTags().stream()
                        .map(t -> ProductResponse.TagResponse.builder()
                                .id(t.getId())
                                .name(t.getName())
                                .slug(t.getSlug())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>())
                .attributeValues(product.getAttributeValues() == null ? List.of() : product.getAttributeValues().stream()
                        .filter(value -> !Boolean.TRUE.equals(value.getIsDeleted())
                                && !Boolean.TRUE.equals(value.getAttributeDefinition().getIsDeleted())
                                && (value.getProductVariant() == null || !Boolean.TRUE.equals(value.getProductVariant().getIsDeleted())))
                        .sorted(java.util.Comparator.comparing(com.core.beautyshop.modules.catalog.domain.ProductAttributeValue::getId))
                        .map(ProductAttributeServiceImpl::mapToValueResponse).toList())
                .ingredientsList(product.getProductIngredients() != null ? product.getProductIngredients().stream()
                        .filter(pi -> pi.getIngredient() != null && !Boolean.TRUE.equals(pi.getIngredient().getIsDeleted()))
                        .sorted(java.util.Comparator.comparing(pi -> pi.getDisplayOrder(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                        .map(pi -> ProductResponse.IngredientDetailResponse.builder()
                                .ingredientId(pi.getIngredient() != null ? pi.getIngredient().getId() : null)
                                .name(pi.getIngredient() != null ? pi.getIngredient().getName() : null)
                                .inciName(pi.getIngredient() != null ? pi.getIngredient().getInciName() : null)
                                .concentration(pi.getConcentration())
                                .concentrationUnit(pi.getConcentrationUnit())
                                .isKeyActive(pi.getIsKeyActive())
                                .function(pi.getIngredient() != null && pi.getIngredient().getFunctions() != null
                                        ? new ArrayList<>(pi.getIngredient().getFunctions()) : new ArrayList<>())
                                .benefits(pi.getIngredient() != null && pi.getIngredient().getBenefits() != null
                                        ? new ArrayList<>(pi.getIngredient().getBenefits()) : new ArrayList<>())
                                .potentialConcerns(pi.getIngredient() != null && pi.getIngredient().getPotentialConcerns() != null
                                        ? new ArrayList<>(pi.getIngredient().getPotentialConcerns()) : new ArrayList<>())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>())
                .ingredientSummary(ProductResponse.IngredientSummaryResponse.builder()
                        .keyActives(product.getProductIngredients() != null ? product.getProductIngredients().stream()
                                .filter(pi -> Boolean.TRUE.equals(pi.getIsKeyActive()) && pi.getIngredient() != null && !Boolean.TRUE.equals(pi.getIngredient().getIsDeleted()))
                                .map(pi -> pi.getIngredient().getName())
                                .collect(Collectors.toList()) : new ArrayList<>())
                        .hydratingIngredients(product.getProductIngredients() != null ? product.getProductIngredients().stream()
                                .filter(pi -> pi.getIngredient() != null && !Boolean.TRUE.equals(pi.getIngredient().getIsDeleted()) && pi.getIngredient().getFunctions() != null && pi.getIngredient().getFunctions().contains("humectant"))
                                .map(pi -> pi.getIngredient().getName())
                                .collect(Collectors.toList()) : new ArrayList<>())
                        .exfoliatingIngredients(product.getProductIngredients() != null ? product.getProductIngredients().stream()
                                .filter(pi -> pi.getIngredient() != null && !Boolean.TRUE.equals(pi.getIngredient().getIsDeleted()) && pi.getIngredient().getFunctions() != null && pi.getIngredient().getFunctions().contains("exfoliant"))
                                .map(pi -> pi.getIngredient().getName())
                                .collect(Collectors.toList()) : new ArrayList<>())
                        .fragrance(Boolean.TRUE.equals(product.getHasFragrance()))
                        .alcohol(Boolean.TRUE.equals(product.getHasAlcohol()))
                        .build())
                .skinCompatibility(product.getSkinCompatibilities() != null ? ProductResponse.SkinCompatibilityResponse.builder()
                        .recommendedSkinTypes(product.getSkinCompatibilities().stream()
                                .filter(sc -> Boolean.TRUE.equals(sc.getIsRecommended()) && sc.getSkinType() != null)
                                .map(sc -> ProductResponse.RecommendedSkinType.builder()
                                        .skinTypeId(sc.getSkinType().getId())
                                        .code(sc.getSkinType().getCode())
                                        .name(sc.getSkinType().getName())
                                        .score(sc.getScore())
                                        .build())
                                .collect(Collectors.toList()))
                        .notIdealFor(product.getSkinCompatibilities().stream()
                                .filter(sc -> Boolean.FALSE.equals(sc.getIsRecommended()) && sc.getSkinType() != null)
                                .map(sc -> ProductResponse.NotIdealForSkinType.builder()
                                        .skinTypeId(sc.getSkinType().getId())
                                        .skinType(sc.getSkinType().getName())
                                        .reason(sc.getContraindicationReason())
                                        .build())
                                .collect(Collectors.toList()))
                        .build() : null)
                .skinConcerns(product.getSkinConcerns() != null ? product.getSkinConcerns().stream()
                        .map(psc -> ProductResponse.SkinConcernResponse.builder()
                                .concernId(psc.getConcern() != null ? psc.getConcern().getId() : null)
                                .code(psc.getConcern() != null ? psc.getConcern().getCode() : null)
                                .name(psc.getConcern() != null ? psc.getConcern().getName() : null)
                                .score(psc.getScore())
                                .notes(psc.getNotes())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>())
                .usage(product.getUsageDetail() != null ? ProductResponse.UsageDetailResponse.builder()
                        .whenToUse(product.getUsageDetail().getWhenToUse())
                        .frequency(product.getUsageDetail().getFrequency())
                        .instructions(product.getUsageDetail().getInstructions())
                        .warnings(product.getUsageDetail().getWarnings())
                        .build() : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> getAllProductsForAdmin(Pageable pageable) {
        return productRepository.findAllAdminProductList(pageable);
    }

    private List<Category> resolveCategories(List<Long> ids) {
        if (ids.stream().anyMatch(Objects::isNull)) throw new BusinessException("ID danh mục không được để trống");
        List<Long> requested = ids.stream().distinct().toList();
        List<Category> rows = categoryRepository.findAllById(requested);
        if (rows.size() != requested.size() || rows.stream().anyMatch(row -> Boolean.TRUE.equals(row.getIsDeleted()))) {
            throw new BusinessException("Danh mục không tồn tại hoặc đã bị xóa");
        }
        return rows;
    }

    private List<ProductTag> resolveTags(List<Long> ids) {
        if (ids.stream().anyMatch(Objects::isNull)) throw new BusinessException("ID thẻ không được để trống");
        List<Long> requested = ids.stream().distinct().toList();
        List<ProductTag> rows = productTagRepository.findAllById(requested);
        if (rows.size() != requested.size() || rows.stream().anyMatch(row -> Boolean.TRUE.equals(row.getIsDeleted()))) {
            throw new BusinessException("Thẻ không tồn tại hoặc đã bị xóa");
        }
        return rows;
    }

    private Set<Long> collectCategoryAndDescendantIds(Long rootId) {
        Set<Long> result = new LinkedHashSet<>();
        if (rootId == null) {
            return result;
        }
        result.add(rootId);
        List<CategoryResponse> allCategories = categoryRepository.findAllCategoryDtoList();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (CategoryResponse cat : allCategories) {
            if (cat.getParentId() != null) {
                childrenMap.computeIfAbsent(cat.getParentId(), k -> new ArrayList<>()).add(cat.getId());
            }
        }
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            List<Long> children = childrenMap.get(current);
            if (children != null) {
                for (Long childId : children) {
                    if (result.add(childId)) {
                        queue.add(childId);
                    }
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductListResponse> getSimilarProducts(Long productId, int limit) {
        int safeLimit = (limit <= 0 || limit > 50) ? 8 : limit;
        Product currentProduct = productRepository.findByIdAndIsDeletedFalse(productId).orElse(null);
        if (currentProduct == null) {
            return List.of();
        }

        // 1. Thử lấy danh sách ID từ FastAPI Content-Based Engine
        List<Long> similarIds = recommendationClient.getSimilarProductIds(productId, safeLimit);
        if (similarIds != null && !similarIds.isEmpty()) {
            List<ProductListResponse> products = productRepository.findProductListByIds(similarIds);
            Map<Long, ProductListResponse> map = products.stream()
                    .collect(Collectors.toMap(ProductListResponse::getId, p -> p, (a, b) -> a));
            List<ProductListResponse> ordered = similarIds.stream()
                    .map(map::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!ordered.isEmpty()) {
                return ordered;
            }
        }

        // 2. Fallback dựa trên Category hoặc Brand cùng loại trong MySQL
        List<Long> categoryIds = currentProduct.getCategories() != null
                ? currentProduct.getCategories().stream().map(Category::getId).toList()
                : List.of();
        Long brandId = currentProduct.getBrand() != null ? currentProduct.getBrand().getId() : null;

        return productRepository.findFallbackSimilarProducts(
                productId,
                categoryIds.isEmpty() ? List.of(-1L) : categoryIds,
                brandId != null ? brandId : -1L,
                org.springframework.data.domain.PageRequest.of(0, safeLimit)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductListResponse> getRecommendedForYou(Long userId, String sessionId, int limit) {
        int safeLimit = (limit <= 0 || limit > 50) ? 8 : limit;

        // 1. Thử lấy danh sách ID từ FastAPI RecSys (User-to-Item Centroid Vector)
        List<Long> recommendedIds = recommendationClient.getRecommendedProductIdsForUser(userId, sessionId, safeLimit);
        if (recommendedIds != null && !recommendedIds.isEmpty()) {
            List<ProductListResponse> products = productRepository.findProductListByIds(recommendedIds);
            Map<Long, ProductListResponse> map = products.stream()
                    .collect(Collectors.toMap(ProductListResponse::getId, p -> p, (a, b) -> a));
            List<ProductListResponse> ordered = recommendedIds.stream()
                    .map(map::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!ordered.isEmpty()) {
                return ordered;
            }
        }

        // 2. Fallback Cold-Start: Lấy danh sách sản phẩm phổ biến / bán chạy nhất
        return productRepository.findPopularProducts(org.springframework.data.domain.PageRequest.of(0, safeLimit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductListResponse> getExpiringSoonProducts(int thresholdDays, int limit) {
        int safeThreshold = Math.max(15, Math.min(thresholdDays, 180));
        int safeLimit = Math.max(1, Math.min(limit, 50));

        List<ExpiringVariantStockDto> expiringStocks = inventoryFacade.getExpiringVariantStocks(safeThreshold, safeLimit * 4);
        if (expiringStocks == null || expiringStocks.isEmpty()) {
            return List.of();
        }

        Map<Long, ExpiringVariantStockDto> stockMap = expiringStocks.stream()
                .filter(s -> s.getVariantId() != null && s.getEarliestExpirationDate() != null)
                .collect(Collectors.toMap(ExpiringVariantStockDto::getVariantId, s -> s, (s1, s2) -> s1));

        if (stockMap.isEmpty()) {
            return List.of();
        }

        List<ProductVariant> variants = productVariantRepository.findActiveVariantsWithProductAndBrandByIds(stockMap.keySet());
        if (variants.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductVariant> earliestVariantByProduct = new HashMap<>();
        Map<Long, ExpiringVariantStockDto> earliestStockByProduct = new HashMap<>();

        for (ProductVariant variant : variants) {
            Product product = variant.getProduct();
            if (product == null || product.getId() == null) continue;
            Long productId = product.getId();
            ExpiringVariantStockDto stock = stockMap.get(variant.getId());
            if (stock == null || stock.getEarliestExpirationDate() == null) continue;

            ExpiringVariantStockDto currentEarliest = earliestStockByProduct.get(productId);
            if (currentEarliest == null || stock.getEarliestExpirationDate().isBefore(currentEarliest.getEarliestExpirationDate())) {
                earliestVariantByProduct.put(productId, variant);
                earliestStockByProduct.put(productId, stock);
            }
        }

        LocalDate today = LocalDate.now();
        List<ProductListResponse> result = new ArrayList<>();

        for (Map.Entry<Long, ProductVariant> entry : earliestVariantByProduct.entrySet()) {
            ProductVariant variant = entry.getValue();
            Product product = variant.getProduct();
            ExpiringVariantStockDto stock = earliestStockByProduct.get(entry.getKey());

            BigDecimal salePrice = variant.getDiscountPrice() != null
                    ? variant.getDiscountPrice()
                    : (variant.getPrice() != null
                            ? variant.getPrice().multiply(new BigDecimal("0.80")).setScale(0, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
            BigDecimal originalPrice = variant.getOriginalPrice() != null
                    ? variant.getOriginalPrice()
                    : (variant.getPrice() != null ? variant.getPrice() : salePrice);

            int daysRemaining = (int) ChronoUnit.DAYS.between(today, stock.getEarliestExpirationDate());

            ProductListResponse response = ProductListResponse.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .slug(product.getSlug())
                    .shortDescription(product.getShortDescription())
                    .thumbnailUrl(product.getThumbnailUrl())
                    .minPrice(salePrice)
                    .maxPrice(originalPrice)
                    .status(product.getStatus())
                    .isFeatured(product.getIsFeatured())
                    .averageRating(product.getAverageRating())
                    .totalReviews(product.getTotalReviews())
                    .totalSold(product.getTotalSold())
                    .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                    .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                    .earliestExpirationDate(stock.getEarliestExpirationDate())
                    .daysRemaining(daysRemaining)
                    .clearanceStock(stock.getAvailableQuantity())
                    .build();

            result.add(response);
        }

        result.sort(Comparator.comparing(ProductListResponse::getEarliestExpirationDate)
                .thenComparing(Comparator.comparing(ProductListResponse::getTotalSold, Comparator.nullsLast(Comparator.reverseOrder()))));

        return result.stream().limit(safeLimit).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordInteraction(Long userId, String sessionId, Long productId, String actionType) {
        if (productId == null || actionType == null || actionType.isBlank()) {
            return;
        }
        try {
            UserProductInteraction interaction = UserProductInteraction.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .productId(productId)
                    .actionType(actionType.toUpperCase().trim())
                    .build();
            userProductInteractionRepository.save(interaction);
        } catch (Exception ignored) {
        }
    }
}
