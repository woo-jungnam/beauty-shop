package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest;
import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.domain.ProductVariant;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {

    @Mock private ProductVariantRepository variantRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private ProductVariantServiceImpl variantService;

    @Test
    void addDefaultVariantClearsExistingDefault() {
        Product product = Product.builder().name("Product").slug("product").build();
        product.setId(10L);
        ProductVariantRequest request = validRequest();
        request.setIsDefault(true);

        when(productRepository.findByIdForUpdateAndIsDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(variantRepository.existsBySku("SKU-1")).thenReturn(false);
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            variant.setId(20L);
            return variant;
        });

        var response = variantService.addVariant(10L, request);

        assertTrue(response.getIsDefault());
        verify(variantRepository).clearDefaultsForProduct(10L);
    }

    @Test
    void updateDefaultVariantClearsOtherDefaults() {
        Product product = Product.builder().name("Product").slug("product").build();
        product.setId(10L);
        ProductVariant variant = ProductVariant.builder()
                .product(product).sku("SKU-1").variantName("Default")
                .price(new BigDecimal("100.00")).isDefault(false).build();
        variant.setId(20L);
        ProductVariantRequest request = validRequest();
        request.setIsDefault(true);

        when(variantRepository.findByIdAndIsDeletedFalse(20L)).thenReturn(Optional.of(variant));
        when(productRepository.findByIdForUpdateAndIsDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(variantRepository.save(variant)).thenReturn(variant);

        variantService.updateVariant(20L, request);

        verify(variantRepository).clearOtherDefaultsForProduct(10L, 20L);
        assertTrue(variant.getIsDefault());
    }

    @Test
    void rejectsDiscountPriceAboveOriginalPrice() {
        ProductVariantRequest request = validRequest();
        request.setDiscountPrice(new BigDecimal("101.00"));

        assertThrows(BusinessException.class, () -> variantService.addVariant(10L, request));
        verifyNoInteractions(productRepository);
        verify(variantRepository, never()).save(any());
    }

    private ProductVariantRequest validRequest() {
        ProductVariantRequest request = new ProductVariantRequest();
        request.setSku("SKU-1");
        request.setVariantName("Default");
        request.setPrice(new BigDecimal("100.00"));
        request.setDiscountPrice(new BigDecimal("90.00"));
        return request;
    }
}
