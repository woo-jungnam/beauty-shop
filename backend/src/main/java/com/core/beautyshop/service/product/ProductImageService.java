package com.core.beautyshop.service.product;

import com.core.beautyshop.dto.request.ProductImageRequest;
import com.core.beautyshop.dto.response.ProductImageResponse;

import java.util.List;

public interface ProductImageService {
    List<ProductImageResponse> getImagesByProductId(Long productId);
    ProductImageResponse addImage(Long productId, ProductImageRequest request);
    void deleteImage(Long imageId);
}