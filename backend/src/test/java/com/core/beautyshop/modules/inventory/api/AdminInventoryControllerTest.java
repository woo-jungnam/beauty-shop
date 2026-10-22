package com.core.beautyshop.modules.inventory.api;

import com.core.beautyshop.modules.inventory.application.service.InventoryAlertService;
import com.core.beautyshop.modules.inventory.application.service.InventoryLedgerService;
import com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInventoryControllerTest {

    @Mock InventoryLedgerService ledger;
    @Mock InventoryAlertService alerts;

    @InjectMocks AdminInventoryController controller;

    private InventoryLedgerService.LedgerView sampleView(Long id) {
        return new InventoryLedgerService.LedgerView(
                id, 10L, 20L, "Kho Tổng", 30L, "SKU-01", "Kem Duong", "Hu 50g",
                "LOT-01", LocalDate.of(2028, 1, 1), "Ke A",
                InventoryTransactionType.RECEIPT, 10, 0, 10,
                new BigDecimal("100000.00"), "MANUAL_RECEIPT", "KEY-1", "Ghi chu",
                "admin", Instant.now()
        );
    }

    @Test
    void transactionsDelegatesToService() {
        var pageable = PageRequest.of(0, 15);
        when(ledger.find(10L, null, pageable)).thenReturn(new PageImpl<>(List.of(sampleView(1L))));

        var res = controller.transactions(10L, null, pageable);
        assertNotNull(res);
        assertEquals(200, res.getStatus());
        assertEquals(1, res.getData().getContent().size());
        assertEquals(1L, res.getData().getContent().getFirst().id());
        verify(ledger).find(10L, null, pageable);
    }

    @Test
    void getTransactionReturnsDetail() {
        when(ledger.findById(1L)).thenReturn(sampleView(1L));

        var res = controller.getTransaction(1L);
        assertNotNull(res);
        assertEquals(200, res.getStatus());
        assertEquals(1L, res.getData().id());
        assertEquals("SKU-01", res.getData().sku());
        assertEquals("Kem Duong", res.getData().productName());
        assertEquals("admin", res.getData().createdBy());
        verify(ledger).findById(1L);
    }
}
