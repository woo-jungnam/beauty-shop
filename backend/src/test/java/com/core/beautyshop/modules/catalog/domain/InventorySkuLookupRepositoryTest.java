package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class InventorySkuLookupRepositoryTest {
    @Autowired ProductVariantRepository variants;
    @Autowired EntityManager entities;

    @Test void excludesDeletedProductsAndVariantsButIncludesInactiveInventory() {
        Product product = Product.builder().name("Inventory").slug("lookup-live").build();
        product.setStatus(ProductStatus.DISCONTINUED);
        timestamps(product); entities.persist(product);
        Product deletedProduct = Product.builder().name("Deleted").slug("lookup-deleted").build();
        deletedProduct.setIsDeleted(true); timestamps(deletedProduct); entities.persist(deletedProduct);
        ProductVariant inactive = save(product, "000LIVE", false);
        ProductVariant deletedVariant = save(product, "DELETED-V", true); deletedVariant.setIsDeleted(true);
        save(deletedProduct, "DELETED-P", true);
        entities.flush(); entities.clear();
        var result = variants.findVariantSummariesForInventoryBySkus(List.of("000LIVE", "DELETED-V", "DELETED-P", "MISSING"));
        assertEquals(1, result.size());
        assertEquals(inactive.getId(), result.getFirst().getId());
        assertFalse(result.getFirst().getIsActive());
    }

    private ProductVariant save(Product product, String sku, boolean active) {
        ProductVariant variant = ProductVariant.builder().product(product).sku(sku).variantName(sku).price(BigDecimal.TEN).isActive(active).build();
        timestamps(variant);
        return variants.save(variant);
    }

    private void timestamps(com.core.beautyshop.shared.domain.Base entity) {
        entity.setCreatedAt(java.time.Instant.now()); entity.setUpdatedAt(java.time.Instant.now());
    }
}
