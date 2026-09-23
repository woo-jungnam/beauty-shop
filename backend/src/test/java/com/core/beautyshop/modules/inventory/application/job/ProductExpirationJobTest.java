package com.core.beautyshop.modules.inventory.application.job;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.modules.inventory.domain.Warehouse;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductExpirationJobTest {

    @Mock private WarehouseStockRepository warehouseStockRepository;
    @Mock private CatalogFacade catalogFacade;
    @InjectMocks private ProductExpirationWorker job;

    @Test
    void doesNotDeactivateVariantWhenANewerBatchStillExists() {
        WarehouseStock expired = stock(5L, 3, 0, LocalDate.now().minusDays(1));
        WarehouseStock newerButReserved = stock(5L, 4, 4, LocalDate.now().plusDays(90));
        when(warehouseStockRepository.findByProductVariantId(5L)).thenReturn(List.of(expired, newerButReserved));
        when(catalogFacade.findVariantSummaryById(5L)).thenReturn(Optional.of(activeVariant(5L)));

        job.checkVariant(5L);

        verify(catalogFacade, never()).deactivateVariant(5L);
        verify(catalogFacade, never()).applyDiscountPrice(
                org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deactivatesVariantWhenAllRemainingBatchesAreExpired() {
        when(warehouseStockRepository.findByProductVariantId(5L))
                .thenReturn(List.of(stock(5L, 3, 0, LocalDate.now().minusDays(1))));
        when(catalogFacade.findVariantSummaryById(5L)).thenReturn(Optional.of(activeVariant(5L)));

        job.checkVariant(5L);

        verify(catalogFacade).deactivateVariant(5L);
    }

    @Test
    void removesAutomaticDiscountWhenALongerDatedBatchArrives() {
        when(warehouseStockRepository.findByProductVariantId(5L))
                .thenReturn(List.of(stock(5L, 3, 0, LocalDate.now().plusDays(90))));
        ProductVariantSummaryDto variant = activeVariant(5L);
        variant.setDiscountPrice(new BigDecimal("80000"));
        when(catalogFacade.findVariantSummaryById(5L)).thenReturn(Optional.of(variant));

        job.checkVariant(5L);

        verify(catalogFacade).applyDiscountPrice(5L, null);
    }

    private WarehouseStock stock(Long variantId, int quantity, int reserved, LocalDate expirationDate) {
        Warehouse warehouse = Warehouse.builder().isActive(true).build();
        warehouse.setIsDeleted(false);
        return WarehouseStock.builder()
                .warehouse(warehouse)
                .productVariantId(variantId)
                .quantity(quantity)
                .reservedQuantity(reserved)
                .expirationDate(expirationDate)
                .build();
    }

    private ProductVariantSummaryDto activeVariant(Long id) {
        return ProductVariantSummaryDto.builder()
                .id(id)
                .isActive(true)
                .price(new BigDecimal("100000"))
                .build();
    }
}
