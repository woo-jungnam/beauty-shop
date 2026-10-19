package com.core.beautyshop;

import com.core.beautyshop.modules.cart.domain.*;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.modules.inventory.api.InventoryExpiryPolicy;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.domain.*;
import com.core.beautyshop.shared.time.BusinessTime;
import com.core.beautyshop.modules.order.application.dto.request.*;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.application.service.*;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.application.service.PaymentWebhookService;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:checkout_business_integrity;DB_CLOSE_DELAY=-1;MODE=MySQL")
@ActiveProfiles("test")
class CheckoutIntegrityIntegrationTest {
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseStockRepository stocks;
    @Autowired StockAllocationRepository allocations;
    @Autowired InventoryFacade inventory;
    @Autowired com.core.beautyshop.modules.inventory.application.service.SpaMaterialOptionsService materialOptions;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired com.core.beautyshop.modules.cart.application.service.CartService cartService;
    @Autowired OrderRepository orders;
    @Autowired OrderService service;
    @Autowired OrderExpirationService expiration;
    @Autowired OrderRefundService refunds;
    @Autowired OrderCodSettlementService codSettlements;
    @Autowired ProductReturnService productReturns;
    @Autowired com.core.beautyshop.modules.inventory.application.service.BatchRecallService batchRecalls;
    @Autowired com.core.beautyshop.modules.inventory.application.service.InventoryConsumptionService consumptions;
    @Autowired com.core.beautyshop.modules.procurement.application.ProcurementReceiptService procurementReceipts;
    @Autowired com.core.beautyshop.modules.procurement.domain.PurchaseOrderRepository purchaseOrders;
    @Autowired com.core.beautyshop.modules.procurement.domain.SupplierRepository suppliers;
    @Autowired com.core.beautyshop.shared.config.SystemConfigService systemConfigs;
    @Autowired com.core.beautyshop.shared.config.domain.SystemConfigRepository configRepository;
    @Autowired javax.sql.DataSource dataSource;
    private String previousShippingThreshold;
    @BeforeEach void isolateFixtureShippingPolicy() {
        SpaOperationalJdbcFixture.ensure(dataSource);
        // These concurrency fixtures price a unit at 100 VND. Shipping is tested separately.
        var previous=configRepository.findByConfigKeyAndIsDeletedFalse("shipping.free_threshold");
        previousShippingThreshold=previous.map(com.core.beautyshop.shared.config.domain.SystemConfig::getConfigValue).orElse(null);
        if(previous.isPresent())systemConfigs.update("shipping.free_threshold","0");
        else systemConfigs.create("shipping.free_threshold","0","Isolated checkout integrity test policy","DECIMAL");
    }
    @AfterEach void restoreShippingPolicy() {
        if(previousShippingThreshold==null)systemConfigs.delete("shipping.free_threshold");
        else systemConfigs.update("shipping.free_threshold",previousShippingThreshold);
    }
    @Autowired com.core.beautyshop.modules.inventory.application.service.WarehouseStockService warehouseStockService;
    @Autowired com.core.beautyshop.modules.inventory.application.service.WarehouseService warehouseService;
    @Autowired com.core.beautyshop.modules.inventory.application.service.StockInspectionService stockInspection;
    @Autowired InventoryTransactionRepository inventoryTransactions;
    @Autowired com.core.beautyshop.modules.payment.domain.PaymentTransactionRepository paymentTransactions;
    @Autowired PaymentWebhookService payments;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.core.beautyshop.modules.identity.domain.UserRepository users;
    @Autowired com.core.beautyshop.modules.spa.domain.BeautyServiceRepository spaServices;
    @Autowired com.core.beautyshop.modules.spa.domain.ServicePackageRepository packages;
    @Autowired com.core.beautyshop.modules.spa.domain.UserServiceTicketRepository tickets;
    @Autowired com.core.beautyshop.modules.spa.application.service.AppointmentService appointments;
    @Value("${sepay.bank.account-number}") String bankAccount;

