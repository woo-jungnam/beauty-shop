package com.core.beautyshop;

import com.core.beautyshop.modules.cart.domain.*;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.modules.inventory.domain.*;
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

@SpringBootTest
@ActiveProfiles("test")
class CheckoutIntegrityIntegrationTest {
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseStockRepository stocks;
    @Autowired StockAllocationRepository allocations;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired com.core.beautyshop.modules.cart.application.service.CartService cartService;
    @Autowired OrderRepository orders;
    @Autowired OrderService service;
    @Autowired OrderExpirationService expiration;
    @Autowired OrderRefundService refunds;
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
                    .batchCode(suffix).quantity(quantity).expirationDate(LocalDate.now().plusDays(30)).build());
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
        assertEquals(PaymentStatus.PAID, orders.findById(order.getId()).orElseThrow().getPaymentStatus());
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
        assertEquals(4, stocks.getTotalAvailableQuantityForVariant(fixture.variantId()));
    }

    @Test
    void warehouseDeletionCannotHideStockReservedForAnOpenOrder() {
        Fixture fixture = fixture(5);
        long warehouseId = tx(() -> stocks.findById(fixture.stockId()).orElseThrow().getWarehouse().getId());
        OrderResponse order = service.checkout(null, request(fixture, PaymentMethod.BANK));

        assertThrows(BusinessException.class, () -> warehouseService.deleteWarehouse(warehouseId));

        assertFalse(warehouses.findById(warehouseId).orElseThrow().getIsDeleted());
        assertEquals(4, stocks.getTotalAvailableQuantityForVariant(fixture.variantId()));
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
        assertEquals(2, stocks.getTotalAvailableQuantityForVariant(fixture.variantId()));
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
    void codDeliveryRecordsOnlyRemainingCashAndPreservesExcessTransfersAndPaidAt() {
        Fixture fixture = fixture(5);
        OrderResponse response = service.checkout(null, request(fixture, PaymentMethod.COD));
        var webhook = payment(response, UUID.randomUUID().toString()); webhook.setTransferAmount(new BigDecimal("40"));
        payments.processSePayWebhook(webhook);
        status(response.getId(), OrderStatus.PROCESSING); status(response.getId(), OrderStatus.SHIPPED);
        status(response.getId(), OrderStatus.DELIVERED);
        var cod = paymentTransactions.findByReferenceCode("COD-" + response.getOrderNumber()).orElseThrow();
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
