package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductImageRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse;
import com.core.beautyshop.modules.catalog.domain.Category;
import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductImage;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.catalog.domain.ProductImageRepository;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final ImageSearchClient imageSearchClient;

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImagesByProductId(Long productId) {
        productRepository.findByIdAndStatusAndIsDeletedFalse(productId, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId));
        return imageRepository.findByProductIdOrderByDisplayOrderAsc(productId).stream()
                .filter(image -> !Boolean.TRUE.equals(image.getIsDeleted()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public ProductImageResponse addImage(Long productId, ProductImageRequest request) {
        Product product = productRepository.findByIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId));

        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(request.getImageUrl())
                .altText(request.getAltText())
                .isPrimary(request.getIsPrimary() != null ? request.getIsPrimary() : false)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        image = imageRepository.save(image);
        final Long prodId = productId;
        final Long imgId = image.getId();
        final String imgUrl = image.getImageUrl();
        final String prodName = product.getName();
        final Long brandId = product.getBrand() != null ? product.getBrand().getId() : null;
        final List<Long> categoryIds = product.getCategories() != null
                ? product.getCategories().stream().map(Category::getId).toList()
                : null;
        executeAfterCommit(() -> {
            if (imageSearchClient == null) return;
            try {
                imageSearchClient.syncImage(prodId, imgId, imgUrl, "UPSERT", prodName, false, categoryIds, brandId);
            } catch (Exception ignored) { }
        });
        return mapToResponse(image);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void deleteImage(Long imageId) {
        deleteImage(null, imageId);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hình ảnh với id: " + imageId));
        if (productId != null && !productId.equals(image.getProduct().getId())) {
            throw new ResourceNotFoundException("Hình ảnh không thuộc sản phẩm với id: " + productId);
        }
        Long prodId = image.getProduct() != null ? image.getProduct().getId() : productId;
        imageRepository.delete(image);
        final Long targetProdId = prodId;
        final Long targetImageId = imageId;
        executeAfterCommit(() -> {
            if (imageSearchClient == null) return;
            try {
                imageSearchClient.syncImage(targetProdId, targetImageId, null, "DELETE", null, false);
            } catch (Exception ignored) { }
        });
    }

    private void executeAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private ProductImageResponse mapToResponse(ProductImage image) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .productId(image.getProduct().getId())
                .imageUrl(image.getImageUrl())
                .altText(image.getAltText())
                .isPrimary(image.getIsPrimary())
                .displayOrder(image.getDisplayOrder())
                .createdAt(image.getCreatedAt())
                .updatedAt(image.getUpdatedAt())
                .build();
    }
}
