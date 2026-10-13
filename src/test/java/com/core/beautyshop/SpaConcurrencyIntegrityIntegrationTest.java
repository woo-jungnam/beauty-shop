package com.core.beautyshop;

import com.core.beautyshop.modules.identity.domain.*;
import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.modules.order.domain.enums.*;
import com.core.beautyshop.modules.spa.application.dto.request.*;
import com.core.beautyshop.modules.spa.application.service.*;
import com.core.beautyshop.modules.spa.domain.*;
import com.core.beautyshop.modules.spa.domain.enums.*;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** The MySQL subclass executes the same locks/transactions against the migrated production schema. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spa_concurrency;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@ActiveProfiles("test")
class SpaConcurrencyIntegrityIntegrationTest {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired StaffRepository staffs;
    @Autowired StaffServiceSkillRepository skills;
    @Autowired StaffScheduleRepository shifts;
    @Autowired BeautyServiceRepository services;
    @Autowired ServicePackageRepository packages;
    @Autowired UserServiceTicketRepository tickets;
    @Autowired OrderRepository orders;
    @Autowired AppointmentRepository appointmentRows;
    @Autowired AppointmentService appointments;
    @Autowired AdminStaffService administration;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;

    record Fixture(User customer, User otherCustomer, User admin, long serviceId, long firstStaffId,
                   long secondStaffId, long firstShiftId, long ticketId, LocalDate date) { }
    record Attempt(boolean accepted, String reason) { }

    <T> T tx(Supplier<T> action) {
        var template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template.execute(status -> action.get());
    }

    void assertReadCommitted() {
        try { assertEquals(Connection.TRANSACTION_READ_COMMITTED, DataSourceUtils.getConnection(dataSource).getTransactionIsolation()); }
        catch (SQLException failure) { throw new AssertionError(failure); }
    }

