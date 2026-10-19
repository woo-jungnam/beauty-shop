package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.time.BusinessTime;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockInspectionExpiryTest {
    @Test void thirtyDayReturnCannotReenterSalesButCanStillBeDisposed() {
        var stocks = mock(WarehouseStockRepository.class); var ledger = mock(InventoryLedgerService.class);
        var stock = WarehouseStock.builder().warehouse(Warehouse.builder().isActive(true).build())
                .quantity(4).quarantinedQuantity(2).expirationDate(BusinessTime.today().plusDays(30)).build();
        stock.setId(1L); when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(stock));
        var service = new StockInspectionService(stocks, ledger);
        assertThrows(BusinessException.class, () -> service.inspect(1L, 1, true));
        assertEquals(2, stock.getQuarantinedQuantity()); assertEquals(4, stock.getQuantity());
        service.inspect(1L, 1, false);
        assertEquals(1, stock.getQuarantinedQuantity()); assertEquals(4, stock.getQuantity());
    }

    @Test void thirtyOneDayReturnMayBeRestockedUnlessRecalled() {
        var stocks = mock(WarehouseStockRepository.class); var ledger = mock(InventoryLedgerService.class);
        var stock = WarehouseStock.builder().warehouse(Warehouse.builder().isActive(true).build())
                .quantity(4).quarantinedQuantity(2).expirationDate(BusinessTime.today().plusDays(31)).build();
        stock.setId(1L); when(stocks.findByIdForUpdate(1L)).thenReturn(Optional.of(stock));
        var service = new StockInspectionService(stocks, ledger);
        stock.setIsBlocked(true);
        assertThrows(BusinessException.class, () -> service.inspect(1L, 1, true));
        stock.setIsBlocked(false); service.inspect(1L, 1, true);
        assertEquals(1, stock.getQuarantinedQuantity()); assertEquals(5, stock.getQuantity());
    }
}
