package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductVariantRepositoryIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;

    @Test
    void customerQueriesExcludeVariantWhenParentProductIsSoftDeleted() {
        Product product = Product.builder()
                .name("Deleted product")
                .slug("deleted-product-repository-test")
                .status(ProductStatus.ACTIVE)
                .build();
        product = productRepository.saveAndFlush(product);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("ZOMBIE-SKU-REPOSITORY-TEST")
                .variantName("Default")
                .price(new BigDecimal("100000"))
                .isActive(true)
                .build();
        variant = variantRepository.saveAndFlush(variant);

        product.setIsDeleted(true);
        productRepository.saveAndFlush(product);

        assertTrue(variantRepository.findVariantSummaryByIdDto(variant.getId()).isEmpty());
        assertTrue(variantRepository.findVariantSummariesByIds(java.util.List.of(variant.getId())).isEmpty());
    }
}
