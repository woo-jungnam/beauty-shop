package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductImageRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductImageResponse;

import java.util.List;

public interface ProductImageService {
    List<ProductImageResponse> getImagesByProductId(Long productId);
    ProductImageResponse addImage(Long productId, ProductImageRequest request);
    void deleteImage(Long imageId);
}
