package com.core.beautyshop;

import com.core.beautyshop.modules.identity.domain.*;
import com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult;
import com.core.beautyshop.modules.order.application.dto.request.*;
import com.core.beautyshop.modules.order.application.service.*;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.payment.application.dto.request.SePayWebhookRequest;
import com.core.beautyshop.modules.payment.application.service.PaymentWebhookService;
import com.core.beautyshop.modules.payment.domain.*;
import com.core.beautyshop.modules.payment.domain.enums.TransactionStatus;
import com.core.beautyshop.modules.spa.application.dto.request.*;
import com.core.beautyshop.modules.spa.application.service.SpaAppointmentCheckoutService;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:spa_visit_checkout;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class SpaVisitCheckoutIntegrityIntegrationTest {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired BeautyServiceRepository services;
    @Autowired AppointmentRepository appointments;
    @Autowired ServicePackageRepository packages;
    @Autowired UserServiceTicketRepository tickets;
    @Autowired OrderRepository orders;
    @Autowired PaymentTransactionRepository receipts;
    @Autowired com.core.beautyshop.modules.payment.api.AdminPaymentController paymentAdmin;
    @Autowired com.core.beautyshop.modules.dashboard.application.AdminDashboardService dashboard;
    @Autowired SpaAppointmentCheckoutService checkout;
    @Autowired PaymentWebhookService webhook;
    @Autowired OrderService physicalOrders;
    @Autowired OrderExpirationService expiration;
    @Autowired OrderRefundService refunds;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entities;
    @Autowired MockMvc mvc;
    @Value("${sepay.bank.account-number}") String bankAccount;

    record Fixture(User customer, User other, User receptionist, User admin, long appointmentId, long ticketId, long serviceId) { }

    <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }
    User user(String roleName) {
        Role role = roles.findByName(roleName).orElseGet(() -> roles.save(Role.builder().name(roleName).build()));
        String unique = UUID.randomUUID().toString();
        return users.save(User.builder().username(unique).email(unique + "@example.test").fullName("Visit customer")
                .roles(List.of(role)).build());
    }
    UsernamePasswordAuthenticationToken auth(User user) {
        var principal = UserDetailsImpl.build(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
    <T> T as(User user, Supplier<T> action) {
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth(user)); SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    Fixture fixture(boolean free) {
        return tx(() -> {
            User customer = user("ROLE_CUSTOMER"), other = user("ROLE_CUSTOMER"), receptionist = user("ROLE_STAFF"), admin = user("ROLE_ADMIN");
            BeautyService service = services.save(BeautyService.builder().name("Original service name").slug(UUID.randomUUID().toString())
                    .durationMinutes(30).basePrice(BigDecimal.valueOf(999)).build());
            ServicePackage pack = packages.save(ServicePackage.builder().name("Previously paid ticket package").price(BigDecimal.valueOf(500)).build());
            Order packageOrder = orders.save(Order.builder().orderNumber("ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                    .userId(customer.getId()).servicePackageId(pack.getId()).customerName("Visit customer").customerPhone("0987654321")
                    .shippingAddress("SPA_SERVICE").paymentMethod(PaymentMethod.BANK).status(OrderStatus.PROCESSING)
                    .paymentStatus(PaymentStatus.PAID).paidAt(Instant.now()).subTotal(BigDecimal.valueOf(500))
                    .totalAmount(BigDecimal.valueOf(500)).paidAmount(BigDecimal.valueOf(500)).items(new ArrayList<>())
                    .statusHistories(new ArrayList<>()).build());
            UserServiceTicket ticket = UserServiceTicket.builder().userId(customer.getId()).servicePackage(pack).orderId(packageOrder.getId())
                    .totalSessions(2).usedSessions(1).expiryDate(Instant.now().plusSeconds(86400)).build();
            ticket.getEntitlements().put(service.getId(), new TicketEntitlement(2, 1)); tickets.save(ticket);
            Appointment appointment = Appointment.builder().userId(customer.getId()).appointmentDate(LocalDate.now().minusDays(1))
                    .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(12, 0)).status(AppointmentStatus.COMPLETED)
                    .actualCompletedAt(Instant.now()).items(new ArrayList<>()).build();
            appointment.getItems().add(item(appointment, service, free ? "0.00" : "100.25", AppointmentItemExecutionStatus.PERFORMED, null));
            appointment.getItems().add(item(appointment, service, free ? "0.00" : "200.25", AppointmentItemExecutionStatus.PERFORMED, null));
            appointment.getItems().add(item(appointment, service, "0.00", AppointmentItemExecutionStatus.PERFORMED, ticket));
            appointment.getItems().add(item(appointment, service, "900.00", AppointmentItemExecutionStatus.SKIPPED, null));
            appointments.saveAndFlush(appointment);
            return new Fixture(customer, other, receptionist, admin, appointment.getId(), ticket.getId(), service.getId());
        });
    }
    AppointmentItem item(Appointment appointment, BeautyService service, String price, AppointmentItemExecutionStatus status, UserServiceTicket ticket) {
        return AppointmentItem.builder().appointment(appointment).service(service).serviceNameSnapshot("Booked service name")
                .price(new BigDecimal(price)).executionStatus(status).ticket(ticket)
                .ticketUsageState(ticket == null ? TicketUsageState.NONE : TicketUsageState.REDEEMED)
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(10, 30)).build();
    }
    SpaVisitInvoiceResult invoice(Fixture fixture, PaymentMethod method) {
        return as(fixture.receptionist(), () -> checkout.create(fixture.appointmentId(), new CreateSpaVisitInvoiceRequest(method, "Approved completed visit"), "visit-invoice"));
    }
    SpaVisitInvoiceResult cash(Fixture fixture, String amount, String key) {
        return as(fixture.receptionist(), () -> checkout.collectCash(fixture.appointmentId(), new SpaCashReceiptRequest(new BigDecimal(amount)), key));
    }
    void bank(SpaVisitInvoiceResult invoice, String amount, String key) {
        var request = new SePayWebhookRequest(); request.setReferenceCode(key); request.setAccountNumber(bankAccount);
        request.setGateway("CASH"); // Provider metadata must not spoof the trusted ledger source.
        request.setTransferType("in"); request.setContent("PAY " + invoice.orderNumber()); request.setTransferAmount(new BigDecimal(amount));
        webhook.processSePayWebhook(request);
    }
    long receiptCount(String number) {
        return receipts.count((root, query, cb) -> cb.equal(root.get("orderNumber"), number));
    }
    <T> List<T> concurrently(Supplier<T> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            Callable<T> task = () -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException(); return action.get(); };
            var first = executor.submit(task); var second = executor.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentInvoiceCreationUsesPerformedPriceSnapshotsAndRoundsTheTotalExactlyOnce() throws Exception {
        Fixture fixture = fixture(false);
        var invoices = concurrently(() -> invoice(fixture, PaymentMethod.BANK));
        SpaVisitInvoiceResult invoice = invoices.getFirst();
        assertEquals(invoice.orderId(), invoices.getLast().orderId());
        assertEquals(2, invoice.items().size()); assertEquals(new BigDecimal("300.50"), invoice.subTotal());
        assertEquals(0, new BigDecimal("301").compareTo(invoice.totalAmount()));
        assertTrue(invoice.items().stream().allMatch(item -> item.serviceName().equals("Booked service name")));
        assertTrue(invoice.paymentInstruction().getQrCodeUrl().contains("amount=301"));
        assertEquals(bankAccount, invoice.paymentInstruction().getBankAccountNumber());
        assertEquals(invoice.orderNumber(), invoice.paymentInstruction().getTransferSyntax());
        assertTrue(invoice.paymentInstruction().getQrCodeUrl().contains("-" + bankAccount + "-compact.jpg"));
        tx(() -> {
            Order stored = orders.findByAppointmentId(fixture.appointmentId()).orElseThrow();
            assertTrue(stored.getItems().isEmpty()); assertEquals(2, stored.getSpaVisitItems().size());
            assertNull(stored.getServicePackageId()); assertNull(stored.getPaymentDeadline());
            assertEquals(invoice.orderId(), appointments.findById(fixture.appointmentId()).orElseThrow().getOrderId());
            services.findById(fixture.serviceId()).orElseThrow().setBasePrice(BigDecimal.valueOf(10000)); return null;
        });
        assertEquals(0, invoice(fixture, PaymentMethod.BANK).totalAmount().compareTo(invoice.totalAmount()));
        assertThrows(BusinessException.class, () -> invoice(fixture, PaymentMethod.CASH));
        assertEquals(1, tickets.findById(fixture.ticketId()).orElseThrow().getUsedSessions());
    }

    @Test
    void partialBankTransferAndCashBalanceSettleWithoutShippingStockOrIssuingAnotherTicket() {
        var baseline = dashboard.spaFinancials();
        Fixture fixture = fixture(false);
        var unbilled = dashboard.spaFinancials();
        assertEquals(baseline.unbilledCompletedVisits() + 1, unbilled.unbilledCompletedVisits());
        assertEquals(0, baseline.unbilledAmount().add(new BigDecimal("301")).compareTo(unbilled.unbilledAmount()));
        assertEquals(baseline.outstandingInvoices(), unbilled.outstandingInvoices());
        assertEquals(baseline.legacyVisitsRequiringReconciliation(), unbilled.legacyVisitsRequiringReconciliation());
        SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.BANK);
        var billed = dashboard.spaFinancials();
        assertEquals(baseline.unbilledCompletedVisits(), billed.unbilledCompletedVisits());
        assertEquals(0, baseline.unbilledAmount().compareTo(billed.unbilledAmount()));
        assertEquals(baseline.outstandingInvoices() + 1, billed.outstandingInvoices());
        assertEquals(0, baseline.outstandingAmount().add(new BigDecimal("301")).compareTo(billed.outstandingAmount()));
        long ticketsBefore = tickets.count();
        String bankKey = UUID.randomUUID().toString(); bank(invoice, "100", bankKey); bank(invoice, "100", bankKey);
        SpaVisitInvoiceResult partial = as(fixture.customer(), () -> checkout.get(fixture.appointmentId()));
        assertEquals(PaymentStatus.PENDING, partial.paymentStatus()); assertEquals(0, new BigDecimal("201").compareTo(partial.amountDue()));
        assertTrue(partial.paymentInstruction().getQrCodeUrl().contains("amount=201"));
        var partlyCollected = dashboard.spaFinancials();
        assertEquals(baseline.outstandingInvoices() + 1, partlyCollected.outstandingInvoices());
        assertEquals(0, baseline.outstandingAmount().add(new BigDecimal("201")).compareTo(partlyCollected.outstandingAmount()));
        SpaVisitInvoiceResult paid = cash(fixture, "201", "cash-balance");
        assertEquals(PaymentStatus.PAID, paid.paymentStatus()); assertEquals(OrderStatus.COMPLETED, paid.status()); assertNotNull(paid.paidAt());
        assertNull(paid.paymentInstruction()); assertEquals(paid.paidAt(), cash(fixture, "201", "cash-balance").paidAt());
        var settled = dashboard.spaFinancials();
        assertEquals(baseline.outstandingInvoices(), settled.outstandingInvoices());
        assertEquals(0, baseline.outstandingAmount().compareTo(settled.outstandingAmount()));
        assertEquals(baseline.unbilledCompletedVisits(), settled.unbilledCompletedVisits());
        assertEquals(0, baseline.unbilledAmount().compareTo(settled.unbilledAmount()));
        assertThrows(BusinessException.class, () -> cash(fixture, "200", "cash-balance"));
        assertThrows(BusinessException.class, () -> cash(fixture, "1", "excess-cash"));
        assertEquals(2, receiptCount(invoice.orderNumber())); assertEquals(ticketsBefore, tickets.count());
        assertEquals("SEPAY", receipts.findByReferenceCode(bankKey).orElseThrow().getGateway());
        assertEquals(1, tickets.findById(fixture.ticketId()).orElseThrow().getUsedSessions());
        tx(() -> {
            assertEquals(0L, (long) entities.createQuery("select count(a) from StockAllocation a where a.orderNumber = :number", Long.class)
                    .setParameter("number", invoice.orderNumber()).getSingleResult()); return null;
        });
    }

    @Test
    void bankContentWithoutHyphenMatchesInvoiceAndDuplicateDoesNotCollectTwice() {
        Fixture fixture = fixture(false); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.BANK);
        var request = new SePayWebhookRequest(); request.setReferenceCode(UUID.randomUUID().toString());
        request.setAccountNumber(bankAccount); request.setTransferType("in"); request.setTransferAmount(new BigDecimal("301"));
        request.setContent("150717141386-" + invoice.orderNumber().replace("ORD-", "ORD") + "-CHUYEN TIEN-MOMO");
        webhook.processSePayWebhook(request); webhook.processSePayWebhook(request);
        SpaVisitInvoiceResult paid = as(fixture.customer(), () -> checkout.get(fixture.appointmentId()));
        assertEquals(PaymentStatus.PAID, paid.paymentStatus());
        assertEquals(0, new BigDecimal("301").compareTo(paid.paidAmount()));
        assertEquals(invoice.orderNumber(), receipts.findByReferenceCode(request.getReferenceCode()).orElseThrow().getOrderNumber());
        assertEquals(1, receiptCount(invoice.orderNumber()));
    }

    @Test
    void concurrentReplayRecoversIgnoredReceiptUsingOriginalPayloadOnly() throws Exception {
        Fixture fixture = fixture(false); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.BANK);
        String key = UUID.randomUUID().toString();
        var original = new SePayWebhookRequest(); original.setReferenceCode(key); original.setAccountNumber(bankAccount);
        original.setTransferType("in"); original.setTransferAmount(new BigDecimal("301"));
        original.setContent("PAY " + invoice.orderNumber().replace("ORD-", "ORD"));
        String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(original);
        tx(() -> receipts.saveAndFlush(PaymentTransaction.builder().referenceCode(key).orderNumber("UNKNOWN")
                .gateway("SEPAY").transferType("in").amount(new BigDecimal("301")).accountNumber(bankAccount)
                .content(original.getContent()).rawPayload(payload).status(TransactionStatus.IGNORED).build()));
        concurrently(() -> { bank(invoice, "999", key); return true; });
        SpaVisitInvoiceResult paid = as(fixture.customer(), () -> checkout.get(fixture.appointmentId()));
        assertEquals(PaymentStatus.PAID, paid.paymentStatus());
        assertEquals(0, new BigDecimal("301").compareTo(paid.paidAmount()));
        assertEquals(TransactionStatus.SUCCESS, receipts.findByReferenceCode(key).orElseThrow().getStatus());
        assertEquals(1, receiptCount(invoice.orderNumber()));
    }

    @Test
    void concurrentCashRetryCreatesOneLedgerReceiptAndOnePaidAmount() throws Exception {
        Fixture fixture = fixture(false); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.CASH);
        var outcomes = concurrently(() -> cash(fixture, "301", "same-cash-receipt"));
        assertTrue(outcomes.stream().allMatch(result -> result.paymentStatus() == PaymentStatus.PAID));
        assertTrue(outcomes.stream().allMatch(result -> result.paidAmount().compareTo(new BigDecimal("301")) == 0));
        assertEquals(1, receiptCount(invoice.orderNumber()));
        assertEquals("CASH", receipts.findByOrderNumberOrderByCreatedAtDesc(invoice.orderNumber(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().getFirst().getGateway());
    }

    @Test
    void zeroNonTicketBillIsPaidWithoutCashReceiptOrBankInstructions() {
        Fixture fixture = fixture(true); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.BANK);
        assertEquals(PaymentStatus.PAID, invoice.paymentStatus()); assertNotNull(invoice.paidAt());
        assertEquals(0, invoice.totalAmount().signum()); assertNull(invoice.paymentInstruction()); assertEquals(2, invoice.items().size());
        assertEquals(0, receiptCount(invoice.orderNumber()));
        assertThrows(BusinessException.class, () -> cash(fixture, "1", "zero-excess"));
        assertEquals(0, receiptCount(invoice.orderNumber()));
    }

    @Test
    void competingDifferentCashReceiptsCannotOvercollectTheSameBalance() throws Exception {
        Fixture fixture = fixture(false); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.CASH);
        var outcomes = concurrently(() -> {
            try { cash(fixture, "301", UUID.randomUUID().toString()); return true; }
            catch (BusinessException expectedBalanceRejection) { return false; }
        });
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, receiptCount(invoice.orderNumber()));
        assertEquals(0, new BigDecimal("301").compareTo(orders.findById(invoice.orderId()).orElseThrow().getPaidAmount()));
    }

    @Test
    void fractionalIncomingVndIsFailedWithoutRoundingMoneyOrIncludingItInReceivedSummary() {
        Fixture fixture = fixture(false); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.BANK);
        var before = as(fixture.admin(), () -> paymentAdmin.summary(null, null).getData());
        String fractionalKey = UUID.randomUUID().toString(); bank(invoice, "100.10", fractionalKey);
        PaymentTransaction invalid = receipts.findByReferenceCode(fractionalKey).orElseThrow();
        assertEquals(TransactionStatus.FAILED, invalid.getStatus());
        assertEquals(0, new BigDecimal("100.10").compareTo(invalid.getAmount()));
        assertEquals(0, orders.findById(invoice.orderId()).orElseThrow().getPaidAmount().signum());
        var rejectedSummary = as(fixture.admin(), () -> paymentAdmin.summary(null, null).getData());
        assertEquals(before.failedTransactions() + 1, rejectedSummary.failedTransactions());
        assertEquals(0, before.receivedAmount().compareTo(rejectedSummary.receivedAmount()));
        String wholeKey = UUID.randomUUID().toString(); bank(invoice, "100.00", wholeKey);
        assertEquals(TransactionStatus.PARTIALLY_PAID, receipts.findByReferenceCode(wholeKey).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(orders.findById(invoice.orderId()).orElseThrow().getPaidAmount()));
        var acceptedSummary = as(fixture.admin(), () -> paymentAdmin.summary(null, null).getData());
        assertEquals(0, before.receivedAmount().add(new BigDecimal("100")).compareTo(acceptedSummary.receivedAmount()));
        assertEquals(0, before.bankAmount().add(new BigDecimal("100")).compareTo(acceptedSummary.bankAmount()));
        assertEquals(0, before.cashAmount().compareTo(acceptedSummary.cashAmount()));
    }

    @Test
    void customerCanReadOnlyTheirInvoiceAndCannotCreateOrConfirmCash() throws Exception {
        Fixture fixture = fixture(false); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.BANK);
        String route = "/api/v1/appointments/" + fixture.appointmentId() + "/invoice";
        mvc.perform(get(route).with(authentication(auth(fixture.customer())))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(invoice.orderId())).andExpect(jsonPath("$.data.amountDue").value(301));
        mvc.perform(get(route).with(authentication(auth(fixture.other())))).andExpect(status().isForbidden());
        mvc.perform(get(route).with(authentication(auth(fixture.receptionist())))).andExpect(status().isOk());
        mvc.perform(post(route).with(authentication(auth(fixture.customer()))).header("Idempotency-Key", "unauthorized")
                .contentType("application/json").content("{\"paymentMethod\":\"BANK\"}")).andExpect(status().isForbidden());
        mvc.perform(post(route + "/cash-receipts").with(authentication(auth(fixture.customer()))).header("Idempotency-Key", "unauthorized")
                .contentType("application/json").content("{\"amount\":301}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/appointments/checkout-policy").with(authentication(auth(fixture.customer())))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.depositsEnabled").value(false)).andExpect(jsonPath("$.data.noShowFeesEnabled").value(false));
    }

    @Test
    void unfinishedOrUnreconciledLegacyAppointmentsCannotBeBilled() {
        Fixture fixture = fixture(false);
        tx(() -> { appointments.findById(fixture.appointmentId()).orElseThrow().setStatus(AppointmentStatus.IN_PROGRESS); return null; });
        assertThrows(BusinessException.class, () -> invoice(fixture, PaymentMethod.BANK));
        tx(() -> {
            Appointment visit = appointments.findById(fixture.appointmentId()).orElseThrow(); visit.setStatus(AppointmentStatus.COMPLETED);
            visit.getItems().forEach(item -> item.setExecutionStatus(AppointmentItemExecutionStatus.LEGACY_FINALIZED)); return null;
        });
        assertThrows(BusinessException.class, () -> invoice(fixture, PaymentMethod.BANK));
        assertTrue(orders.findByAppointmentId(fixture.appointmentId()).isEmpty());
    }

    @Test
    void visitInvoiceDoesNotEnterPhysicalShippingOrTimeoutCancellation() {
        Fixture fixture = fixture(false); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.BANK);
        var shipping = new UpdateOrderStatusRequest(); shipping.setStatus(OrderStatus.SHIPPED);
        assertThrows(BusinessException.class, () -> physicalOrders.updateOrderStatus(invoice.orderId(), shipping));
        tx(() -> { orders.findById(invoice.orderId()).orElseThrow().setPaymentDeadline(Instant.now().minusSeconds(3600)); return null; });
        assertFalse(expiration.expire(invoice.orderId()));
        bank(invoice, "301", UUID.randomUUID().toString());
        assertEquals(OrderStatus.COMPLETED, orders.findById(invoice.orderId()).orElseThrow().getStatus());
        var physical = new CheckoutRequest(); physical.setPaymentMethod(PaymentMethod.CASH);
        assertThrows(BusinessException.class, () -> physicalOrders.checkout(null, physical));
    }

    @Test
    void explicitFullRefundPreservesPaidAtAndFutureBankMoneyRemainsAwaitingRefund() {
        Fixture fixture = fixture(false); SpaVisitInvoiceResult invoice = invoice(fixture, PaymentMethod.CASH);
        SpaVisitInvoiceResult paid = cash(fixture, "301", "collected");
        assertThrows(BusinessException.class, () -> as(fixture.admin(), () -> { refunds.confirm(invoice.orderId(),
                new ConfirmRefundRequest(UUID.randomUUID().toString(), new BigDecimal("301"))); return null; }));
        var approval = new ConfirmRefundRequest(UUID.randomUUID().toString(), new BigDecimal("301"), "Manager approved a full discretionary refund");
        as(fixture.admin(), () -> { refunds.confirm(invoice.orderId(), approval); refunds.confirm(invoice.orderId(), approval); return null; });
        SpaVisitInvoiceResult refunded = as(fixture.customer(), () -> checkout.get(fixture.appointmentId()));
        assertEquals(PaymentStatus.REFUNDED, refunded.paymentStatus()); assertEquals(paid.paidAt(), refunded.paidAt());
        assertEquals(0, new BigDecimal("301").compareTo(refunded.refundedAmount()));
        assertThrows(BusinessException.class, () -> cash(fixture, "1", "after-refund"));
        bank(invoice, "5", UUID.randomUUID().toString());
        Order latest = orders.findById(invoice.orderId()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_PENDING, latest.getPaymentStatus()); assertEquals(paid.paidAt(), latest.getPaidAt());
        assertTrue(receipts.findByOrderNumberOrderByCreatedAtDesc(invoice.orderNumber(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().anyMatch(receipt -> receipt.getStatus() == TransactionStatus.CANCELLED_ORDER_RECEIVED));
    }
}
