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
import com.core.beautyshop.shared.time.BusinessTime;
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
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.IntStream;

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
        LocalDate today = BusinessTime.today();
        when(inventoryFacade.getExpiringVariantStocks(today, 90, 0, 100)).thenReturn(List.of());
        assertTrue(productService.getExpiringSoonProducts(90, 8).isEmpty());
        verify(productVariantRepository, never()).findActiveVariantsWithProductAndBrandByIds(any());
    }

    @Test
    void clearanceKeepsExactDiscountedSkuAndDeduplicatesByEarliestDate() {
        LocalDate today = BusinessTime.today();
        var first = clearanceVariant(1L, 101L, "80000");
        var later = clearanceVariant(2L, 101L, "70000");
        var earliest = clearanceVariant(3L, 102L, "0");
        var noDiscount = clearanceVariant(4L, 103L, null);
        var stocks = List.of(stock(4L, today.plusDays(31), 50), stock(3L, today.plusDays(40), 20),
                stock(1L, today.plusDays(50), 50), stock(2L, today.plusDays(60), 30));
        when(inventoryFacade.getExpiringVariantStocks(today, 90, 0, 100)).thenReturn(stocks);
        when(productVariantRepository.findActiveVariantsWithProductAndBrandByIds(any()))
                .thenReturn(List.of(first, later, earliest, noDiscount));
        when(inventoryFacade.getAvailableQuantity(1L, today)).thenReturn(70);
        when(inventoryFacade.getAvailableQuantity(3L, today)).thenReturn(20);

        var result = productService.getExpiringSoonProducts(90, 4);
        assertEquals(List.of(102L, 101L), result.stream().map(ProductListResponse::getId).toList());
        assertEquals(3L, result.get(0).getClearanceVariant().getId());
        assertEquals(BigDecimal.ZERO, result.get(0).getMinPrice());
        assertEquals(40, result.get(0).getDaysRemaining());
        assertEquals(1L, result.get(1).getClearanceVariant().getId());
        assertEquals(new BigDecimal("80000"), result.get(1).getMinPrice());
        assertEquals(result.get(1).getMinPrice(), result.get(1).getMaxPrice());
        assertEquals(new BigDecimal("100000"), result.get(1).getClearanceVariant().getPrice());
        assertEquals(70, result.get(1).getClearanceVariant().getStockQuantity());
        assertEquals(50, result.get(1).getClearanceStock());
        assertEquals(new BigDecimal("80000"), first.getDiscountPrice());
        assertEquals(null, noDiscount.getDiscountPrice());
    }

    @Test
    void clearanceRejectsUnsafeAndOutOfWindowLotsAndInvalidAdminDiscounts() {
        LocalDate today = BusinessTime.today();
        var stocks = List.of(stock(1L, today.plusDays(30), 5), stock(2L, today.plusDays(31), 5),
                stock(3L, today.plusDays(90), 5), stock(4L, today.plusDays(91), 5), stock(5L, null, 5),
                stock(6L, today.plusDays(50), 5), stock(7L, today.plusDays(51), 5));
        when(inventoryFacade.getExpiringVariantStocks(today, 90, 0, 100)).thenReturn(stocks);
        when(productVariantRepository.findActiveVariantsWithProductAndBrandByIds(any())).thenReturn(List.of(
                clearanceVariant(1L, 101L, "80000"), clearanceVariant(2L, 102L, "80000"), clearanceVariant(3L, 103L, "80000"),
                clearanceVariant(4L, 104L, "80000"), clearanceVariant(5L, 105L, "80000"),
                clearanceVariant(6L, 106L, "100000"), clearanceVariant(7L, 107L, "-1")));
        assertEquals(List.of(102L, 103L), productService.getExpiringSoonProducts(999, 8).stream().map(ProductListResponse::getId).toList());
    }

    @Test
    void clearanceReadsNextPageAfterHundredNonDiscountedCandidates() {
        LocalDate today = BusinessTime.today();
        var firstPage = IntStream.rangeClosed(1, 100).mapToObj(id -> stock((long) id, today.plusDays(31), 1)).toList();
        var variants = IntStream.rangeClosed(1, 100).mapToObj(id -> clearanceVariant((long) id, 1000L + id, null)).toList();
        var valid = clearanceVariant(101L, 5000L, "80000");
        when(inventoryFacade.getExpiringVariantStocks(today, 90, 0, 100)).thenReturn(firstPage);
        when(inventoryFacade.getExpiringVariantStocks(today, 90, 1, 100)).thenReturn(List.of(stock(101L, today.plusDays(32), 1)));
        when(productVariantRepository.findActiveVariantsWithProductAndBrandByIds(any()))
                .thenReturn(variants).thenReturn(List.of(valid));
        var result = productService.getExpiringSoonProducts(90, 1);
        assertEquals(1, result.size());
        assertEquals(101L, result.getFirst().getClearanceVariant().getId());
        verify(inventoryFacade).getExpiringVariantStocks(today, 90, 1, 100);
    }

    private ExpiringVariantStockDto stock(Long variantId, LocalDate expiry, int quantity) {
        return ExpiringVariantStockDto.builder().variantId(variantId).earliestExpirationDate(expiry).availableQuantity(quantity).build();
    }

    private ProductVariant clearanceVariant(Long variantId, Long productId, String discount) {
        Product product = Product.builder().name("Product " + productId).slug("prod-" + productId).totalSold(productId).build();
        product.setId(productId);
        ProductVariant variant = ProductVariant.builder().product(product).sku("SKU-" + variantId).variantName("Variant " + variantId)
                .price(new BigDecimal("100000")).discountPrice(discount == null ? null : new BigDecimal(discount)).build();
        variant.setId(variantId);
        return variant;
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
