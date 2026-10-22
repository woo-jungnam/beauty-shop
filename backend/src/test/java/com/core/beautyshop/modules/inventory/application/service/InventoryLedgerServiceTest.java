package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryLedgerServiceTest {

    @Mock InventoryTransactionRepository repository;
    @Mock CatalogFacade catalogFacade;
    @Mock WarehouseRepository warehouseRepository;
    @Mock WarehouseStockRepository warehouseStockRepository;

    @InjectMocks InventoryLedgerService service;

    private InventoryTransaction sampleTx(Long id) {
        InventoryTransaction tx = InventoryTransaction.builder()
                .warehouseStockId(10L)
                .warehouseId(20L)
                .productVariantId(30L)
                .transactionType(InventoryTransactionType.RECEIPT)
                .quantity(50)
                .quantityBefore(0)
                .quantityAfter(50)
                .unitCost(new BigDecimal("150000.00"))
                .referenceType("PURCHASE_ORDER")
                .referenceId("PO-20261022-01")
                .note("Nhập kho từ PO")
                .occurredAt(Instant.parse("2026-10-22T08:00:00Z"))
                .build();
        tx.setId(id);
        tx.setCreatedBy("admin_user");
        return tx;
    }

    @Test
    void findEnrichesTransactionsWithCatalogWarehouseAndStockDetails() {
        InventoryTransaction tx = sampleTx(1L);
        var pageable = PageRequest.of(0, 15);
        when(repository.findAllByOrderByOccurredAtDesc(pageable)).thenReturn(new PageImpl<>(List.of(tx)));

        ProductVariantSummaryDto variant = ProductVariantSummaryDto.builder()
                .id(30L)
                .sku("SKU-SERUM-30")
                .productName("Serum Duong Am")
                .variantName("Chai 30ml")
                .build();
        when(catalogFacade.getVariantSummariesForInventory(List.of(30L))).thenReturn(Map.of(30L, variant));

        Warehouse wh = Warehouse.builder().name("Kho Tong TP.HCM").build();
        wh.setId(20L);
        when(warehouseRepository.findAllById(List.of(20L))).thenReturn(List.of(wh));

        WarehouseStock stock = WarehouseStock.builder()
                .batchCode("LOT-202610-01")
                .expirationDate(LocalDate.of(2028, 12, 31))
                .location("Ke A-01")
                .build();
        stock.setId(10L);
        when(warehouseStockRepository.findAllById(List.of(10L))).thenReturn(List.of(stock));

        var result = service.find(null, null, pageable);

        assertEquals(1, result.getTotalElements());
        var view = result.getContent().getFirst();
        assertEquals(1L, view.id());
        assertEquals("SKU-SERUM-30", view.sku());
        assertEquals("Serum Duong Am", view.productName());
        assertEquals("Chai 30ml", view.variantName());
        assertEquals("Kho Tong TP.HCM", view.warehouseName());
        assertEquals("LOT-202610-01", view.batchCode());
        assertEquals(LocalDate.of(2028, 12, 31), view.expirationDate());
        assertEquals("Ke A-01", view.location());
        assertEquals("admin_user", view.createdBy());
        assertEquals(InventoryTransactionType.RECEIPT, view.type());
        assertEquals(50, view.quantity());
        assertEquals(0, view.quantityBefore());
        assertEquals(50, view.quantityAfter());
    }

    @Test
    void findFiltersByStockIdOrVariantId() {
        var pageable = PageRequest.of(0, 15);
        when(repository.findByWarehouseStockIdOrderByOccurredAtDesc(eq(10L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleTx(1L))));
        when(repository.findByProductVariantIdOrderByOccurredAtDesc(eq(30L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleTx(2L))));

        var byStock = service.find(10L, 30L, pageable);
        assertEquals(1L, byStock.getContent().getFirst().id());
        verify(repository).findByWarehouseStockIdOrderByOccurredAtDesc(10L, pageable);
        verify(repository, never()).findByProductVariantIdOrderByOccurredAtDesc(any(), any());

        var byVariant = service.find(null, 30L, pageable);
        assertEquals(2L, byVariant.getContent().getFirst().id());
        verify(repository).findByProductVariantIdOrderByOccurredAtDesc(30L, pageable);
    }

    @Test
    void findByIdReturnsEnrichedTransaction() {
        InventoryTransaction tx = sampleTx(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(tx));

        ProductVariantSummaryDto variant = ProductVariantSummaryDto.builder()
                .id(30L).sku("SKU-5").productName("Kem Duong").variantName("Hu 50g").build();
        when(catalogFacade.getVariantSummariesForInventory(List.of(30L))).thenReturn(Map.of(30L, variant));

        Warehouse wh = Warehouse.builder().name("Kho Binh Thanh").build();
        wh.setId(20L);
        when(warehouseRepository.findById(20L)).thenReturn(Optional.of(wh));

        WarehouseStock stock = WarehouseStock.builder()
                .batchCode("BATCH-99").expirationDate(LocalDate.of(2027, 5, 1)).location("Ke B-02").build();
        stock.setId(10L);
        when(warehouseStockRepository.findById(10L)).thenReturn(Optional.of(stock));

        var view = service.findById(5L);
        assertEquals(5L, view.id());
        assertEquals("SKU-5", view.sku());
        assertEquals("Kem Duong", view.productName());
        assertEquals("Hu 50g", view.variantName());
        assertEquals("Kho Binh Thanh", view.warehouseName());
        assertEquals("BATCH-99", view.batchCode());
        assertEquals(LocalDate.of(2027, 5, 1), view.expirationDate());
        assertEquals("Ke B-02", view.location());
        assertEquals("admin_user", view.createdBy());
    }

    @Test
    void findByIdThrowsWhenNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(999L));
    }
}
