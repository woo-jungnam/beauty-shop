package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductImageRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse;
import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductImage;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.catalog.domain.ProductImageRepository;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;

    @Override
    public List<ProductImageResponse> getImagesByProductId(Long productId) {
        return imageRepository.findByProductIdOrderByDisplayOrderAsc(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
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
        return mapToResponse(image);
    }

    @Override
    @Transactional
    public void deleteImage(Long imageId) {
        if (!imageRepository.existsById(imageId)) {
            throw new ResourceNotFoundException("Không tìm thấy hình ảnh với id: " + imageId);
        }
        imageRepository.deleteById(imageId);
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
