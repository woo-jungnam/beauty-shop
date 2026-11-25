package com.core.beautyshop.modules.procurement.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.procurement.domain.ProcurementMovement;
import com.core.beautyshop.modules.procurement.domain.ProcurementMovementRepository;
import com.core.beautyshop.modules.procurement.domain.PurchaseOrder;
import com.core.beautyshop.modules.procurement.domain.PurchaseOrderItem;
import com.core.beautyshop.modules.procurement.domain.PurchaseOrderRepository;
import com.core.beautyshop.modules.procurement.domain.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcurementHistoryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void enrichesMovementLinesWithCatalogNamesAndComputesSummary() {
        var movementRepo = mock(ProcurementMovementRepository.class);
        var orderRepo = mock(PurchaseOrderRepository.class);
        var catalog = mock(CatalogFacade.class);
        var service = new ProcurementHistoryService(movementRepo, orderRepo, catalog, new ObjectMapper().findAndRegisterModules());

        var supplier = Supplier.builder().code("NCC01").name("NCC Mỹ Phẩm A").build();
        supplier.setId(5L);

        var po = PurchaseOrder.builder()
                .orderNumber("PO-20261125-01")
                .supplier(supplier)
                .warehouseId(2L)
                .status(PurchaseOrder.Status.PARTIALLY_RECEIVED)
                .totalAmount(new BigDecimal("1000000"))
                .paidAmount(new BigDecimal("400000"))
                .receivedAt(Instant.parse("2026-11-25T03:00:00Z"))
                .build();
        po.setId(10L);

        var poItem = PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .productVariantId(30L)
                .quantity(10)
                .receivedQuantity(6)
                .returnedQuantity(1)
                .unitCost(new BigDecimal("100000"))
                .batchCode("LOT-1")
                .build();
        poItem.setId(20L);
        po.getItems().add(poItem);

        var receipt = new ProcurementMovement();
        receipt.setId(100L);
        receipt.setPurchaseOrderId(10L);
        receipt.setKind("RECEIPT");
        receipt.setReference("RCV-01");
        receipt.setAmount(new BigDecimal("600000"));
        receipt.setReason("Nhập kho đợt 1");
        receipt.setLinesJson("[{\"itemId\":20,\"quantity\":6,\"batchCode\":\"LOT-1\",\"expirationDate\":\"2027-12-31\"}]");
        receipt.setCreatedAt(Instant.parse("2026-11-25T03:00:00Z"));

        var payment = new ProcurementMovement();
        payment.setId(101L);
        payment.setPurchaseOrderId(10L);
        payment.setKind("PAYMENT");
        payment.setReference("PAY-01");
        payment.setAmount(new BigDecimal("400000"));
        payment.setReason("Thanh toán đợt 1");
        payment.setLinesJson("[]");
        payment.setCreatedAt(Instant.parse("2026-11-25T04:00:00Z"));

        var pageable = PageRequest.of(0, 15);
        when(movementRepo.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(receipt), pageable, 1));
        when(orderRepo.findAllById(any())).thenReturn(List.of(po));
        when(catalog.getVariantSummariesForInventory(any()))
                .thenReturn(Map.of(30L, ProductVariantSummaryDto.builder()
                        .id(30L).productName("Kem chống nắng").variantName("50ml").sku("KCN-50").build()));

        var page = service.searchMovements(null, "RECEIPT", null, null, null, null, null, pageable);
        assertEquals(1, page.getTotalElements());
        var view = page.getContent().getFirst();
        assertEquals("PO-20261125-01", view.orderNumber());
        assertEquals("NCC Mỹ Phẩm A", view.supplierName());
        assertEquals(6, view.totalQuantity());
        assertEquals(1, view.lines().size());
        assertEquals("Kem chống nắng", view.lines().getFirst().productName());
        assertEquals("KCN-50", view.lines().getFirst().sku());
        assertEquals(new BigDecimal("600000"), view.lines().getFirst().lineAmount());

        when(movementRepo.findAll(any(Specification.class))).thenReturn(List.of(receipt, payment));
        when(orderRepo.findAll(any(Specification.class))).thenReturn(List.of(po));

        var summary = service.getHistorySummary(null, null, null, null, null);
        assertEquals(1L, summary.receiptCount());
        assertEquals(new BigDecimal("600000"), summary.totalReceiptAmount());
        assertEquals(new BigDecimal("400000"), summary.totalPaymentAmount());
        assertEquals(1L, summary.receivedOrdersCount());
        assertEquals(new BigDecimal("500000"), summary.receivedOrdersValue());
        assertEquals(new BigDecimal("100000"), summary.outstandingPayableAmount());
    }
}

