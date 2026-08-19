package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse;
import java.util.List;

public interface ProductVariantService {
    List<ProductVariantResponse> getVariantsByProductId(Long productId);
    ProductVariantResponse getVariantById(Long variantId);
    ProductVariantResponse addVariant(Long productId, ProductVariantRequest request);
    ProductVariantResponse updateVariant(Long variantId, ProductVariantRequest request);
    void deleteVariant(Long variantId);
}
