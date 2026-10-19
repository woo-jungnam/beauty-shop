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
    @Autowired com.core.beautyshop.modules.catalog.domain.ProductVariantRepository variants;
    @Autowired com.core.beautyshop.modules.inventory.domain.WarehouseRepository warehouses;
    @Autowired com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository stocks;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void stockEligibilityRestoresDynamicallyWithoutChangingAdminStateOrPrice() {
        String key = "expiry-" + UUID.randomUUID();
        var product = products.saveAndFlush(Product.builder().name(key).slug(key).build());
        var sku = variants.saveAndFlush(com.core.beautyshop.modules.catalog.domain.ProductVariant.builder().product(product)
                .sku(key).variantName("Original SKU").price(new java.math.BigDecimal("100000"))
                .discountPrice(new java.math.BigDecimal("80000")).build());
        product.setVariants(new java.util.ArrayList<>(java.util.List.of(sku)));
        var warehouse = warehouses.saveAndFlush(com.core.beautyshop.modules.inventory.domain.Warehouse.builder().name(key).code(key).build());
        var lot = stocks.saveAndFlush(com.core.beautyshop.modules.inventory.domain.WarehouseStock.builder().warehouse(warehouse)
                .productVariantId(sku.getId()).batchCode("old").quantity(5)
                .expirationDate(com.core.beautyshop.shared.time.BusinessTime.today().plusDays(30)).build());
        var request = new com.core.beautyshop.modules.catalog.application.dto.request.ProductSearchRequest(); request.setKeyword(key);
        var page = org.springframework.data.domain.PageRequest.of(0, 5);
        assertTrue(service.searchProducts(request, page).isEmpty());
        assertEquals(0, service.getProductById(product.getId()).getVariants().getFirst().getStockQuantity());
        assertTrue(sku.getIsActive()); assertFalse(lot.getIsBlocked()); assertEquals(5, lot.getQuantity());
        stocks.saveAndFlush(com.core.beautyshop.modules.inventory.domain.WarehouseStock.builder().warehouse(warehouse)
                .productVariantId(sku.getId()).batchCode("new").quantity(3)
                .expirationDate(com.core.beautyshop.shared.time.BusinessTime.today().plusDays(100)).build());
        assertEquals(1, service.searchProducts(request, page).getTotalElements());
        assertEquals(3, service.getProductById(product.getId()).getVariants().getFirst().getStockQuantity());
        assertEquals(new java.math.BigDecimal("80000"), sku.getDiscountPrice());
        sku.setIsActive(false); variants.flush();
        assertTrue(service.searchProducts(request, page).isEmpty());
        assertFalse(variants.findById(sku.getId()).orElseThrow().getIsActive());
        assertTrue(service.getProductById(product.getId()).getVariants().isEmpty());
        assertFalse(service.getProductByIdForAdmin(product.getId()).getVariants().getFirst().getIsActive());
    }

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
