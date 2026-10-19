package com.core.beautyshop;

import com.core.beautyshop.modules.crm.application.CrmWorkService;
import com.core.beautyshop.modules.crm.application.CustomerCareService;
import com.core.beautyshop.modules.crm.domain.CustomerCareNoteRepository;
import com.core.beautyshop.modules.identity.api.StaffPermissionService;
import com.core.beautyshop.modules.identity.application.service.LoyaltyReconciliationService;
import com.core.beautyshop.modules.identity.domain.*;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.modules.spa.application.service.SpaLoyaltyService;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentItemExecutionStatus;
import com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus;
import com.core.beautyshop.modules.spa.domain.enums.TicketUsageState;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Same isolated context as CustomerAccountIntegrityIntegrationTest, distinct fixture IDs. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:customer_account_integrity;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "app.mail.enabled=false"
})
@ActiveProfiles("test")
class CrmLoyaltyIntegrityIntegrationTest {
    @Autowired CrmWorkService work;
    @Autowired CustomerCareService care;
    @Autowired CustomerCareNoteRepository notes;
    @Autowired StaffPermissionService permissions;
    @Autowired LoyaltyReconciliationService loyalty;
    @Autowired SpaLoyaltyService spaLoyalty;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired LoyaltyPointAwardRepository awards;
    @Autowired OrderRepository orders;
    @Autowired ProductReturnRepository returns;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired(required = false) com.core.beautyshop.modules.order.application.service.SpaVisitOrderService spaVisitOrderService;
    @Autowired(required = false) com.core.beautyshop.modules.order.api.OrderFacade orderFacade;

