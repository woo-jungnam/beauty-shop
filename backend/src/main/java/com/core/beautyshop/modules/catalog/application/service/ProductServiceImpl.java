package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.*;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("không tìm thấy sản phẩm với ID: " + id));
        return mapToProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndIsDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với slug: " + slug));
        return mapToProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products_page", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    public Page<ProductListResponse> getAllProducts(Pageable pageable) {
        // Sử dụng DTO Projection: truy vấn trực tiếp ra DTO, không load full entity
        return productRepository.findAllProductList(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> searchProducts(String keyword, Pageable pageable) {
        // Sử dụng DTO Projection cho tìm kiếm
        return productRepository.searchProductList(keyword, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        // Sử dụng DTO Projection cho lọc theo danh mục
        return productRepository.findProductListByCategoryId(categoryId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductListResponse> getProductsByBrand(Long brandId, Pageable pageable) {
        // Sử dụng DTO Projection cho lọc theo thương hiệu
        return productRepository.findProductListByBrandId(brandId, pageable);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products_page", allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySlug(request.getSlug())) {
            throw new BusinessException("Slug sản phẩm đã tồn tại: " + request.getSlug());
        }

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

    @Override
    @Transactional
    @CacheEvict(value = "products_page", allEntries = true)
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
    @CacheEvict(value = "products_page", allEntries = true)
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
                        .collect(Collectors.toList()) : List.of())
                .variants(product.getVariants() != null ? product.getVariants().stream()
                        .map(v -> ProductResponse.VariantResponse.builder()
                                .id(v.getId())
                                .sku(v.getSku())
                                .variantName(v.getVariantName())
                                .price(v.getPrice())
                                .discountPrice(v.getDiscountPrice())
                                .volume(v.getVolume())
                                .color(v.getColor())
                                .barcode(v.getBarcode())
                                .isDefault(v.getIsDefault())
                                .isActive(v.getIsActive())
                                .build())
                        .collect(Collectors.toList()) : List.of())
                .images(product.getImages() != null ? product.getImages().stream()
                        .map(img -> ProductResponse.ImageResponse.builder()
                                .id(img.getId())
                                .imageUrl(img.getImageUrl())
                                .altText(img.getAltText())
                                .displayOrder(img.getDisplayOrder())
                                .isPrimary(img.getIsPrimary())
                                .build())
                        .collect(Collectors.toList()) : List.of())
                .tags(product.getTags() != null ? product.getTags().stream()
                        .map(t -> ProductResponse.TagResponse.builder()
                                .id(t.getId())
                                .name(t.getName())
                                .slug(t.getSlug())
                                .build())
                        .collect(Collectors.toList()) : List.of())
                .build();
    }
}
