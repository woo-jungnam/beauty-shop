package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.api.InventoryExpiryPolicy;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.time.BusinessTime;
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
    @Mock InventoryLedgerService ledger;
    @InjectMocks StockAllocationService service;

    WarehouseStock stock(long id, int quantity) {
        var stock = WarehouseStock.builder().warehouse(Warehouse.builder().name("Main").code("MAIN").build())
                .productVariantId(10L).quantity(quantity).expirationDate(BusinessTime.today().plusDays(30 + id)).build();
        stock.setId(id); return stock;
    }
    @Test
    void reservesEarlierBatchAndRecordsExactOwnership() {
        var first = stock(1, 3); var second = stock(2, 5);
        when(stocks.lockAvailableBatches(eq(10L), any(LocalDate.class))).thenReturn(List.of(first, second));
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

    @Test
    void cancellingPaidOrderRestoresOnlyItsDeductedUnitsAndIsIdempotent() {
        var batch=stock(1,7);batch.setReservedQuantity(2);
        var row=allocation(3,StockAllocation.Status.DEDUCTED);
        when(allocations.lockAllocations("ORD-A",10L)).thenReturn(List.of(row));
        when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
        service.transition("ORD-A",10L,3,StockAllocation.Status.RELEASED);
        service.transition("ORD-A",10L,3,StockAllocation.Status.RELEASED);
        assertEquals(10,batch.getQuantity());assertEquals(2,batch.getReservedQuantity());
        assertEquals(StockAllocation.Status.RELEASED,row.getStatus());
        verify(ledger,times(1)).record(any(),any(),anyInt(),anyInt(),anyInt(),anyString(),anyString(),anyString());
    }
    @Test
    void dispatchRejectsPaidAllocationWhoseBatchExpiredAfterPayment() {
        var batch=stock(1,7);batch.setExpirationDate(com.core.beautyshop.shared.time.BusinessTime.today().minusDays(1));
        var row=allocation(3,StockAllocation.Status.DEDUCTED);
        when(allocations.lockAllocations("ORD-A",10L)).thenReturn(List.of(row));when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
        assertThrows(BusinessException.class,()->service.validateForDispatch("ORD-A",10L,3));
        assertEquals(7,batch.getQuantity());verifyNoInteractions(ledger);
    }
    @Test
    void dispatchRejectsRecalledBatchEvenWhenAlreadyDeducted() {
        var batch=stock(1,7);batch.setIsBlocked(true);var row=allocation(3,StockAllocation.Status.DEDUCTED);
        when(allocations.lockAllocations("ORD-A",10L)).thenReturn(List.of(row));when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
        assertThrows(BusinessException.class,()->service.validateForDispatch("ORD-A",10L,3));
    }
    @Test void expirationUsesVietnamBusinessDayBeforeSevenAmEvenWhenDatabaseDayIsUtcYesterday() {
        var fixed=java.time.Clock.fixed(java.time.Instant.parse("2026-10-16T18:30:00Z"),java.time.ZoneOffset.UTC);
        var today=com.core.beautyshop.shared.time.BusinessTime.today(fixed);
        assertEquals(java.time.LocalDate.of(2026,10,17),today);
        var batch=stock(1,7);batch.setExpirationDate(today);
        assertThrows(BusinessException.class,()->StockAllocationService.requireSellable(batch,today));
        for (int days : List.of(29, 30)) {
            batch.setExpirationDate(today.plusDays(days));
            assertThrows(BusinessException.class, () -> StockAllocationService.requireSellable(batch, today));
        }
        for (int days : List.of(31, 90, 91)) {
            batch.setExpirationDate(today.plusDays(days));
            assertDoesNotThrow(() -> StockAllocationService.requireSellable(batch, today));
        }
        batch.setExpirationDate(null);
        assertDoesNotThrow(() -> StockAllocationService.requireSellable(batch, today));
    }
    @Test
    void deliveredAllocationRequiresDeductedEvidenceWithoutInspectingCurrentStock() {
        var row = allocation(3, StockAllocation.Status.DEDUCTED);
        when(allocations.lockAllocations("ORD-A", 10L)).thenReturn(List.of(row));
        service.validateDeliveredAllocation("ORD-A", 10L, 3);
        service.validateDeliveredAllocation("ORD-A", 10L, 3);
        assertEquals(StockAllocation.Status.DEDUCTED, row.getStatus());
        verifyNoInteractions(stocks, ledger);
    }

    @Test
    void deliveredAllocationRejectsLegacyReservedMissingAndWrongQuantityEvidence() {
        var row = allocation(3, StockAllocation.Status.RESERVED);
        when(allocations.lockAllocations("ORD-A", 10L)).thenReturn(List.of(row));
        assertThrows(BusinessException.class, () -> service.validateDeliveredAllocation("ORD-A", 10L, 3));
        row.setStatus(StockAllocation.Status.DEDUCTED);
        assertThrows(BusinessException.class, () -> service.validateDeliveredAllocation("ORD-A", 10L, 2));
        assertThrows(BusinessException.class, () -> service.validateDeliveredAllocation("ORD-MISSING", 10L, 3));
        verifyNoInteractions(stocks, ledger);
    }

    @Test
    void reservePassesOneCapturedCutoffToTheFefoQuery() {
        LocalDate businessDate = LocalDate.of(2026, 10, 17);
        var batch = stock(1, 5); batch.setExpirationDate(businessDate.plusDays(31));
        when(stocks.lockAvailableBatches(10L, businessDate.plusDays(30))).thenReturn(List.of(batch));
        try (var time = mockStatic(com.core.beautyshop.shared.time.BusinessTime.class)) {
            time.when(com.core.beautyshop.shared.time.BusinessTime::today).thenReturn(businessDate, businessDate.plusDays(1));
            service.reserve("ORD-CUTOFF", 10L, 2);
            time.verify(com.core.beautyshop.shared.time.BusinessTime::today, times(1));
        }
        assertEquals(2, batch.getReservedQuantity());
        assertEquals(businessDate.plusDays(30), InventoryExpiryPolicy.saleCutoff(businessDate));
    }

    @Test
    void refusedCodShipmentReturnsReservationToQuarantineWithoutFictitiousSale() {
        var batch=stock(1,10);batch.setReservedQuantity(5);var row=allocation(3,StockAllocation.Status.RESERVED);
        when(allocations.lockAllocations("ORD-A",10L)).thenReturn(List.of(row));when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
        service.transition("ORD-A",10L,3,StockAllocation.Status.QUARANTINED);
        assertEquals(7,batch.getQuantity());assertEquals(2,batch.getReservedQuantity());assertEquals(3,batch.getQuarantinedQuantity());
    }
}