    <T> T as(User user, Supplier<T> action) {
        var principal = UserDetailsImpl.build(user);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try { return action.get(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    User user(String roleName) {
        Role role = roles.findByName(roleName).orElseGet(() -> roles.save(Role.builder().name(roleName).build()));
        String suffix = UUID.randomUUID().toString();
        return users.save(User.builder().username(suffix).email(suffix + "@example.test").fullName("Concurrency fixture")
                .roles(List.of(role)).build());
    }

    Fixture fixture() {
        return tx(() -> {
            User customer = user("ROLE_CUSTOMER"), other = user("ROLE_CUSTOMER"), admin = user("ROLE_ADMIN");
            BeautyService service = services.save(BeautyService.builder().name("Concurrency service")
                    .slug(UUID.randomUUID().toString()).basePrice(BigDecimal.valueOf(100))
                    .durationMinutes(30).preparationTimeMinutes(10).build());
            Staff first = staffs.save(Staff.builder().userId(user("ROLE_STAFF").getId()).skills(new ArrayList<>()).build());
            Staff second = staffs.save(Staff.builder().userId(user("ROLE_STAFF").getId()).skills(new ArrayList<>()).build());
            skills.save(StaffServiceSkill.builder().staff(first).service(service).isCertified(true).build());
            skills.save(StaffServiceSkill.builder().staff(second).service(service).isCertified(true).build());
            LocalDate date = LocalDate.now(SpaTimeRules.ZONE).plusDays(2);
            StaffSchedule firstShift = shifts.save(StaffSchedule.builder().staff(first).workDate(date)
                    .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(20, 0)).build());
            shifts.save(StaffSchedule.builder().staff(second).workDate(date).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(20, 0)).build());
            ServicePackage pack = packages.save(ServicePackage.builder().name("One session")
                    .price(BigDecimal.valueOf(100)).validityDays(30).build());
            Order paid = orders.save(Order.builder().orderNumber("ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                    .userId(customer.getId()).servicePackageId(pack.getId()).customerName(customer.getFullName())
                    .customerPhone("0987654321").shippingAddress("SPA_SERVICE").paymentMethod(PaymentMethod.BANK)
                    .paymentStatus(PaymentStatus.PAID).paidAt(Instant.now()).paidAmount(BigDecimal.valueOf(100))
                    .status(OrderStatus.PROCESSING).subTotal(BigDecimal.valueOf(100)).totalAmount(BigDecimal.valueOf(100))
                    .items(new ArrayList<>()).statusHistories(new ArrayList<>()).build());
            UserServiceTicket ticket = UserServiceTicket.builder().userId(customer.getId()).orderId(paid.getId())
                    .servicePackage(pack).totalSessions(1).expiryDate(Instant.now().plusSeconds(86400 * 30L)).build();
            ticket.getEntitlements().put(service.getId(), new TicketEntitlement(1, 0)); tickets.save(ticket);
            return new Fixture(customer, other, admin, service.getId(), first.getId(), second.getId(), firstShift.getId(), ticket.getId(), date);
        });
    }

    BookAppointmentRequest booking(Fixture fixture, long staffId, LocalTime time, Long ticketId) {
        var item = new AppointmentItemRequest(); item.setServiceId(fixture.serviceId()); item.setStaffId(staffId); item.setTicketId(ticketId);
        var request = new BookAppointmentRequest(); request.setAppointmentDate(fixture.date()); request.setStartTime(time); request.setItems(List.of(item));
        return request;
    }

    Attempt attempt(Supplier<?> action) {
        try { action.get(); return new Attempt(true, null); }
        catch (BusinessException expectedRejection) { return new Attempt(false, expectedRejection.getMessage()); }
    }

    List<Attempt> concurrently(Supplier<Attempt> first, Supplier<Attempt> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            Callable<Attempt> a = () -> { ready.countDown(); await(start); return first.get(); };
            Callable<Attempt> b = () -> { ready.countDown(); await(start); return second.get(); };
            var one = executor.submit(a); var two = executor.submit(b);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }

    void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent operation did not reach its barrier"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }

    @Test
    void simultaneousBookingsForSameStaffAndIntervalCommitExactlyOneAppointment() throws Exception {
        Fixture fixture = fixture();
        var outcomes = concurrently(
                () -> attempt(() -> as(fixture.customer(), () -> tx(() -> { assertReadCommitted(); return appointments.bookAppointment(booking(fixture, fixture.firstStaffId(), LocalTime.of(10, 0), null)); }))),
                () -> attempt(() -> as(fixture.otherCustomer(), () -> tx(() -> { assertReadCommitted(); return appointments.bookAppointment(booking(fixture, fixture.firstStaffId(), LocalTime.of(10, 0), null)); }))));
        assertEquals(1, outcomes.stream().filter(Attempt::accepted).count());
        assertEquals(1, (int) tx(() -> appointmentRows.findFutureAssignedItems(fixture.firstStaffId(), fixture.date()).size()));
    }

    @Test
    void simultaneousBookingsWithDifferentStaffCannotSpendTheLastTicketSessionTwice() throws Exception {
        Fixture fixture = fixture();
        var outcomes = concurrently(
                () -> attempt(() -> as(fixture.customer(), () -> tx(() -> { assertReadCommitted(); return appointments.bookAppointment(booking(fixture, fixture.firstStaffId(), LocalTime.of(10, 0), fixture.ticketId())); }))),
                () -> attempt(() -> as(fixture.customer(), () -> tx(() -> { assertReadCommitted(); return appointments.bookAppointment(booking(fixture, fixture.secondStaffId(), LocalTime.of(12, 0), fixture.ticketId())); }))));
        assertEquals(1, outcomes.stream().filter(Attempt::accepted).count());
        tx(() -> {
            var ticket = tickets.findById(fixture.ticketId()).orElseThrow();
            assertEquals(0, ticket.getUsedSessions()); assertEquals(1, ticket.getReservedSessions()); assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
            assertEquals(0, ticket.getEntitlements().get(fixture.serviceId()).getUsed());
            assertEquals(1, ticket.getEntitlements().get(fixture.serviceId()).getReserved());
            assertEquals(1, appointmentRows.findByUserId(fixture.customer().getId()).size()); return null;
        });
    }

    @Test
    void bookingWaitingForShiftDeletionRejectsTheDeletedShiftAfterCommit() throws Exception {
        Fixture fixture = fixture(); var deletedInsideTransaction = new CountDownLatch(1); var releaseCommit = new CountDownLatch(1);
        var bookingStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> as(fixture.admin(), () -> tx(() -> {
                assertReadCommitted(); administration.deleteSchedule(fixture.firstStaffId(), fixture.firstShiftId());
                deletedInsideTransaction.countDown(); await(releaseCommit); return true;
            })));
            await(deletedInsideTransaction);
            var booking = executor.submit(() -> attempt(() -> as(fixture.customer(), () -> tx(() -> {
                assertReadCommitted(); bookingStarted.countDown();
                return appointments.bookAppointment(booking(fixture, fixture.firstStaffId(), LocalTime.of(10, 0), null));
            }))));
            await(bookingStarted);
            try { assertThrows(TimeoutException.class, () -> booking.get(250, TimeUnit.MILLISECONDS)); }
            finally { releaseCommit.countDown(); }
            assertTrue(deletion.get(20, TimeUnit.SECONDS)); assertFalse(booking.get(20, TimeUnit.SECONDS).accepted());
        }
        assertTrue(shifts.findById(fixture.firstShiftId()).orElseThrow().getIsDeleted());
        assertEquals(0, (int) tx(() -> appointmentRows.findFutureAssignedItems(fixture.firstStaffId(), fixture.date()).size()));
    }

    @Test
    void shiftDeletionWaitingForBookingRollsBackWhenTheCommittedAppointmentNeedsThatShift() throws Exception {
        Fixture fixture = fixture(); var bookedInsideTransaction = new CountDownLatch(1); var releaseCommit = new CountDownLatch(1);
        var deletionStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var booking = executor.submit(() -> as(fixture.customer(), () -> tx(() -> {
                assertReadCommitted(); var response = appointments.bookAppointment(booking(fixture, fixture.firstStaffId(), LocalTime.of(10, 0), null));
                bookedInsideTransaction.countDown(); await(releaseCommit); return response;
            })));
            await(bookedInsideTransaction);
            var deletion = executor.submit(() -> attempt(() -> as(fixture.admin(), () -> tx(() -> {
                assertReadCommitted(); deletionStarted.countDown(); administration.deleteSchedule(fixture.firstStaffId(), fixture.firstShiftId()); return true;
            }))));
            await(deletionStarted);
            try { assertThrows(TimeoutException.class, () -> deletion.get(250, TimeUnit.MILLISECONDS)); }
            finally { releaseCommit.countDown(); }
            assertNotNull(booking.get(20, TimeUnit.SECONDS)); assertFalse(deletion.get(20, TimeUnit.SECONDS).accepted());
        }
        assertFalse(shifts.findById(fixture.firstShiftId()).orElseThrow().getIsDeleted());
        assertEquals(StaffScheduleStatus.SCHEDULED, shifts.findById(fixture.firstShiftId()).orElseThrow().getStatus());
        assertEquals(1, (int) tx(() -> appointmentRows.findFutureAssignedItems(fixture.firstStaffId(), fixture.date()).size()));
    }
}
