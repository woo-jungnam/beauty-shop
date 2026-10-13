package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateProductRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.domain.Brand;
import com.core.beautyshop.modules.catalog.domain.BrandRepository;
import com.core.beautyshop.modules.catalog.domain.CategoryRepository;
import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.domain.ProductTagRepository;
import com.core.beautyshop.modules.catalog.domain.ProductVariant;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.modules.catalog.domain.Category;
import com.core.beautyshop.modules.catalog.domain.ProductTag;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.api.dto.ExpiringVariantStockDto;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductTagRepository productTagRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private InventoryFacade inventoryFacade;
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

    @Test
    void creatingProductRejectsMissingCategoryInsteadOfSilentlyDroppingIt() {
        CreateProductRequest request = requestWithVariants(variant("SKU-1", "100", null, true));
        request.setCategoryIds(List.of(1L, 2L));
        Category category = Category.builder().name("Valid").slug("valid").build();
        category.setId(1L);
        when(categoryRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(category));
        assertThrows(BusinessException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void creatingProductRejectsSoftDeletedCategoryAndTag() {
        Category category = Category.builder().name("Deleted").slug("deleted").build();
        category.setId(1L);
        category.setIsDeleted(true);
        CreateProductRequest categoryRequest = requestWithVariants(variant("SKU-1", "100", null, true));
        categoryRequest.setCategoryIds(List.of(1L));
        when(categoryRepository.findAllById(List.of(1L))).thenReturn(List.of(category));
        assertThrows(BusinessException.class, () -> productService.createProduct(categoryRequest));

        ProductTag tag = ProductTag.builder().name("Deleted").slug("deleted").build();
        tag.setId(2L);
        tag.setIsDeleted(true);
        CreateProductRequest tagRequest = requestWithVariants(variant("SKU-2", "100", null, true));
        tagRequest.setTagIds(List.of(2L));
        when(productTagRepository.findAllById(List.of(2L))).thenReturn(List.of(tag));
        assertThrows(BusinessException.class, () -> productService.createProduct(tagRequest));
        verify(productRepository, never()).save(any());
    }

    @Test
    void getExpiringSoonProductsReturnsEmptyWhenNoExpiringStocks() {
        when(inventoryFacade.getExpiringVariantStocks(90, 32)).thenReturn(List.of());

        List<ProductListResponse> result = productService.getExpiringSoonProducts(90, 8);

        assertTrue(result.isEmpty());
        verify(productVariantRepository, never()).findActiveVariantsWithProductAndBrandByIds(any());
    }

    @Test
    void getExpiringSoonProductsDeduplicatesByProductAndSortsByEarliestExpirationDate() {
        LocalDate dateIn20Days = LocalDate.now().plusDays(20);
        LocalDate dateIn30Days = LocalDate.now().plusDays(30);
        LocalDate dateIn10Days = LocalDate.now().plusDays(10);

        // Product 1 has two variants (v1 expires in 20 days, v2 expires in 30 days)
        // Product 2 has one variant (v3 expires in 10 days)
        ExpiringVariantStockDto stockV1 = ExpiringVariantStockDto.builder()
                .variantId(1L).earliestExpirationDate(dateIn20Days).availableQuantity(50).build();
        ExpiringVariantStockDto stockV2 = ExpiringVariantStockDto.builder()
                .variantId(2L).earliestExpirationDate(dateIn30Days).availableQuantity(30).build();
        ExpiringVariantStockDto stockV3 = ExpiringVariantStockDto.builder()
                .variantId(3L).earliestExpirationDate(dateIn10Days).availableQuantity(20).build();

        when(inventoryFacade.getExpiringVariantStocks(60, 16))
                .thenReturn(List.of(stockV3, stockV1, stockV2));

        Brand brand = Brand.builder().name("Test Brand").build();
        brand.setId(10L);

        Product prod1 = Product.builder().name("Product 1").slug("prod-1").status(ProductStatus.ACTIVE).brand(brand).totalSold(100L).build();
        prod1.setId(101L);

        Product prod2 = Product.builder().name("Product 2").slug("prod-2").status(ProductStatus.ACTIVE).brand(brand).totalSold(50L).build();
        prod2.setId(102L);

        ProductVariant v1 = ProductVariant.builder().product(prod1).price(new BigDecimal("100000")).discountPrice(new BigDecimal("80000")).build();
        v1.setId(1L);

        ProductVariant v2 = ProductVariant.builder().product(prod1).price(new BigDecimal("120000")).discountPrice(new BigDecimal("90000")).build();
        v2.setId(2L);

        ProductVariant v3 = ProductVariant.builder().product(prod2).price(new BigDecimal("200000")).build();
        v3.setId(3L);

        when(productVariantRepository.findActiveVariantsWithProductAndBrandByIds(any()))
                .thenReturn(List.of(v1, v2, v3));

        List<ProductListResponse> result = productService.getExpiringSoonProducts(60, 4);

        assertEquals(2, result.size());
        // Earliest expiration date first: prod2 (in 10 days) before prod1 (in 20 days)
        assertEquals(102L, result.get(0).getId());
        assertEquals(dateIn10Days, result.get(0).getEarliestExpirationDate());
        assertEquals(10, result.get(0).getDaysRemaining());
        assertEquals(20, result.get(0).getClearanceStock());

        assertEquals(101L, result.get(1).getId());
        assertEquals(dateIn20Days, result.get(1).getEarliestExpirationDate());
        assertEquals(20, result.get(1).getDaysRemaining());
        assertEquals(50, result.get(1).getClearanceStock());
        assertEquals(new BigDecimal("80000"), result.get(1).getMinPrice());
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
                .discountPrice(discountPrice == null ? null : new BigDecimal(discountPrice))
                .isDefault(isDefault)
                .build();
    }
}
