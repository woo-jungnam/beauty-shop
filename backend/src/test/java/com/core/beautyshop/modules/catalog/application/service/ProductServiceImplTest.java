package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.domain.BrandRepository;
import com.core.beautyshop.modules.catalog.domain.CategoryRepository;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.domain.ProductTagRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductTagRepository productTagRepository;
    @InjectMocks private ProductServiceImpl productService;

    @Test
    void createProductRejectsMultipleDefaultVariants() {
        CreateProductRequest request = requestWithVariants(
                variant("SKU-1", "100.00", "90.00", true),
                variant("SKU-2", "120.00", "100.00", true));

        assertThrows(BusinessException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProductRejectsInvalidVariantDiscount() {
        CreateProductRequest request = requestWithVariants(
                variant("SKU-1", "100.00", "110.00", true));

        assertThrows(BusinessException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    private CreateProductRequest requestWithVariants(CreateProductRequest.VariantRequest... variants) {
        CreateProductRequest request = new CreateProductRequest();
        request.setSlug("product");
        request.setVariants(List.of(variants));
        return request;
    }

    private CreateProductRequest.VariantRequest variant(
            String sku, String price, String discountPrice, boolean isDefault) {
        return CreateProductRequest.VariantRequest.builder()
                .sku(sku)
                .variantName(sku)
                .price(new BigDecimal(price))
                .discountPrice(new BigDecimal(discountPrice))
                .isDefault(isDefault)
                .build();
    }
}
