package com.core.beautyshop.service.product;

import com.core.beautyshop.dto.request.ProductVariantRequest;
import com.core.beautyshop.dto.response.ProductVariantResponse;
import java.util.List;

public interface ProductVariantService {
    List<ProductVariantResponse> getVariantsByProductId(Long productId);
    ProductVariantResponse getVariantById(Long variantId);
    ProductVariantResponse addVariant(Long productId, ProductVariantRequest request);
    ProductVariantResponse updateVariant(Long variantId, ProductVariantRequest request);
    void deleteVariant(Long variantId);
}