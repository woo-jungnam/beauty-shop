package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.*;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductTagRepository productTagRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "product_detail", key = "'id:' + #id")
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy sản phẩm với ID: " + id));
        return mapToProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "product_detail", key = "'slug:' + #slug")
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndIsDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với slug: " + slug));
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
    public Page<ProductListResponse> searchProducts(String keyword, Pageable pageable) {
        return productRepository.searchProductList(keyword, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findProductListByCategoryId(categoryId, pageable);
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
                .basePrice(request.getBasePrice())
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE)
                .productType(request.getProductType() != null ? request.getProductType() : com.core.beautyshop.modules.catalog.domain.enums.ProductType.PRODUCT)
                .targetGender(request.getTargetGender())
                .skinType(request.getSkinType())
                .ingredients(request.getIngredients())
                .howToUse(request.getHowToUse())
                .originCountry(request.getOriginCountry())
                .volume(request.getVolume())
                .isFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false)
                .build();

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findByIdAndIsDeletedFalse(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với id: " + request.getBrandId()));
            product.setBrand(brand);
        }

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
            product.setCategories(categories);
        }

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<ProductTag> tags = productTagRepository.findAllById(request.getTagIds());
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
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));

        if (request.getName() != null) product.setName(request.getName());
        if (request.getSlug() != null) {
            if (!product.getSlug().equals(request.getSlug()) && productRepository.existsBySlug(request.getSlug())) {
                throw new BusinessException("Slug sản phẩm đã tồn tại: " + request.getSlug());
            }
            product.setSlug(request.getSlug());
        }
        if (request.getShortDescription() != null) product.setShortDescription(request.getShortDescription());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getThumbnailUrl() != null) product.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getBasePrice() != null) product.setBasePrice(request.getBasePrice());
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        if (request.getProductType() != null) product.setProductType(request.getProductType());
        if (request.getTargetGender() != null) product.setTargetGender(request.getTargetGender());
        if (request.getSkinType() != null) product.setSkinType(request.getSkinType());
        if (request.getIngredients() != null) product.setIngredients(request.getIngredients());
        if (request.getHowToUse() != null) product.setHowToUse(request.getHowToUse());
        if (request.getOriginCountry() != null) product.setOriginCountry(request.getOriginCountry());
        if (request.getVolume() != null) product.setVolume(request.getVolume());
        if (request.getIsFeatured() != null) product.setIsFeatured(request.getIsFeatured());

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findByIdAndIsDeletedFalse(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với id: " + request.getBrandId()));
            product.setBrand(brand);
        }

        if (request.getCategoryIds() != null) {
            List<Category> categories = categoryRepository.findAllById(request.getCategoryIds());
            product.setCategories(categories);
        }

        if (request.getTagIds() != null) {
            List<ProductTag> tags = productTagRepository.findAllById(request.getTagIds());
            product.setTags(tags);
        }

        Product saved = productRepository.save(product);
        return mapToProductResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));
        product.setIsDeleted(true);
        productRepository.save(product);
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .thumbnailUrl(product.getThumbnailUrl())
                .basePrice(product.getBasePrice())
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
                .brand(product.getBrand() != null ? BrandResponse.builder()
                        .id(product.getBrand().getId())
                        .name(product.getBrand().getName())
                        .slug(product.getBrand().getSlug())
                        .logoUrl(product.getBrand().getLogoUrl())
                        .build() : null)
                .categories(product.getCategories() != null ? product.getCategories().stream()
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
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>())
                .images(product.getImages() != null ? product.getImages().stream()
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
                .ingredientsList(product.getProductIngredients() != null ? product.getProductIngredients().stream()
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
                                .filter(pi -> Boolean.TRUE.equals(pi.getIsKeyActive()) && pi.getIngredient() != null)
                                .map(pi -> pi.getIngredient().getName())
                                .collect(Collectors.toList()) : new ArrayList<>())
                        .hydratingIngredients(product.getProductIngredients() != null ? product.getProductIngredients().stream()
                                .filter(pi -> pi.getIngredient() != null && pi.getIngredient().getFunctions() != null && pi.getIngredient().getFunctions().contains("humectant"))
                                .map(pi -> pi.getIngredient().getName())
                                .collect(Collectors.toList()) : new ArrayList<>())
                        .exfoliatingIngredients(product.getProductIngredients() != null ? product.getProductIngredients().stream()
                                .filter(pi -> pi.getIngredient() != null && pi.getIngredient().getFunctions() != null && pi.getIngredient().getFunctions().contains("exfoliant"))
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
}
