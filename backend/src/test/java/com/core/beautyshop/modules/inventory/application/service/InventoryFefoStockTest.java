package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryFefoStockTest {
    @Mock WarehouseStockRepository stocks;
    @Mock StockAllocationRepository allocations;
    @InjectMocks StockAllocationService service;

    WarehouseStock stock(long id, int quantity) {
        var stock = WarehouseStock.builder().productVariantId(10L).quantity(quantity)
                .expirationDate(LocalDate.now().plusDays(id)).build();
        stock.setId(id); return stock;
    }
    @Test
    void reservesEarlierBatchAndRecordsExactOwnership() {
        var first = stock(1, 3); var second = stock(2, 5);
        when(stocks.lockAvailableBatches(10L)).thenReturn(List.of(first, second));
        service.reserve("ORD-TEST", 10L, 4);
        assertEquals(3, first.getReservedQuantity()); assertEquals(1, second.getReservedQuantity());
        ArgumentCaptor<StockAllocation> captor = ArgumentCaptor.forClass(StockAllocation.class);
        verify(allocations, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(a -> a.getOrderNumber().equals("ORD-TEST")));
        assertEquals(List.of(1L, 2L), captor.getAllValues().stream().map(StockAllocation::getStockId).toList());
    }
    @Test
    void releasingOneOrderNeverConsumesAnotherOrdersReservation() {
        var stock = stock(1, 10); stock.setReservedQuantity(8);
        var allocation = allocation(3, StockAllocation.Status.RESERVED);
        when(allocations.lockAllocations("ORD-A", 10L)).thenReturn(List.of(allocation));
        when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(stock));
        service.transition("ORD-A", 10L, 3, StockAllocation.Status.RELEASED);
        service.transition("ORD-A", 10L, 3, StockAllocation.Status.RELEASED);
        assertEquals(5, stock.getReservedQuantity()); assertEquals(10, stock.getQuantity());
    }
    @Test
    void missingLegacyAllocationFailsWithoutTouchingAnyBatch() {
        assertThrows(BusinessException.class, () -> service.transition("ORD-OLD", 10L, 3, StockAllocation.Status.DEDUCTED));
        verifyNoInteractions(stocks);
    }
    @Test
    void returnedItemsAreQuarantinedInTheirOriginalBatch() {
        var stock = stock(1, 7);
        var allocation = allocation(3, StockAllocation.Status.DEDUCTED);
        when(allocations.lockAllocations("ORD-A", 10L)).thenReturn(List.of(allocation));
        when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(stock));
        service.transition("ORD-A", 10L, 3, StockAllocation.Status.QUARANTINED);
        assertEquals(7, stock.getQuantity()); assertEquals(3, stock.getQuarantinedQuantity());
    }
    private StockAllocation allocation(int quantity, StockAllocation.Status status) {
        var allocation = new StockAllocation(); allocation.setOrderNumber("ORD-A"); allocation.setVariantId(10L);
        allocation.setStockId(1L); allocation.setQuantity(quantity); allocation.setStatus(status); return allocation;
    }
}
