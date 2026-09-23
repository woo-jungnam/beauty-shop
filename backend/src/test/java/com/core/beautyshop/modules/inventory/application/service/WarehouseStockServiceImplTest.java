package com.core.beautyshop.modules.inventory.application.service;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.inventory.domain.WarehouseRepository;
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseStockServiceImplTest {

    @Mock WarehouseStockRepository stockRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock CatalogFacade catalogFacade;
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
}
