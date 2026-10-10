package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProductVisibilityIntegrationTest {
    @Autowired ProductService service;
    @Autowired ProductRepository products;

    @Test
    void inactiveProductRemainsEditableButPublicIdAndSlugAreHidden() {
        Product product = products.saveAndFlush(Product.builder().name("Private product")
                .slug("visibility-regression-" + UUID.randomUUID()).status(ProductStatus.INACTIVE).build());
        try {
            assertEquals(product.getId(), service.getProductByIdForAdmin(product.getId()).getId());
            assertThrows(ResourceNotFoundException.class, () -> service.getProductById(product.getId()));
            assertThrows(ResourceNotFoundException.class, () -> service.getProductBySlug(product.getSlug()));
        } finally {
            products.deleteById(product.getId());
        }
    }
}
