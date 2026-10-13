package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductVariantRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateCategoryRequest;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CatalogMutationConcurrencyIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired CategoryService categoryService;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductVariantService variantService;

    @Test
    void concurrentReverseParentChangesCannotCreateACycle() throws Exception {
        Category a = categories.saveAndFlush(Category.builder().name("A").slug("tree-a-" + UUID.randomUUID()).build());
        Category b = categories.saveAndFlush(Category.builder().name("B").slug("tree-b-" + UUID.randomUUID()).build());
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return reparent(a.getId(), b.getId()); });
            var second = executor.submit(() -> { start.await(); return reparent(b.getId(), a.getId()); });
            start.countDown();
            int accepted = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, accepted);
        } finally {
            Category freshA = categories.findById(a.getId()).orElseThrow();
            Category freshB = categories.findById(b.getId()).orElseThrow();
            freshA.setParentCategory(null);
            freshB.setParentCategory(null);
            categories.saveAllAndFlush(List.of(freshA, freshB));
            categories.deleteAllById(List.of(a.getId(), b.getId()));
        }
    }

    @Test
    void priceEditCannotRestoreAnOldDefaultWhileAnotherVariantBecomesDefault() throws Exception {
        Product product = products.saveAndFlush(Product.builder().name("Default concurrency")
                .slug("variant-default-" + UUID.randomUUID()).build());
        ProductVariant a = variants.saveAndFlush(ProductVariant.builder().product(product).sku("A-" + UUID.randomUUID())
                .variantName("A").price(new BigDecimal("100")).isDefault(true).build());
        ProductVariant b = variants.saveAndFlush(ProductVariant.builder().product(product).sku("B-" + UUID.randomUUID())
                .variantName("B").price(new BigDecimal("100")).isDefault(false).build());
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return variantService.updateVariant(a.getId(), request(a, null)); });
            var second = executor.submit(() -> { start.await(); return variantService.updateVariant(b.getId(), request(b, true)); });
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            assertFalse(variants.findById(a.getId()).orElseThrow().getIsDefault());
            assertTrue(variants.findById(b.getId()).orElseThrow().getIsDefault());
        } finally {
            products.deleteById(product.getId());
        }
    }

    private boolean reparent(Long child, Long parent) {
        try {
            categoryService.updateCategory(child, UpdateCategoryRequest.builder().parentId(parent).build());
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private ProductVariantRequest request(ProductVariant row, Boolean defaultVariant) {
        ProductVariantRequest request = new ProductVariantRequest();
        request.setSku(row.getSku());
        request.setVariantName(row.getVariantName());
        request.setPrice(new BigDecimal("120"));
        request.setIsDefault(defaultVariant);
        return request;
    }
}
