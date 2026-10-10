package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryMovementServiceTest {
    @Mock WarehouseStockRepository stocks;
    @Mock WarehouseRepository warehouses;
    @Mock InventoryLedgerService ledger;
    @Mock InventoryBatchService batches;
    @InjectMocks InventoryMovementService service;

    @Test
    void transferMovesOnlyAvailableQuantityBetweenWarehouses() {
        Warehouse sourceWarehouse = Warehouse.builder().name("A").code("A").build(); sourceWarehouse.setId(1L);
        Warehouse targetWarehouse = Warehouse.builder().name("B").code("B").build(); targetWarehouse.setId(2L);
        WarehouseStock source = WarehouseStock.builder().warehouse(sourceWarehouse).productVariantId(10L).quantity(20)
                .reservedQuantity(3).quarantinedQuantity(2).batchCode("LOT-1").costPrice(new BigDecimal("10000")).build();
        source.setId(5L);
        when(stocks.findByIdForUpdate(5L)).thenReturn(Optional.of(source));
        when(stocks.findWarehouseIdByStockId(5L)).thenReturn(Optional.of(1L));
        when(batches.lockWarehouse(1L)).thenReturn(sourceWarehouse);
        when(batches.lockWarehouse(2L)).thenReturn(targetWarehouse);
        when(batches.lockBatch(2L, 10L, "LOT-1")).thenReturn(Optional.empty());
        when(stocks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.transfer(5L, 2L, 10, "Balance branches");

        assertEquals(10, source.getQuantity());
        verify(ledger, times(2)).record(any(), any(), anyInt(), anyInt(), anyInt(), eq("TRANSFER"), isNull(), eq("Balance branches"));
    }

    @Test
    void adjustmentPreservesReservationsAndDoesNotCountSeparateQuarantineTwice() {
        WarehouseStock stock = WarehouseStock.builder().quantity(10).reservedQuantity(4).quarantinedQuantity(3).build();
        when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(stock));
        service.adjust(1L, 6, "Count");
        assertEquals(6, stock.getQuantity()); assertEquals(3, stock.getQuarantinedQuantity());
        assertThrows(BusinessException.class, () -> service.adjust(1L, 3, "Count"));
    }

    @Test
    void allSellableStockCanMoveWhileSeparateReturnedGoodsRemainQuarantined() {
        Warehouse a = Warehouse.builder().name("A").code("A").build(); a.setId(1L);
        Warehouse b = Warehouse.builder().name("B").code("B").build(); b.setId(2L);
        WarehouseStock source = WarehouseStock.builder().warehouse(a).productVariantId(10L).quantity(7)
                .quarantinedQuantity(3).batchCode("LOT").build(); source.setId(5L);
        when(stocks.findWarehouseIdByStockId(5L)).thenReturn(Optional.of(1L));
        when(batches.lockWarehouse(1L)).thenReturn(a); when(batches.lockWarehouse(2L)).thenReturn(b);
        when(stocks.findByIdForUpdate(5L)).thenReturn(Optional.of(source));
        when(batches.lockBatch(2L, 10L, "LOT")).thenReturn(Optional.empty());
        when(stocks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.transfer(5L, 2L, 7, "Move sellable stock");
        assertEquals(0, source.getQuantity()); assertEquals(3, source.getQuarantinedQuantity());
    }
}
