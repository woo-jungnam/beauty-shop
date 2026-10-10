package com.core.beautyshop.modules.procurement.application;

import com.core.beautyshop.modules.inventory.api.InventoryAdminFacade;
import com.core.beautyshop.modules.procurement.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcurementServiceTest {
    @Mock SupplierRepository suppliers;
    @Mock PurchaseOrderRepository orders;
    @Mock InventoryAdminFacade inventory;
    @Mock com.core.beautyshop.modules.catalog.api.CatalogFacade catalog;
    @InjectMocks ProcurementService service;

    @Test
    void receiveApprovedOrderPostsEachLineToInventoryExactlyOnce() {
        Supplier supplier = Supplier.builder().code("SUP").name("Supplier").build(); supplier.setId(2L);
        PurchaseOrder order = PurchaseOrder.builder().orderNumber("PO-1").supplier(supplier).warehouseId(3L)
                .status(PurchaseOrder.Status.APPROVED).totalAmount(new BigDecimal("200000")).build(); order.setId(1L);
        PurchaseOrderItem item = PurchaseOrderItem.builder().purchaseOrder(order).productVariantId(4L).quantity(2)
                .receivedQuantity(0).unitCost(new BigDecimal("100000")).batchCode("LOT").expirationDate(LocalDate.now().plusYears(1)).build();
        order.getItems().add(item);
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        var result = service.receive(1L);

        assertEquals(PurchaseOrder.Status.RECEIVED, result.status());
        assertEquals(2, item.getReceivedQuantity());
        verify(inventory).receive(eq(3L), eq(4L), eq(2), eq(new BigDecimal("100000")), eq("LOT"), any(), eq("PURCHASE_ORDER"), eq("PO-1"));
        assertThrows(com.core.beautyshop.shared.exception.BusinessException.class, () -> service.receive(1L));
    }

    @Test
    void invalidSkuCannotBeSavedOrApprovedAndOnlyDraftCanBeEdited() {
        Supplier supplier = Supplier.builder().code("SUP").name("Supplier").build(); supplier.setId(2L);
        var command = new ProcurementService.OrderCommand(2L, 3L, null, "note",
                List.of(new ProcurementService.ItemCommand(999L, 2, BigDecimal.TEN, "LOT", null)));
        when(suppliers.findById(2L)).thenReturn(Optional.of(supplier));
        doThrow(new com.core.beautyshop.shared.exception.ResourceNotFoundException("SKU missing"))
                .when(catalog).getVariantSummaryForInventory(999L);
        assertThrows(com.core.beautyshop.shared.exception.ResourceNotFoundException.class, () -> service.create(command));
        verify(orders, never()).save(any());
        PurchaseOrder order = PurchaseOrder.builder().orderNumber("PO").supplier(supplier).warehouseId(3L)
                .status(PurchaseOrder.Status.APPROVED).totalAmount(BigDecimal.TEN).build();
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        assertThrows(com.core.beautyshop.shared.exception.BusinessException.class, () -> service.update(1L, command));
        order.setStatus(PurchaseOrder.Status.DRAFT);
        order.getItems().add(PurchaseOrderItem.builder().purchaseOrder(order).productVariantId(999L).quantity(1)
                .receivedQuantity(0).unitCost(BigDecimal.TEN).build());
        assertThrows(com.core.beautyshop.shared.exception.ResourceNotFoundException.class, () -> service.approve(1L));
        assertEquals(PurchaseOrder.Status.DRAFT, order.getStatus());
    }

    @Test
    void editingDraftReplacesLinesAndRecalculatesTotal() {
        Supplier supplier = Supplier.builder().code("SUP").name("Supplier").build(); supplier.setId(2L);
        PurchaseOrder order = PurchaseOrder.builder().orderNumber("PO").supplier(supplier).warehouseId(3L)
                .status(PurchaseOrder.Status.DRAFT).totalAmount(BigDecimal.TEN).build();
        order.getItems().add(PurchaseOrderItem.builder().purchaseOrder(order).productVariantId(4L).quantity(1)
                .receivedQuantity(0).unitCost(BigDecimal.TEN).build());
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(suppliers.findById(2L)).thenReturn(Optional.of(supplier));
        var view = service.update(1L, new ProcurementService.OrderCommand(2L, 3L, null, "Changed",
                List.of(new ProcurementService.ItemCommand(5L, 3, new BigDecimal("20.00"), " LOT ", null))));
        assertEquals(1, view.items().size()); assertEquals(5L, view.items().getFirst().variantId());
        assertEquals(0, new BigDecimal("60").compareTo(view.totalAmount()));
        assertEquals("LOT", view.items().getFirst().batchCode());
    }
}
