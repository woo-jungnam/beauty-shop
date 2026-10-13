package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryBatchServiceTest {
    @Test
    void duplicateLegacyUnnamedBatchesFailInsteadOfHidingOrOverwritingInventory() {
        var stocks = mock(WarehouseStockRepository.class);
        when(stocks.lockBatch(1L, 2L, "")).thenReturn(List.of(new WarehouseStock(), new WarehouseStock()));
        var service = new InventoryBatchService(mock(WarehouseRepository.class), stocks, mock(jakarta.persistence.EntityManager.class));
        assertThrows(BusinessException.class, () -> service.lockBatch(1L, 2L, null));
    }

    @Test
    void onlyEmptyDeletedBatchesMayBeRestored() {
        var service = new InventoryBatchService(mock(WarehouseRepository.class), mock(WarehouseStockRepository.class), mock(jakarta.persistence.EntityManager.class));
        var stock = WarehouseStock.builder().quantity(0).build(); stock.setIsDeleted(true);
        service.restoreEmptyBatch(stock); assertFalse(stock.getIsDeleted());
        stock.setIsDeleted(true); stock.setQuarantinedQuantity(1);
        assertThrows(BusinessException.class, () -> service.restoreEmptyBatch(stock));
    }
}
