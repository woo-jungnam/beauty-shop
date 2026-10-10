package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest;
import com.core.beautyshop.modules.inventory.domain.Warehouse;
import com.core.beautyshop.modules.inventory.domain.WarehouseRepository;
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseStockServiceImplTest {

    @Mock WarehouseStockRepository stockRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock CatalogFacade catalogFacade;
    @Mock InventoryLedgerService ledgerService;
    @Mock InventoryBatchService batches;
    @InjectMocks WarehouseStockServiceImpl service;

    @Test
    void refusesToDeleteAReservedBatch() {
        WarehouseStock stock = WarehouseStock.builder()
                .quantity(10)
                .reservedQuantity(2)
                .quarantinedQuantity(0)
                .build();
        stock.setId(7L);
        when(stockRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(stock));

        assertThrows(BusinessException.class, () -> service.deleteStock(7L));

        verify(stockRepository, never()).delete(stock);
    }

    @Test
    void refusesToDeleteAQuarantinedBatch() {
        WarehouseStock stock = WarehouseStock.builder()
                .quantity(10)
                .reservedQuantity(0)
                .quarantinedQuantity(3)
                .build();
        stock.setId(8L);
        when(stockRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(stock));

        assertThrows(BusinessException.class, () -> service.deleteStock(8L));

        verify(stockRepository, never()).delete(stock);
    }

    @Test
    void purchaseReceiptIncrementsQuantityAndPreservesStockPolicy() {
        Warehouse warehouse = Warehouse.builder().name("Main").code("MAIN").build();
        warehouse.setId(1L);
        WarehouseStock stock = WarehouseStock.builder()
                .warehouse(warehouse)
                .productVariantId(11L)
                .quantity(10)
                .reservedQuantity(2)
                .quarantinedQuantity(0)
                .batchCode("LOT-1")
                .minQuantity(5)
                .maxQuantity(100)
                .location("A-01")
                .build();
        stock.setId(7L);
        WarehouseStockRequest request = new WarehouseStockRequest();
        request.setProductVariantId(11L);
        request.setQuantity(3);
        request.setBatchCode("LOT-1");

        when(batches.lockWarehouse(1L)).thenReturn(warehouse);
        when(catalogFacade.getVariantSummaryForInventory(11L))
                .thenReturn(ProductVariantSummaryDto.builder().id(11L).sku("SKU-11").build());
        when(batches.lockBatch(1L, 11L, "LOT-1"))
                .thenReturn(Optional.of(stock));
        when(stockRepository.save(any(WarehouseStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.receiveStock(1L, request, "PURCHASE_ORDER", "PO-1");

        assertEquals(13, stock.getQuantity());
        assertEquals(5, stock.getMinQuantity());
        assertEquals(100, stock.getMaxQuantity());
        assertEquals("A-01", stock.getLocation());
        verify(ledgerService).record(stock, InventoryTransactionType.RECEIPT, 3, 10, 13,
                "PURCHASE_ORDER", "PO-1", "Stock receipt");
    }
}