    record Fixture(String session, long stockId, long variantId) {}
    <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }

    Fixture fixture(int quantity) {
        return tx(() -> {
            String suffix = UUID.randomUUID().toString();
            Product product = products.save(Product.builder().name("Test product").slug(suffix).build());
            ProductVariant variant = variants.save(ProductVariant.builder().product(product).sku(suffix)
                    .variantName("Default").price(BigDecimal.valueOf(100)).build());
            Warehouse warehouse = warehouses.save(Warehouse.builder().name("Test warehouse").code(suffix).build());
            WarehouseStock stock = stocks.save(WarehouseStock.builder().warehouse(warehouse).productVariantId(variant.getId())
                    .batchCode(suffix).quantity(quantity).expirationDate(BusinessTime.today().plusDays(100)).build());
            Cart cart = Cart.builder().sessionId(suffix).items(new ArrayList<>()).build();
            cart.getItems().add(CartItem.builder().cart(cart).productVariantId(variant.getId()).quantity(1).build());
            carts.save(cart);
            return new Fixture(suffix, stock.getId(), variant.getId());
        });
    }
    CheckoutRequest request(Fixture fixture, PaymentMethod method) {
        CheckoutRequest request = new CheckoutRequest();
        request.setSessionId(fixture.session()); request.setCustomerName("Guest");
        request.setCustomerPhone("0987654321"); request.setShippingAddress("Test address");
        request.setPaymentMethod(method); request.setIdempotencyKey("checkout-1");
        return request;
    }
    <T> List<T> concurrent(Supplier<T> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            Callable<T> task = () -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException(); return action.get(); };
            var first = executor.submit(task); var second = executor.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
    void status(long id, OrderStatus status) {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(); request.setStatus(status);
        service.updateOrderStatus(id, request);
    }
    SePayWebhookRequest payment(OrderResponse order, String reference) {
        var request = new SePayWebhookRequest(); request.setReferenceCode(reference);
        request.setTransferType("in"); request.setAccountNumber(bankAccount);
        request.setContent("PAY " + order.getOrderNumber()); request.setTransferAmount(BigDecimal.valueOf(100));
        return request;
    }

    @Test
    void oneAddToCartCallPersistsExactlyOneRowAndReturnsThatRow() {
        Fixture fixture = fixture(5);
        String sessionId = "single-add-" + UUID.randomUUID();
        var request = new com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest();
        request.setSessionId(sessionId);
        request.setVariantId(fixture.variantId());
        request.setQuantity(1);

        var response = cartService.addToCart(null, request);

        Cart persistedCart = carts.findBySessionId(sessionId).orElseThrow();
        List<CartItem> persistedItems = cartItems.findAllByCartIdOrderByIdAsc(persistedCart.getId());
        assertEquals(1, persistedItems.size());
        assertEquals(1, response.getItems().size());
        assertEquals(persistedItems.getFirst().getId(), response.getItems().getFirst().getId());
        assertEquals(0L, persistedItems.getFirst().getRowVersion());
    }

    @Test
    void concurrentCheckoutReplaysOneOrderAndReservesOnce() throws Exception {
        Fixture fixture = fixture(5);
        List<OrderResponse> results = concurrent(() -> service.checkout(null, request(fixture, PaymentMethod.BANK)));
        assertEquals(results.getFirst().getId(), results.getLast().getId());
        assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
        assertEquals(36, results.getFirst().getOrderNumber().length());
        CheckoutRequest changed = request(fixture, PaymentMethod.COD);
        assertThrows(BusinessException.class, () -> service.checkout(null, changed));
        assertTrue(tx(() -> carts.findBySessionId(fixture.session()).orElseThrow().getItems().isEmpty()));
    }

    @Test
    void repeatedConcurrentDeliveryAndReturnAffectTheOriginalBatchOnlyOnce() throws Exception {
        Fixture fixture = fixture(5);
        OrderResponse order = service.checkout(null, request(fixture, PaymentMethod.COD));
        status(order.getId(), OrderStatus.PROCESSING); status(order.getId(), OrderStatus.SHIPPED);
        concurrent(() -> { status(order.getId(), OrderStatus.DELIVERED); return true; });
        assertEquals(4, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
        assertEquals(PaymentStatus.PENDING, orders.findById(order.getId()).orElseThrow().getPaymentStatus());
        asAdmin(() -> { codSettlements.confirm(order.getId(),new OrderCodSettlementService.Settlement(order.getTotalAmount(),"COD-"+UUID.randomUUID(),"Carrier remitted collected cash")); return true; });
        concurrent(() -> { status(order.getId(), OrderStatus.RETURNED); return true; });
        WarehouseStock stock = stocks.findById(fixture.stockId()).orElseThrow();
        assertEquals(4, stock.getQuantity()); assertEquals(1, stock.getQuarantinedQuantity());
        assertEquals(PaymentStatus.REFUND_PENDING, orders.findById(order.getId()).orElseThrow().getPaymentStatus());
        assertThrows(BusinessException.class, () -> status(order.getId(), OrderStatus.CANCELLED));
    }

    @Test
    void duplicateConcurrentWebhookAccumulatesMoneyOnce() throws Exception {
        Fixture fixture = fixture(5);
        OrderResponse order = service.checkout(null, request(fixture, PaymentMethod.BANK));
        String reference = UUID.randomUUID().toString();
        concurrent(() -> { payments.processSePayWebhook(payment(order, reference)); return true; });
        Order saved = orders.findById(order.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(100).compareTo(saved.getPaidAmount()));
        assertEquals(PaymentStatus.PAID, saved.getPaymentStatus());
    }

    @Test
    void latePaymentRequiresRefundAndDoesNotReReserveStock() {
        Fixture fixture = fixture(5);
        OrderResponse order = service.checkout(null, request(fixture, PaymentMethod.BANK));
        tx(() -> { orders.findById(order.getId()).orElseThrow().setPaymentDeadline(Instant.now().minusSeconds(1)); return null; });
        expiration.expire(order.getId()); expiration.expire(order.getId());
        assertEquals(0, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
        payments.processSePayWebhook(payment(order, UUID.randomUUID().toString()));
        assertEquals(PaymentStatus.REFUND_PENDING, orders.findById(order.getId()).orElseThrow().getPaymentStatus());
        var confirmation = new ConfirmRefundRequest("bank-refund-" + order.getId(), BigDecimal.valueOf(100));
        refunds.confirm(order.getId(), confirmation); refunds.confirm(order.getId(), confirmation);
        assertEquals(PaymentStatus.REFUNDED, orders.findById(order.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void failedCheckoutRollsBackCartAndStock() {
        Fixture fixture = fixture(0);
        assertThrows(RuntimeException.class, () -> service.checkout(null, request(fixture, PaymentMethod.BANK)));
        assertEquals(0, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
        assertEquals(1, (int) tx(() -> carts.findBySessionId(fixture.session()).orElseThrow().getItems().size()));
    }

    com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest receipt(long variantId, String batch, int quantity) {
        var request = new com.core.beautyshop.modules.inventory.application.dto.request.WarehouseStockRequest();
        request.setProductVariantId(variantId); request.setQuantity(quantity); request.setBatchCode(batch);
        return request;
    }

    @Test
    void concurrentReceiptsAccumulateTheSameExistingBatchAndLedger() throws Exception {
        Fixture fixture = fixture(10);
        var batch = tx(() -> stocks.findById(fixture.stockId()).orElseThrow());
        long warehouseId = tx(() -> stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId());
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        concurrent(() -> warehouseStockService.receiveStock(warehouseId,
                receipt(fixture.variantId(), batch.getBatchCode(), counter.getAndIncrement() == 0 ? 5 : 7), "TEST_RECEIPT", UUID.randomUUID().toString()));
        assertEquals(22, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
    }

    @Test
    void concurrentNewNullBatchReceiptsCreateOneBatchAndAcceptDraftInactiveSku() throws Exception {
        Fixture fixture = fixture(0);
        long warehouseId = tx(() -> {
            var variant = variants.findById(fixture.variantId()).orElseThrow();
            variant.setIsActive(false); variant.getProduct().setStatus(com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.INACTIVE);
            return stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId();
        });
        concurrent(() -> warehouseStockService.receiveStock(warehouseId, receipt(fixture.variantId(), null, 5), "TEST_RECEIPT", "new-batch"));
        var rows = stocks.findByWarehouseIdAndProductVariantIdAndBatchCode(warehouseId, fixture.variantId(), "");
        assertTrue(rows.isPresent()); assertEquals(10, rows.orElseThrow().getQuantity());
        assertEquals(2, stocks.findByProductVariantId(fixture.variantId()).size()); // Existing named fixture + one unnamed batch.
    }

    @Test
    void receivingIntoDeletedEmptyBatchRestoresSaleableInventory() {
        Fixture fixture = fixture(3);
        long warehouseId = tx(() -> stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId());
        String batch = stocks.findById(fixture.stockId()).orElseThrow().getBatchCode();
        warehouseStockService.deleteStock(fixture.stockId());
        warehouseStockService.receiveStock(warehouseId, receipt(fixture.variantId(), batch, 4), "TEST_RECEIPT", "restore");
        var restored = stocks.findById(fixture.stockId()).orElseThrow();
        assertFalse(restored.getIsDeleted()); assertEquals(4, restored.getQuantity());
        assertEquals(4, stocks.getTotalAvailableQuantityForVariant(fixture.variantId(), InventoryExpiryPolicy.saleCutoff()));
    }

    @Test
    void warehouseDeletionCannotHideStockReservedForAnOpenOrder() {
        Fixture fixture = fixture(5);
        long warehouseId = tx(() -> stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId());
        OrderResponse order = service.checkout(null, request(fixture, PaymentMethod.BANK));

        assertThrows(BusinessException.class, () -> warehouseService.deleteWarehouse(warehouseId));

        assertFalse(warehouses.findById(warehouseId).orElseThrow().getIsDeleted());
        assertEquals(4, stocks.getTotalAvailableQuantityForVariant(fixture.variantId(), InventoryExpiryPolicy.saleCutoff()));
        assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
        payments.processSePayWebhook(payment(order, UUID.randomUUID().toString()));
        status(order.getId(), OrderStatus.SHIPPED); status(order.getId(), OrderStatus.DELIVERED);
        assertEquals(4, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
    }

    @Test
    void warehouseDeletionRequiresQuarantineResolutionAndKeepsHistoryWhenEmpty() {
        Fixture fixture = fixture(0);
        long warehouseId = tx(() -> {
            var stock = stocks.findById(fixture.stockId()).orElseThrow();
            stock.setQuarantinedQuantity(1);
            return stock.getWarehouse().getId();
        });
        assertThrows(BusinessException.class, () -> warehouseService.deleteWarehouse(warehouseId));
        assertFalse(warehouses.findById(warehouseId).orElseThrow().getIsDeleted());
        assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getQuarantinedQuantity());

        stockInspection.inspect(fixture.stockId(), 1, false);
        long historyBefore = inventoryTransactions.count();
        warehouseService.deleteWarehouse(warehouseId);
        warehouseService.deleteWarehouse(warehouseId);

        assertTrue(warehouses.findById(warehouseId).orElseThrow().getIsDeleted());
        assertTrue(stocks.findById(fixture.stockId()).isPresent());
        assertEquals(historyBefore, inventoryTransactions.count());
        assertFalse(warehouseService.getAllWarehouses().stream().anyMatch(w -> w.getId().equals(warehouseId)));
    }

    @Test
    void legacyDeletedBatchWithReservationStillBlocksWarehouseDeletion() {
        Fixture fixture = fixture(0);
        long warehouseId = tx(() -> {
            var stock = stocks.findById(fixture.stockId()).orElseThrow();
            stock.setIsDeleted(true); stock.setReservedQuantity(1);
            return stock.getWarehouse().getId();
        });
        assertThrows(BusinessException.class, () -> warehouseService.deleteWarehouse(warehouseId));
        assertFalse(warehouses.findById(warehouseId).orElseThrow().getIsDeleted());
        assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
    }

    @Test
    void committedReceiptWinsBeforeConcurrentWarehouseDeletion() throws Exception {
        Fixture fixture = fixture(0);
        long warehouseId = tx(() -> stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId());
        String batchCode = stocks.findById(fixture.stockId()).orElseThrow().getBatchCode();
        var received = new CountDownLatch(1); var commit = new CountDownLatch(1); var deleting = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var receipt = executor.submit(() -> tx(() -> {
                warehouseStockService.receiveStock(warehouseId, receipt(fixture.variantId(), batchCode, 2), "TEST_RECEIPT", "before-delete");
                received.countDown(); awaitCommit(commit); return true;
            }));
            try {
                assertTrue(received.await(10, TimeUnit.SECONDS));
                var deletion = executor.submit(() -> {
                    deleting.countDown();
                    try { warehouseService.deleteWarehouse(warehouseId); return false; }
                    catch (BusinessException expected) { return true; }
                });
                assertTrue(deleting.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> deletion.get(250, TimeUnit.MILLISECONDS));
                commit.countDown();
                assertTrue(receipt.get(20, TimeUnit.SECONDS)); assertTrue(deletion.get(20, TimeUnit.SECONDS));
            } finally { commit.countDown(); }
        }
        assertFalse(warehouses.findById(warehouseId).orElseThrow().getIsDeleted());
        assertEquals(2, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
        assertEquals(2, stocks.getTotalAvailableQuantityForVariant(fixture.variantId(), InventoryExpiryPolicy.saleCutoff()));
    }

    @Test
    void committedWarehouseDeletionRejectsConcurrentReceiptInsteadOfHidingNewStock() throws Exception {
        Fixture fixture = fixture(0);
        long warehouseId = tx(() -> stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId());
        String batchCode = stocks.findById(fixture.stockId()).orElseThrow().getBatchCode();
        var deleted = new CountDownLatch(1); var commit = new CountDownLatch(1); var receiving = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> tx(() -> {
                warehouseService.deleteWarehouse(warehouseId);
                deleted.countDown(); awaitCommit(commit); return true;
            }));
            try {
                assertTrue(deleted.await(10, TimeUnit.SECONDS));
                var receipt = executor.submit(() -> {
                    receiving.countDown();
                    try {
                        warehouseStockService.receiveStock(warehouseId, receipt(fixture.variantId(), batchCode, 2), "TEST_RECEIPT", "after-delete");
                        return false;
                    } catch (BusinessException expected) { return true; }
                });
                assertTrue(receiving.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> receipt.get(250, TimeUnit.MILLISECONDS));
                commit.countDown();
                assertTrue(deletion.get(20, TimeUnit.SECONDS)); assertTrue(receipt.get(20, TimeUnit.SECONDS));
            } finally { commit.countDown(); }
        }
        assertTrue(warehouses.findById(warehouseId).orElseThrow().getIsDeleted());
        assertEquals(0, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
    }

    void awaitCommit(CountDownLatch commit) {
        try { if (!commit.await(20, TimeUnit.SECONDS)) throw new AssertionError("Commit latch timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    @Test
    void freeBankOrderIsPaidWithoutDeadlineAndReplayDoesNotReserveTwice() {
        Fixture fixture = fixture(5);
        tx(() -> { variants.findById(fixture.variantId()).orElseThrow().setDiscountPrice(BigDecimal.ZERO); return null; });
        OrderResponse response = service.checkout(null, request(fixture, PaymentMethod.BANK));
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus()); assertEquals(OrderStatus.PROCESSING, response.getStatus());
        assertNull(response.getPaymentDeadline()); assertNotNull(response.getPaidAt());
        assertEquals(response.getPaidAt(), service.checkout(null, request(fixture, PaymentMethod.BANK)).getPaidAt());
        assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
        status(response.getId(), OrderStatus.SHIPPED); status(response.getId(), OrderStatus.DELIVERED);
        assertEquals(4, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
    }

    @Test
    void qrAmountAndPersistedVndObligationRoundTogether() {
        Fixture fixture = fixture(5);
        tx(() -> { variants.findById(fixture.variantId()).orElseThrow().setPrice(new BigDecimal("100.50")); return null; });
        OrderResponse response = service.checkout(null, request(fixture, PaymentMethod.BANK));
        assertEquals(0, new BigDecimal("101").compareTo(response.getTotalAmount()));
        assertTrue(response.getPaymentInstruction().getQrCodeUrl().contains("amount=101&"));
        var webhook = payment(response, UUID.randomUUID().toString()); webhook.setTransferAmount(new BigDecimal("101"));
        payments.processSePayWebhook(webhook);
        assertEquals(PaymentStatus.PAID, orders.findById(response.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void codDeliveryWaitsForCarrierRemittanceAndPreservesExcessTransfersAndPaidAt() {
        Fixture fixture = fixture(5);
        OrderResponse response = service.checkout(null, request(fixture, PaymentMethod.COD));
        var webhook = payment(response, UUID.randomUUID().toString()); webhook.setTransferAmount(new BigDecimal("40"));
        payments.processSePayWebhook(webhook);
        status(response.getId(), OrderStatus.PROCESSING); status(response.getId(), OrderStatus.SHIPPED);
        status(response.getId(), OrderStatus.DELIVERED);
        assertEquals(PaymentStatus.PENDING,orders.findById(response.getId()).orElseThrow().getPaymentStatus());
        assertTrue(paymentTransactions.findByReferenceCode("COD-" + response.getOrderNumber()).isEmpty());
        String settlementReference="remit-"+UUID.randomUUID();
        var command=new OrderCodSettlementService.Settlement(new BigDecimal("60"),settlementReference,"Carrier transferred actual COD collections");
        asAdmin(()->{codSettlements.confirm(response.getId(),command);codSettlements.confirm(response.getId(),command);return true;});
        var cod = paymentTransactions.findByReferenceCode("COD-SETTLED-" + settlementReference).orElseThrow();
        assertEquals(0, new BigDecimal("60").compareTo(cod.getAmount()));
        Instant paidAt = orders.findById(response.getId()).orElseThrow().getPaidAt();
        status(response.getId(), OrderStatus.DELIVERED);
        webhook = payment(response, UUID.randomUUID().toString()); webhook.setTransferAmount(new BigDecimal("50"));
        payments.processSePayWebhook(webhook);
        assertEquals(0, new BigDecimal("150").compareTo(orders.findById(response.getId()).orElseThrow().getPaidAmount()));
        assertEquals(paidAt, orders.findById(response.getId()).orElseThrow().getPaidAt());
        status(response.getId(), OrderStatus.RETURNED);
        refunds.confirm(response.getId(), new ConfirmRefundRequest("COD-refund-" + response.getId(), new BigDecimal("150")));
        assertEquals(paidAt, orders.findById(response.getId()).orElseThrow().getPaidAt());
    }

    @Test
    void codOverpaymentBeforeDeliveryIsNotOverwrittenOrCollectedAgain() {
        Fixture fixture = fixture(5);
        OrderResponse response = service.checkout(null, request(fixture, PaymentMethod.COD));
        var webhook = payment(response, UUID.randomUUID().toString()); webhook.setTransferAmount(new BigDecimal("150"));
        payments.processSePayWebhook(webhook);
        Instant paidAt = orders.findById(response.getId()).orElseThrow().getPaidAt();
        status(response.getId(), OrderStatus.SHIPPED); status(response.getId(), OrderStatus.DELIVERED);
        assertEquals(0, new BigDecimal("150").compareTo(orders.findById(response.getId()).orElseThrow().getPaidAmount()));
        assertTrue(paymentTransactions.findByReferenceCode("COD-" + response.getOrderNumber()).isEmpty());
        assertEquals(paidAt, orders.findById(response.getId()).orElseThrow().getPaidAt());
    }

    <T> T asUser(Long userId, Supplier<T> action) {
        var principal = new com.core.beautyshop.shared.security.services.UserDetailsImpl(
                userId, "integration", "integration@example.test", "unused",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")));
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        try { return action.get(); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    <T> T asAdmin(Supplier<T> action) {
        var principal=new com.core.beautyshop.shared.security.services.UserDetailsImpl(90000L,"commerce-admin","admin@example.test","unused",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        try{return action.get();}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }

    @Test void paidBankCancellationRestoresItsReservationAndQueuesActualRefund() {
        var fixture=fixture(5);var response=service.checkout(null,request(fixture,PaymentMethod.BANK));payments.processSePayWebhook(payment(response,UUID.randomUUID().toString()));
        var c=new UpdateOrderStatusRequest();c.setStatus(OrderStatus.CANCELLED);c.setNotes("Customer requested cancellation before shipping");service.updateOrderStatus(response.getId(),c);
        var stock=stocks.findById(fixture.stockId()).orElseThrow();assertEquals(5,stock.getQuantity());assertEquals(0,stock.getReservedQuantity());assertEquals(PaymentStatus.REFUND_PENDING,orders.findById(response.getId()).orElseThrow().getPaymentStatus());
    }

    @Test void bankReceiptPersistsEvenIfReservedBatchExpiresAndDispatchMustBeReallocated() {
        var fixture=fixture(5);var response=service.checkout(null,request(fixture,PaymentMethod.BANK));
        tx(()->{stocks.findById(fixture.stockId()).orElseThrow().setExpirationDate(LocalDate.now().minusDays(1));return true;});
        payments.processSePayWebhook(payment(response,UUID.randomUUID().toString()));assertEquals(PaymentStatus.PAID,orders.findById(response.getId()).orElseThrow().getPaymentStatus());
        assertThrows(BusinessException.class,()->status(response.getId(),OrderStatus.SHIPPED));assertEquals(OrderStatus.PROCESSING,orders.findById(response.getId()).orElseThrow().getStatus());
    }

    @Test void refusedCodDeliveryReturnsToQuarantineWithoutRecordingAnyReceipt() {
        var fixture=fixture(5);var response=service.checkout(null,request(fixture,PaymentMethod.COD));status(response.getId(),OrderStatus.PROCESSING);status(response.getId(),OrderStatus.SHIPPED);
        var c=new UpdateOrderStatusRequest();c.setStatus(OrderStatus.DELIVERY_FAILED);c.setNotes("Recipient refused delivery");service.updateOrderStatus(response.getId(),c);c.setStatus(OrderStatus.RETURN_TO_SENDER);service.updateOrderStatus(response.getId(),c);
        var order=orders.findById(response.getId()).orElseThrow();assertEquals(0,order.getPaidAmount().signum());assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());assertTrue(paymentTransactions.findByOrderNumberOrderByCreatedAtDesc(order.getOrderNumber(),org.springframework.data.domain.PageRequest.of(0,20)).isEmpty());
        assertEquals(1,stocks.findById(fixture.stockId()).orElseThrow().getQuarantinedQuantity());
    }

    @Test void guestPartialReturnReservesPurchasedQuantityReceivesOnceAndRefundsOnlySelectedItem() {
        var fixture=fixture(5);tx(()->{var cart=carts.findBySessionId(fixture.session()).orElseThrow();cart.getItems().getFirst().setQuantity(2);return true;});
        var response=service.checkout(null,request(fixture,PaymentMethod.COD));status(response.getId(),OrderStatus.PROCESSING);status(response.getId(),OrderStatus.SHIPPED);status(response.getId(),OrderStatus.DELIVERED);
        asAdmin(()->{codSettlements.confirm(response.getId(),new OrderCodSettlementService.Settlement(response.getTotalAmount(),"cod-"+UUID.randomUUID(),"Actual carrier remittance"));return true;});
        var request=new ProductReturnService.Request("One sealed item was damaged",List.of(new ProductReturnService.Line(response.getItems().getFirst().getId(),1)),List.of("https://example.test/photo.jpg"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->productReturns.request(response.getId(),"other-session",null,request));
        var row=productReturns.request(response.getId(),fixture.session(),"return-1",request);assertEquals(row.id(),productReturns.request(response.getId(),fixture.session(),"return-1",request).id());
        productReturns.decide(row.id(),new ProductReturnService.Decision(true,"Damaged item approved"));var receipt=new ProductReturnService.Confirmation("receipt-"+UUID.randomUUID(),"Goods physically received");productReturns.receive(row.id(),receipt);productReturns.receive(row.id(),receipt);
        assertEquals(1,stocks.findById(fixture.stockId()).orElseThrow().getQuarantinedQuantity());
        asAdmin(()->productReturns.refund(row.id(),new ProductReturnService.Confirmation("refund-"+UUID.randomUUID(),"Bank transfer completed")));
        var order=orders.findById(response.getId()).orElseThrow();assertEquals(OrderStatus.DELIVERED,order.getStatus());assertEquals(0,row.refundAmount().compareTo(order.getRefundedAmount()));assertTrue(order.getRefundedAmount().compareTo(order.getPaidAmount())<0);
        var extra=payment(response,UUID.randomUUID().toString());extra.setTransferAmount(new BigDecimal("50"));payments.processSePayWebhook(extra);
        asAdmin(()->{productReturns.overpaymentRefund(response.getId(),new BigDecimal("50"),"excess-after-return-"+UUID.randomUUID(),"Actual return of extra transfer after RMA");return true;});
        assertEquals(0,row.refundAmount().add(new BigDecimal("50")).compareTo(orders.findById(response.getId()).orElseThrow().getRefundedAmount()));
        var add=new com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest();add.setSessionId(fixture.session());add.setVariantId(fixture.variantId());add.setQuantity(1);cartService.addToCart(null,add);
        var replacementRequest=request(fixture,PaymentMethod.BANK);replacementRequest.setIdempotencyKey("replacement-"+UUID.randomUUID());var replacement=service.checkout(null,replacementRequest);
        int beforeLink=stocks.findById(fixture.stockId()).orElseThrow().getQuantity();
        var linked=asAdmin(()->productReturns.linkReplacement(row.id(),new ProductReturnService.Replacement(replacement.getId(),"Customer agreed to a separate replacement sale")));
        assertEquals(replacement.getId(),linked.replacementOrderId());assertEquals(beforeLink,stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
    }

    @Test void extraTransferCanBeRefundedWithoutCancellingFulfilledSale() {
        var fixture=fixture(5);var response=service.checkout(null,request(fixture,PaymentMethod.BANK));var webhook=payment(response,UUID.randomUUID().toString());webhook.setTransferAmount(response.getTotalAmount().add(new BigDecimal("50")));payments.processSePayWebhook(webhook);
        String reference="excess-"+UUID.randomUUID();asAdmin(()->{productReturns.overpaymentRefund(response.getId(),new BigDecimal("50"),reference,"Confirmed return of excess transfer");productReturns.overpaymentRefund(response.getId(),new BigDecimal("50"),reference,"Confirmed return of excess transfer");return true;});
        var order=orders.findById(response.getId()).orElseThrow();assertEquals(OrderStatus.PROCESSING,order.getStatus());assertEquals(PaymentStatus.PAID,order.getPaymentStatus());assertEquals(0,new BigDecimal("50").compareTo(order.getRefundedAmount()));
    }

    @Test void recalledReservationCanBeReplacedWithoutSellingTheRecalledBatch() {
        var fixture=fixture(5);var response=service.checkout(null,request(fixture,PaymentMethod.BANK));payments.processSePayWebhook(payment(response,UUID.randomUUID().toString()));
        var original=stocks.findById(fixture.stockId()).orElseThrow();
        batchRecalls.recall(new com.core.beautyshop.modules.inventory.application.service.BatchRecallService.Command("recall-"+UUID.randomUUID(),fixture.variantId(),original.getBatchCode(),"Supplier quality recall"));
        assertThrows(BusinessException.class,()->status(response.getId(),OrderStatus.SHIPPED));
        Long replacementId=tx(()->{var source=stocks.findById(fixture.stockId()).orElseThrow();return stocks.save(WarehouseStock.builder().warehouse(source.getWarehouse()).productVariantId(fixture.variantId()).batchCode("replacement-"+UUID.randomUUID()).quantity(4).expirationDate(LocalDate.now().plusYears(1)).costPrice(new BigDecimal("20")).build()).getId();});
        productReturns.replaceAllocation(response.getId());status(response.getId(),OrderStatus.SHIPPED);
        assertEquals(5,stocks.findById(fixture.stockId()).orElseThrow().getQuantity());assertEquals(0,stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());assertEquals(3,stocks.findById(replacementId).orElseThrow().getQuantity());
    }

    @Test void actualSpaConsumptionUsesUnreservedStockAndSnapshotsBatchCostOnce() {
        var fixture=fixture(5);Long warehouseId=tx(()->{var row=stocks.findById(fixture.stockId()).orElseThrow();row.setCostPrice(new BigDecimal("20"));return row.getWarehouse().getId();});
        String ref="spa-use-"+UUID.randomUUID();var result=consumptions.consume(ref,fixture.variantId(),warehouseId,2);var replay=consumptions.consume(ref,fixture.variantId(),warehouseId,2);
        assertEquals(result,replay);assertEquals(0,new BigDecimal("40").compareTo(result.totalCost()));assertEquals(fixture.stockId(),result.batches().getFirst().stockId());assertEquals(3,stocks.findById(fixture.stockId()).orElseThrow().getQuantity());assertThrows(BusinessException.class,()->consumptions.consume(ref,fixture.variantId(),warehouseId,1));
    }

    @Test
    void retailAndSpaSkipUnsafeEarlierLotsOfTheSameSku() {
        var fixture = fixture(5);
        LocalDate today = BusinessTime.today();
        Long safeId = tx(() -> {
            var unsafe = stocks.findById(fixture.stockId()).orElseThrow();
            unsafe.setExpirationDate(today.plusDays(20));
            return stocks.save(WarehouseStock.builder().warehouse(unsafe.getWarehouse())
                    .productVariantId(fixture.variantId()).batchCode("safe-" + UUID.randomUUID())
                    .quantity(3).expirationDate(today.plusDays(100)).build()).getId();
        });
        assertEquals(3, inventory.getAvailableQuantity(fixture.variantId()));
        assertEquals(List.of(safeId), tx(() -> stocks.findAvailableStocksByVariantIdFefo(
                fixture.variantId(), InventoryExpiryPolicy.saleCutoff(today)).stream().map(WarehouseStock::getId).toList()));
        var order = service.checkout(null, request(fixture, PaymentMethod.COD));
        assertEquals(safeId, tx(() -> allocations.lockAllocations(order.getOrderNumber(), fixture.variantId()).getFirst().getStockId()));
        Long warehouseId = tx(() -> stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId());
        assertEquals(2, materialOptions.list().stream().filter(option -> option.variantId().equals(fixture.variantId()))
                .findFirst().orElseThrow().availableQuantity());
        var usage = consumptions.consume("safe-spa-" + UUID.randomUUID(), fixture.variantId(), warehouseId, 2);
        assertEquals(safeId, usage.batches().getFirst().stockId());
        assertThrows(BusinessException.class, () -> consumptions.consume("unsafe-spa-" + UUID.randomUUID(), fixture.variantId(), warehouseId, 1));
        assertEquals(5, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
        assertEquals(0, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
    }

    @Test
    void clearanceAggregationFiltersBoundariesBeforeStableDatabasePagination() {
        LocalDate businessDate = LocalDate.of(2199, 1, 1);
        var first = fixture(2); var second = fixture(3); var third = fixture(4);
        var tooSoon = fixture(5); var tooLate = fixture(6); var undated = fixture(7);
        tx(() -> {
            stocks.findById(first.stockId()).orElseThrow().setExpirationDate(businessDate.plusDays(31));
            stocks.findById(second.stockId()).orElseThrow().setExpirationDate(businessDate.plusDays(31));
            stocks.findById(third.stockId()).orElseThrow().setExpirationDate(businessDate.plusDays(90));
            stocks.findById(tooSoon.stockId()).orElseThrow().setExpirationDate(businessDate.plusDays(30));
            stocks.findById(tooLate.stockId()).orElseThrow().setExpirationDate(businessDate.plusDays(91));
            stocks.findById(undated.stockId()).orElseThrow().setExpirationDate(null);
            return true;
        });
        var page0 = inventory.getExpiringVariantStocks(businessDate, 90, 0, 2);
        var page1 = inventory.getExpiringVariantStocks(businessDate, 90, 1, 2);
        assertEquals(List.of(first.variantId(), second.variantId()), page0.stream().map(row -> row.getVariantId()).toList());
        assertEquals(List.of(third.variantId()), page1.stream().map(row -> row.getVariantId()).toList());
        assertEquals(2, page0.getFirst().getAvailableQuantity());
        assertEquals(businessDate.plusDays(31), page0.getFirst().getEarliestExpirationDate());
        assertTrue(inventory.getExpiringVariantStocks(businessDate, 90, 2, 2).isEmpty());
    }

    @Test
    void dispatchedAt31DaysCanBeDeliveredAt30DaysWithoutAnotherDeduction() {
        var fixture = fixture(5); LocalDate day = LocalDate.of(2026, 10, 17);
        tx(() -> { stocks.findById(fixture.stockId()).orElseThrow().setExpirationDate(day.plusDays(31)); return true; });
        OrderResponse order;
        try (var time = org.mockito.Mockito.mockStatic(BusinessTime.class)) {
            time.when(BusinessTime::today).thenReturn(day);
            order = service.checkout(null, request(fixture, PaymentMethod.COD));
            status(order.getId(), OrderStatus.PROCESSING); status(order.getId(), OrderStatus.SHIPPED);
            time.when(BusinessTime::today).thenReturn(day.plusDays(1));
            status(order.getId(), OrderStatus.DELIVERED); status(order.getId(), OrderStatus.DELIVERED);
        }
        assertEquals(OrderStatus.DELIVERED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(4, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
        assertEquals(1, inventoryTransactions.findByWarehouseStockIdOrderByOccurredAtDesc(fixture.stockId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).stream()
                .filter(row -> row.getTransactionType() == com.core.beautyshop.modules.inventory.domain.enums.InventoryTransactionType.SALE).count());
    }

    @Test
    void legacyShippedReservationCannotBeDeductedByDeliveryConfirmation() {
        var fixture = fixture(5); var order = service.checkout(null, request(fixture, PaymentMethod.COD));
        tx(() -> { orders.findById(order.getId()).orElseThrow().setStatus(OrderStatus.SHIPPED); return true; });
        assertThrows(BusinessException.class, () -> status(order.getId(), OrderStatus.DELIVERED));
        assertEquals(OrderStatus.SHIPPED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(5, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
        assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
    }

    @Test
    void replacementFailureRollsBackReleasedReservationAtTheCutoff() {
        var fixture = fixture(5); LocalDate day = LocalDate.of(2026, 10, 17);
        tx(() -> { stocks.findById(fixture.stockId()).orElseThrow().setExpirationDate(day.plusDays(31)); return true; });
        try (var time = org.mockito.Mockito.mockStatic(BusinessTime.class)) {
            time.when(BusinessTime::today).thenReturn(day);
            var order = service.checkout(null, request(fixture, PaymentMethod.COD));
            status(order.getId(), OrderStatus.PROCESSING);
            time.when(BusinessTime::today).thenReturn(day.plusDays(1));
            assertThrows(BusinessException.class, () -> status(order.getId(), OrderStatus.SHIPPED));
            assertThrows(BusinessException.class, () -> productReturns.replaceAllocation(order.getId()));
            assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getReservedQuantity());
            assertEquals(StockAllocation.Status.RESERVED, tx(() -> allocations.lockAllocations(order.getOrderNumber(), fixture.variantId()).getFirst().getStatus()));
            assertEquals(OrderStatus.PROCESSING, orders.findById(order.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void inTransitFailedDeliveryCannotReplaceOrRedispatchAnUnsafeBatch() {
        var fixture = fixture(5); var order = service.checkout(null, request(fixture, PaymentMethod.COD));
        status(order.getId(), OrderStatus.PROCESSING); status(order.getId(), OrderStatus.SHIPPED);
        var failure = new UpdateOrderStatusRequest(); failure.setStatus(OrderStatus.DELIVERY_FAILED); failure.setNotes("Recipient absent");
        service.updateOrderStatus(order.getId(), failure);
        tx(() -> { stocks.findById(fixture.stockId()).orElseThrow().setExpirationDate(BusinessTime.today().plusDays(30)); return true; });
        assertThrows(BusinessException.class, () -> productReturns.replaceAllocation(order.getId()));
        assertThrows(BusinessException.class, () -> status(order.getId(), OrderStatus.SHIPPED));
        assertEquals(OrderStatus.DELIVERY_FAILED, orders.findById(order.getId()).orElseThrow().getStatus());
        failure.setStatus(OrderStatus.RETURN_TO_SENDER); service.updateOrderStatus(order.getId(), failure);
        assertEquals(4, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
        assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getQuarantinedQuantity());
    }

    @Test
    void paidFailedDeliveryCanBeRefundedAfterUnsafeStockReturnsToSender() {
        var fixture = fixture(5); var order = service.checkout(null, request(fixture, PaymentMethod.BANK));
        payments.processSePayWebhook(payment(order, UUID.randomUUID().toString()));
        status(order.getId(), OrderStatus.SHIPPED);
        var failure = new UpdateOrderStatusRequest(); failure.setStatus(OrderStatus.DELIVERY_FAILED); failure.setNotes("Recipient absent");
        service.updateOrderStatus(order.getId(), failure);
        tx(() -> { stocks.findById(fixture.stockId()).orElseThrow().setExpirationDate(BusinessTime.today().plusDays(30)); return true; });
        assertThrows(BusinessException.class, () -> status(order.getId(), OrderStatus.SHIPPED));
        failure.setStatus(OrderStatus.RETURN_TO_SENDER); service.updateOrderStatus(order.getId(), failure);
        var request = new com.core.beautyshop.modules.order.application.dto.request.ConfirmRefundRequest(
                "cutoff-refund-" + UUID.randomUUID(), order.getTotalAmount(), "Actual bank refund after carrier returned parcel");
        asAdmin(() -> { refunds.confirm(order.getId(), request); refunds.confirm(order.getId(), request); return true; });
        var saved = orders.findById(order.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, saved.getPaymentStatus());
        assertEquals(0, saved.getPaidAmount().compareTo(saved.getRefundedAmount()));
        assertEquals(4, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
        assertEquals(1, stocks.findById(fixture.stockId()).orElseThrow().getQuarantinedQuantity());
    }

    @Test
    void historicalSpaConsumptionReplaysAfterExpiryWithoutFurtherMutation() {
        var fixture = fixture(5);
        Long warehouseId = tx(() -> stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId());
        String reference = "historical-spa-" + UUID.randomUUID();
        var first = consumptions.consume(reference, fixture.variantId(), warehouseId, 2);
        tx(() -> { stocks.findById(fixture.stockId()).orElseThrow().setExpirationDate(BusinessTime.today().minusDays(1)); return true; });
        assertEquals(first, consumptions.consume(reference, fixture.variantId(), warehouseId, 2));
        assertEquals(3, stocks.findById(fixture.stockId()).orElseThrow().getQuantity());
        assertThrows(BusinessException.class, () -> consumptions.consume("new-spa-" + UUID.randomUUID(), fixture.variantId(), warehouseId, 1));
    }

    @Test void partialProcurementReceiptsSupplierReturnsAndCreditsReconcileToActualStock() {
        var fixture=fixture(5);Long poId=tx(()->{var supplier=suppliers.save(com.core.beautyshop.modules.procurement.domain.Supplier.builder().code("SUP-"+UUID.randomUUID()).name("Supplier").build());var warehouse=stocks.findById(fixture.stockId()).orElseThrow().getWarehouse();var po=com.core.beautyshop.modules.procurement.domain.PurchaseOrder.builder().orderNumber("PO-"+UUID.randomUUID()).supplier(supplier).warehouseId(warehouse.getId()).status(com.core.beautyshop.modules.procurement.domain.PurchaseOrder.Status.APPROVED).totalAmount(new BigDecimal("70")).build();po.getItems().add(com.core.beautyshop.modules.procurement.domain.PurchaseOrderItem.builder().purchaseOrder(po).productVariantId(fixture.variantId()).quantity(10).unitCost(new BigDecimal("7")).batchCode("planned").build());return purchaseOrders.saveAndFlush(po).getId();});
        Long itemId=tx(()->purchaseOrders.findById(poId).orElseThrow().getItems().getFirst().getId());String actualBatch="actual-"+UUID.randomUUID();
        var line=new com.core.beautyshop.modules.procurement.application.ProcurementReceiptService.Line(itemId,3,actualBatch,LocalDate.now().plusYears(1));
        var first=new com.core.beautyshop.modules.procurement.application.ProcurementReceiptService.Receipt("receive-"+UUID.randomUUID(),"Actual first shipment",List.of(line));procurementReceipts.receive(poId,first);procurementReceipts.receive(poId,first);
        procurementReceipts.receive(poId,new com.core.beautyshop.modules.procurement.application.ProcurementReceiptService.Receipt("receive-"+UUID.randomUUID(),"Actual second shipment",List.of(line)));
        assertEquals(com.core.beautyshop.modules.procurement.domain.PurchaseOrder.Status.PARTIALLY_RECEIVED,purchaseOrders.findById(poId).orElseThrow().getStatus());assertEquals(0,new BigDecimal("42").compareTo(procurementReceipts.financials(poId).dueAmount()));
        procurementReceipts.payment(poId,new com.core.beautyshop.modules.procurement.application.ProcurementReceiptService.Payment("pay-"+UUID.randomUUID(),"Actual supplier bank transfer",new BigDecimal("30")),false);
        var returned=new com.core.beautyshop.modules.procurement.application.ProcurementReceiptService.Line(itemId,2,actualBatch,null);var credit=procurementReceipts.supplierReturn(poId,new com.core.beautyshop.modules.procurement.application.ProcurementReceiptService.Receipt("return-"+UUID.randomUUID(),"Two damaged sealed units returned",List.of(returned)));
        assertEquals(0,new BigDecimal("-2").compareTo(credit.dueAmount()));var settled=procurementReceipts.payment(poId,new com.core.beautyshop.modules.procurement.application.ProcurementReceiptService.Payment("recover-"+UUID.randomUUID(),"Supplier returned excess payment",new BigDecimal("2")),true);assertEquals(0,settled.dueAmount().signum());
    }

    @Test
    void concurrentCancellationRestoresOnlyTheCancelledAppointmentsEntitlement() throws Exception {
        Fixture fixture = fixture(5);
        OrderResponse order = service.checkout(null, request(fixture, PaymentMethod.BANK));
        long[] ids = tx(() -> {
            var user = users.save(com.core.beautyshop.modules.identity.domain.User.builder()
                    .username(UUID.randomUUID().toString()).fullName("Spa customer").build());
            Order paid = orders.findById(order.getId()).orElseThrow();
            paid.setUserId(user.getId()); paid.setPaymentStatus(PaymentStatus.PAID); paid.setPaidAmount(BigDecimal.valueOf(100));
            var spa = spaServices.save(com.core.beautyshop.modules.spa.domain.BeautyService.builder()
                    .name("Facial").slug(UUID.randomUUID().toString()).basePrice(BigDecimal.TEN)
                    .durationMinutes(30).preparationTimeMinutes(0).build());
            var pack = packages.save(com.core.beautyshop.modules.spa.domain.ServicePackage.builder()
                    .name("Package").price(BigDecimal.valueOf(100)).build());
            var ticket = com.core.beautyshop.modules.spa.domain.UserServiceTicket.builder()
                    .userId(user.getId()).orderId(paid.getId()).servicePackage(pack).totalSessions(5).build();
            ticket.getEntitlements().put(spa.getId(), new com.core.beautyshop.modules.spa.domain.TicketEntitlement(5, 0));
            tickets.save(ticket);
            return new long[]{user.getId(), spa.getId(), ticket.getId()};
        });
        var item = new com.core.beautyshop.modules.spa.application.dto.request.AppointmentItemRequest();
        item.setServiceId(ids[1]); item.setTicketId(ids[2]);
        var booking = new com.core.beautyshop.modules.spa.application.dto.request.BookAppointmentRequest();
        booking.setAppointmentDate(LocalDate.now().plusDays(1)); booking.setStartTime(LocalTime.of(10, 0));
        booking.setItems(List.of(item));
        var first = asUser(ids[0], () -> appointments.bookAppointment(booking));
        booking.setStartTime(LocalTime.of(12, 0));
        asUser(ids[0], () -> appointments.bookAppointment(booking));
        concurrent(() -> asUser(ids[0], () -> { appointments.cancelAppointment(first.getId()); return true; }));
        assertEquals(0, tickets.findById(ids[2]).orElseThrow().getUsedSessions());
        assertEquals(1, tickets.findById(ids[2]).orElseThrow().getReservedSessions());
        tx(() -> {
            var entitlement = tickets.findById(ids[2]).orElseThrow().getEntitlements().get(ids[1]);
            assertEquals(0, entitlement.getUsed()); assertEquals(1, entitlement.getReserved()); return null;
        });
    }
}