    @AfterEach void clearPrincipal() { SecurityContextHolder.clearContext(); }
    <T> T tx(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }
    <T> T as(User owner, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext(); var context = SecurityContextHolder.createEmptyContext();
        var principal = UserDetailsImpl.build(owner);
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    User user(String roleName) {
        return tx(() -> {
            var role = roles.findByName(roleName).orElseGet(() -> roles.saveAndFlush(Role.builder().name(roleName).build()));
            String suffix = UUID.randomUUID().toString();
            return users.saveAndFlush(User.builder().username(suffix).email(suffix + "@example.test").fullName("CRM loyalty fixture")
                .passwordHash("unused").roles(new ArrayList<>(List.of(role))).build());
        });
    }
    int points(User customer) { return users.findById(customer.getId()).orElseThrow().getLoyaltyPoints(); }
    Order order(Long customerId, String total, String paid, String shipping, OrderStatus status) {
        BigDecimal amount = new BigDecimal(total);
        return orders.saveAndFlush(Order.builder().orderNumber("CRM-" + UUID.randomUUID()).userId(customerId)
            .customerName("Fixture").customerPhone("0912345678").shippingAddress("Fixture address").paymentMethod(PaymentMethod.BANK)
            .status(status).paymentStatus(PaymentStatus.PENDING).subTotal(amount.subtract(new BigDecimal(shipping)))
            .shippingFee(new BigDecimal(shipping)).totalAmount(amount).paidAmount(new BigDecimal(paid)).build());
    }
    CrmWorkService.View readWork(Long customerId, Long id) {
        return work.list(null, null, null, null, customerId, 0, 100).getContent().stream().filter(row -> id.equals(row.id())).findFirst().orElseThrow();
    }

    @Test void followUpAndComplaintNotesCreateActionableWorkOnceWhileGeneralNotesStayNotes() {
        var customer = user("ROLE_CUSTOMER"); var admin = user("ROLE_ADMIN");
        Instant due = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var followUp = as(admin, () -> care.add(customer.getId(), new CustomerCareService.NoteCommand("GENERAL", "Call after treatment", null, null, null, due)));
        var complaint = as(admin, () -> care.add(customer.getId(), new CustomerCareService.NoteCommand("COMPLAINT", "Customer reports a delivery problem", null, null, null, null)));
        as(admin, () -> care.add(customer.getId(), new CustomerCareService.NoteCommand("GENERAL", "Informational note", null, null, null, null)));
        var rows = as(admin, () -> work.list(null, null, null, null, customer.getId(), 0, 20));
        assertEquals(2, rows.getTotalElements());
        assertTrue(rows.getContent().stream().anyMatch(row -> "FOLLOW_UP".equals(row.kind()) && due.equals(row.dueAt()) && row.overdue()));
        assertTrue(rows.getContent().stream().anyMatch(row -> "COMPLAINT".equals(row.kind()) && row.dueAt() == null));
        as(admin, () -> { work.fromNote(notes.findById(followUp.id()).orElseThrow()); work.fromNote(notes.findById(complaint.id()).orElseThrow()); return null; });
        assertEquals(2, as(admin, () -> work.list(null, null, null, null, customer.getId(), 0, 20)).getTotalElements());
    }

    @Test void dueQueueSupportsSelfAssignmentRequiresOutcomeAndRejectsStaleUpdates() {
        var customer = user("ROLE_CUSTOMER"); var admin = user("ROLE_ADMIN"); var agent = user("ROLE_STAFF");
        as(admin, () -> permissions.replace(agent.getId(), Set.of("CRM")));
        Instant due = Instant.now().minusSeconds(120);
        var created = as(admin, () -> work.create(new CrmWorkService.Create(customer.getId(), "FOLLOW_UP", "Call customer", "Review customer feedback", null, due)));
        assertTrue(created.overdue()); assertEquals("OPEN", created.status());
        assertEquals(1, as(agent, () -> work.list(null, null, Instant.now(), null, customer.getId(), 0, 20)).getTotalElements());
        var assigned = as(agent, () -> work.update(created.id(), new CrmWorkService.Update("IN_PROGRESS", agent.getId(), due, null, created.version())));
        assertEquals(agent.getId(), assigned.assigneeId()); assertTrue(assigned.version() > created.version());
        assertThrows(BusinessException.class, () -> as(agent, () -> work.update(created.id(), new CrmWorkService.Update("OPEN", agent.getId(), due, null, created.version()))));
        assertThrows(BusinessException.class, () -> as(agent, () -> work.update(created.id(), new CrmWorkService.Update("DONE", agent.getId(), due, " ", assigned.version()))));
        assertEquals("IN_PROGRESS", as(agent, () -> readWork(customer.getId(), created.id())).status());
        var done = as(agent, () -> work.update(created.id(), new CrmWorkService.Update("DONE", agent.getId(), due, "Customer contacted and follow-up agreed", assigned.version())));
        assertEquals("DONE", done.status()); assertNotNull(done.completedAt()); assertFalse(done.overdue());
        assertTrue(as(agent, () -> work.list(null, null, Instant.now(), agent.getId(), customer.getId(), 0, 20)).getContent().isEmpty());
        assertEquals(1, as(agent, () -> work.list("DONE", null, null, agent.getId(), customer.getId(), 0, 20)).getTotalElements());
    }

    @Test void onlyAdminMayAssignAnotherCrmAgentAndNonCrmStaffCannotBeAssigned() {
        var customer = user("ROLE_CUSTOMER"); var admin = user("ROLE_ADMIN"); var one = user("ROLE_STAFF"); var two = user("ROLE_STAFF"); var catalog = user("ROLE_STAFF");
        as(admin, () -> permissions.replace(one.getId(), Set.of("CRM")));
        as(admin, () -> permissions.replace(two.getId(), Set.of("CRM")));
        as(admin, () -> permissions.replace(catalog.getId(), Set.of("CATALOG")));
        assertThrows(BusinessException.class, () -> as(one, () -> work.create(new CrmWorkService.Create(customer.getId(), "COMPLAINT", "Resolve", "Description", two.getId(), null))));
        assertThrows(BusinessException.class, () -> as(admin, () -> work.create(new CrmWorkService.Create(customer.getId(), "COMPLAINT", "Resolve", "Description", catalog.getId(), null))));
        var assigned = as(admin, () -> work.create(new CrmWorkService.Create(customer.getId(), "COMPLAINT", "Resolve", "Description", two.getId(), null)));
        assertEquals(two.getId(), assigned.assigneeId());
        assertThrows(BusinessException.class, () -> work.list(null, null, null, null, null, -1, 20));
        assertThrows(BusinessException.class, () -> work.list(null, "INVALID", null, null, null, 0, 20));
    }

    @Test void deliveredProductAwardsUseActualPaidValueAndReceivedReturnsRemainCappedAfterExtraTransfer() {
        var customer = user("ROLE_CUSTOMER");
        var order = order(customer.getId(), "230000", "80000", "30000", OrderStatus.DELIVERED);
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(8, points(customer));
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(8, points(customer));
        assertEquals(1, awards.findAll().stream().filter(row -> row.getOrderId().equals(order.getId())).count());
        tx(() -> { orders.findById(order.getId()).orElseThrow().setPaidAmount(new BigDecimal("230000")); return null; });
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(20, points(customer));
        var returned = new ProductReturn(); returned.setOrderId(order.getId()); returned.setStatus("RECEIVED"); returned.setReason("One product returned");
        returned.setRefundAmount(new BigDecimal("50000")); returned.setLinesJson("[]"); returned.setEvidenceJson("[]"); returns.saveAndFlush(returned);
        // Physical receipt reduces eligible spend before a manual refund is recorded.
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(15, points(customer));
        tx(() -> { var row = orders.findById(order.getId()).orElseThrow(); row.setRefundedAmount(new BigDecimal("50000")); row.setPaidAmount(new BigDecimal("300000")); return null; });
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(15, points(customer));
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(15, points(customer));
        assertEquals(15, awards.findByOrderId(order.getId()).orElseThrow().getPoints());
    }

    @Test void completedSpaInvoiceAwardsPartialActualPaymentAndRefundsWhileGuestAndForeignOwnerEarnNothing() {
        var customer = user("ROLE_CUSTOMER"); var other = user("ROLE_CUSTOMER");
        var appointment = tx(() -> {
            var row = Appointment.builder().userId(customer.getId()).appointmentDate(LocalDate.now()).startTime(LocalTime.of(9,0)).endTime(LocalTime.of(10,0)).build(); em.persist(row); return row;
        });
        var invoice = order(customer.getId(), "200000", "60000", "0", OrderStatus.COMPLETED);
        tx(() -> { orders.findById(invoice.getId()).orElseThrow().setAppointmentId(appointment.getId()); return null; });
        loyalty.reconcile(invoice.getId(), customer.getId(), new BigDecimal("200000")); assertEquals(6, points(customer));
        tx(() -> { orders.findById(invoice.getId()).orElseThrow().setRefundedAmount(new BigDecimal("20000")); return null; });
        loyalty.reconcile(invoice.getId(), customer.getId(), new BigDecimal("200000")); assertEquals(4, points(customer));
        loyalty.reconcile(invoice.getId(), other.getId(), new BigDecimal("200000")); assertEquals(0, points(other));
        var guest = order(null, "100000", "100000", "0", OrderStatus.DELIVERED);
        loyalty.reconcile(guest.getId(), null, guest.getTotalAmount());
        loyalty.reconcile(guest.getId(), customer.getId(), guest.getTotalAmount());
        assertTrue(awards.findByOrderId(guest.getId()).isEmpty()); assertEquals(4, points(customer));
    }

    @Test void unfulfilledProductAndFullReturnReconcileToZeroWithoutReawardingOnRepeatedEvents() {
        var customer = user("ROLE_CUSTOMER"); var order = order(customer.getId(), "100000", "100000", "0", OrderStatus.PROCESSING);
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(0, points(customer));
        tx(() -> { orders.findById(order.getId()).orElseThrow().setStatus(OrderStatus.DELIVERED); return null; });
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(10, points(customer));
        tx(() -> { orders.findById(order.getId()).orElseThrow().setStatus(OrderStatus.RETURNED); return null; });
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(0, points(customer));
        loyalty.reconcile(order.getId(), customer.getId(), order.getTotalAmount()); assertEquals(0, points(customer));
        assertTrue(awards.findByOrderId(order.getId()).orElseThrow().isReversed());
    }

    @Test void packagePointsUseOriginalPurchaseSnapshotAndPerformedRedeemedItemsExcludeForfeitedQuota() {
        var customer = user("ROLE_CUSTOMER");
        var service = tx(() -> { var row = BeautyService.builder().name("Performed service").slug(UUID.randomUUID().toString()).basePrice(new BigDecimal("100000")).durationMinutes(30).build(); em.persist(row); return row; });
        var pack = tx(() -> { var row = ServicePackage.builder().name("Original package").price(new BigDecimal("200000")).build(); em.persist(row); return row; });
        var purchase = order(customer.getId(), "200000", "200000", "0", OrderStatus.PROCESSING);
        var ticket = tx(() -> {
            orders.findById(purchase.getId()).orElseThrow().setServicePackageId(pack.getId());
            var snapshot = new SpaPurchaseSnapshot(); snapshot.setOrderId(purchase.getId()); snapshot.setPackageId(pack.getId()); snapshot.getEntitlements().put(service.getId(), 2); em.persist(snapshot);
            var row = UserServiceTicket.builder().userId(customer.getId()).servicePackage(pack).orderId(purchase.getId()).totalSessions(2).usedSessions(2).build(); em.persist(row);
            var appointment = Appointment.builder().userId(customer.getId()).appointmentDate(LocalDate.now()).startTime(LocalTime.of(10,0)).endTime(LocalTime.of(11,0)).status(AppointmentStatus.COMPLETED).build(); em.persist(appointment);
            em.persist(AppointmentItem.builder().appointment(appointment).service(service).ticket(row).executionStatus(AppointmentItemExecutionStatus.PERFORMED).ticketUsageState(TicketUsageState.REDEEMED).build());
            em.persist(AppointmentItem.builder().appointment(appointment).service(service).ticket(row).executionStatus(AppointmentItemExecutionStatus.NO_SHOW).ticketUsageState(TicketUsageState.FORFEITED).build());
            return row;
        });
        spaLoyalty.performedTicket(ticket); assertEquals(10, points(customer));
        spaLoyalty.performedTicket(ticket); assertEquals(10, points(customer));
        // Catalog edits and compensated quota cannot rewrite the original paid denominator.
        tx(() -> { em.find(ServicePackage.class, pack.getId()).setPrice(new BigDecimal("999999")); em.find(UserServiceTicket.class, ticket.getId()).setTotalSessions(20); return null; });
        spaLoyalty.performedTicket(ticket); assertEquals(10, points(customer));
        assertEquals(10, awards.findByOrderId(purchase.getId()).orElseThrow().getPoints());
    }

    @Test void spaVisitAndPackageOrdersApplyMembershipTierDiscount() {
        var goldCustomer = tx(() -> {
            var role = roles.findByName("ROLE_CUSTOMER").orElseGet(() -> roles.saveAndFlush(Role.builder().name("ROLE_CUSTOMER").build()));
            String suffix = UUID.randomUUID().toString();
            return users.saveAndFlush(User.builder().username(suffix).email(suffix + "@example.test").fullName("Gold VIP customer")
                    .passwordHash("unused").roles(new ArrayList<>(List.of(role)))
                    .membershipTier(com.core.beautyshop.modules.identity.domain.enums.MembershipTier.GOLD)
                    .loyaltyPoints(5500).build());
        });
        var admin = user("ROLE_ADMIN");

        // 1. Spa visit invoice applies 10% discount for GOLD tier
        var visitCommand = new com.core.beautyshop.modules.order.api.dto.CreateSpaVisitOrderCommand(
                99991L, goldCustomer.getId(),
                List.of(new com.core.beautyshop.modules.order.api.dto.SpaVisitCharge(8881L, 7771L, "Gold facial", new BigDecimal("200000"))),
                PaymentMethod.BANK, "Gold VIP customer", "0912345678", "Gold test visit", UUID.randomUUID().toString()
        );
        var visitInvoice = as(admin, () -> spaVisitOrderService.create(visitCommand));
        assertEquals(0, new BigDecimal("200000").compareTo(visitInvoice.subTotal()));
        assertEquals(0, new BigDecimal("180000").compareTo(visitInvoice.totalAmount())); // 200k - 10% = 180k

        // 2. Spa package purchase applies 10% discount for GOLD tier
        var pack = tx(() -> {
            var row = ServicePackage.builder().name("Gold Spa Package").price(new BigDecimal("1000000")).build();
            em.persist(row); return row;
        });
        var packageCommand = com.core.beautyshop.modules.order.api.dto.CreateSpaPackageOrderCommand.builder()
                .userId(goldCustomer.getId())
                .servicePackageId(pack.getId())
                .packageName(pack.getName())
                .amount(new BigDecimal("1000000"))
                .customerName(goldCustomer.getFullName())
                .customerPhone("0912345678")
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        var packageResult = as(admin, () -> orderFacade.createSpaPackageOrder(packageCommand));
        assertEquals(0, new BigDecimal("900000").compareTo(packageResult.getTotalAmount())); // 1M - 10% = 900k
    }
}
