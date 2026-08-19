package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse getProductById(Long id);

    ProductResponse getProductBySlug(String slug);

    Page<ProductListResponse> getAllProducts(Pageable pageable);

    Page<ProductListResponse> searchProducts(String keyword, Pageable pageable);

    Page<ProductListResponse> getProductsByCategory(Long categoryId, Pageable pageable);

    Page<ProductListResponse> getProductsByBrand(Long brandId, Pageable pageable);

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    void deleteProduct(Long id);
}
