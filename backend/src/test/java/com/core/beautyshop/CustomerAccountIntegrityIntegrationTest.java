package com.core.beautyshop;

import com.core.beautyshop.modules.catalog.api.CustomerWishlistController;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.modules.identity.api.StaffPermissionService;
import com.core.beautyshop.modules.identity.application.dto.request.LoginRequest;
import com.core.beautyshop.modules.identity.application.dto.request.UpdateProfileRequest;
import com.core.beautyshop.modules.identity.application.service.*;
import com.core.beautyshop.modules.identity.domain.*;
import com.core.beautyshop.modules.identity.domain.CustomerAccountData.*;
import com.core.beautyshop.modules.notification.application.service.CustomerNotificationService;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.spa.application.service.SpaAccessService;
import com.core.beautyshop.modules.spa.domain.Appointment;
import com.core.beautyshop.modules.spa.domain.AppointmentItem;
import com.core.beautyshop.modules.spa.domain.Staff;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.jwt.JwtUtils;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.core.beautyshop.modules.identity.application.service.CustomerAccountService.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real transactions and principal IDs: no shared fixture truncation or external services. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:customer_account_integrity;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "app.mail.enabled=false"
})
@ActiveProfiles("test")
class CustomerAccountIntegrityIntegrationTest {
    @Autowired CustomerAccountService accounts;
    @Autowired UserService profiles;
    @Autowired CustomerNotificationService notifications;
    @Autowired CustomerWishlistController wishlist;
    @Autowired StaffPermissionService permissions;
    @Autowired SpaAccessService spaAccess;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired RefreshTokenSessionRepository sessions;
    @Autowired AuthService auth;
    @Autowired AccessTokenRevocationService revocation;
    @Autowired JwtUtils jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clearPrincipal() { SecurityContextHolder.clearContext(); }
    <T> T tx(Supplier<T> work) { return new TransactionTemplate(transactions).execute(status -> work.get()); }
    <T> T as(User owner, Supplier<T> work) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var principal = UserDetailsImpl.build(owner);
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try { return work.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    User user(String roleName) {
        return tx(() -> {
            var role = roles.findByName(roleName).orElseGet(() -> roles.saveAndFlush(Role.builder().name(roleName).build()));
            var suffix = UUID.randomUUID().toString();
            return users.saveAndFlush(User.builder().username(suffix).email(suffix + "@example.test").fullName("Account fixture")
                .passwordHash(encoder.encode("InitialPass123!")).roles(new ArrayList<>(List.of(role))).build());
        });
    }
    AddressCommand address(String label, boolean isDefault) {
        return new AddressCommand(label, "0912345678", "12 Test street", "Test ward", null, "Dong Nai", isDefault);
    }
    void recovery(User owner, String token, Instant expires) {
        tx(() -> {
            var record = new PasswordRecovery(); record.setTokenHash(hash(token)); record.setUserId(owner.getId());
            record.setCreatedAt(Instant.now().minusSeconds(5)); record.setExpiresAt(expires); em.persist(record); return null;
        });
    }

    @Test void customerDeclaredSkinAndProfileEditsNeverTouchAnotherAccountOrLoginIdentity() {
        var first = user("ROLE_CUSTOMER"); var other = user("ROLE_CUSTOMER");
        assertNull(as(first, accounts::skin).skinType());
        var own = as(first, () -> accounts.updateSkin(new SkinCommand(" OILY ", "Sensitive", "Fragrance", null, "B5", "Customer declaration")));
        assertEquals("OILY", own.skinType()); assertTrue(own.declaredByCustomer()); assertNotNull(own.updatedAt());
        assertNull(as(other, accounts::skin).allergies());
        var edit = new UpdateProfileRequest(); edit.setFullName("Updated name"); edit.setBio("New biography");
        as(first, () -> profiles.updateProfile(edit));
        var updated = users.findById(first.getId()).orElseThrow();
        assertEquals("Updated name", updated.getFullName()); assertEquals(first.getEmail(), updated.getEmail());
        assertEquals(first.getUsername(), updated.getUsername()); assertTrue(encoder.matches("InitialPass123!", updated.getPasswordHash()));
        assertEquals("Account fixture", users.findById(other.getId()).orElseThrow().getFullName());
    }

    @Test void addressesHaveOneDefaultAndOwnershipIsEnforcedForUpdateAndDelete() {
        var first = user("ROLE_CUSTOMER"); var other = user("ROLE_CUSTOMER");
        var one = as(first, () -> accounts.saveAddress(null, address("First", false)));
        var two = as(first, () -> accounts.saveAddress(null, address("Second", true)));
        assertTrue(one.isDefault()); assertTrue(two.isDefault());
        var current = as(first, accounts::addresses);
        assertEquals(1, current.stream().filter(AddressView::isDefault).count());
        assertEquals(two.id(), current.stream().filter(AddressView::isDefault).findFirst().orElseThrow().id());
        assertThrows(ResourceNotFoundException.class, () -> as(other, () -> accounts.saveAddress(one.id(), address("Intrusion", true))));
        assertThrows(ResourceNotFoundException.class, () -> as(other, () -> { accounts.deleteAddress(two.id()); return null; }));
        assertTrue(as(other, accounts::addresses).isEmpty());
        as(first, () -> { accounts.deleteAddress(two.id()); return null; });
        assertEquals(one.id(), as(first, accounts::addresses).stream().filter(AddressView::isDefault).findFirst().orElseThrow().id());
    }

    @Test void passwordChangeChecksCurrentPasswordAndRevokesEveryIssuedSession() {
        var customer = user("ROLE_CUSTOMER");
        var first = auth.login(new LoginRequest(customer.getUsername(), "InitialPass123!"));
        var second = auth.login(new LoginRequest(customer.getUsername(), "InitialPass123!"));
        var token = jwt.parseAccessToken(first.getAccessToken()).orElseThrow();
        assertTrue(revocation.isCurrent(customer.getId(), token.getTokenVersion()));
        assertThrows(BusinessException.class, () -> as(customer, () -> { accounts.changePassword(new PasswordChange("Incorrect", "Replacement123!")); return null; }));
        assertTrue(revocation.isCurrent(customer.getId(), token.getTokenVersion()));
        as(customer, () -> { accounts.changePassword(new PasswordChange("InitialPass123!", "Replacement123!")); return null; });
        assertFalse(revocation.isCurrent(customer.getId(), token.getTokenVersion()));
        assertFalse(revocation.isSessionCurrent(customer.getId(), jwt.parseAccessToken(second.getAccessToken()).orElseThrow().getSessionFamilyId()));
        assertTrue(sessions.findByUserIdOrderByCreatedAtDesc(customer.getId()).stream().allMatch(row -> row.getRevokedAt() != null));
        assertTrue(encoder.matches("Replacement123!", users.findById(customer.getId()).orElseThrow().getPasswordHash()));
        assertEquals(customer.getId(), auth.login(new LoginRequest(customer.getUsername(), "Replacement123!")).getId());
    }

    @Test void recoveryIsOneUseExpiresAndInvalidatesSiblingLinksAndSessions() {
        var customer = user("ROLE_CUSTOMER");
        var issued = auth.login(new LoginRequest(customer.getUsername(), "InitialPass123!"));
        recovery(customer, "valid-first-token", Instant.now().plusSeconds(600));
        recovery(customer, "valid-sibling-token", Instant.now().plusSeconds(600));
        accounts.resetPassword(new PasswordReset("valid-first-token", "RecoveredPass123!"));
        assertThrows(BusinessException.class, () -> accounts.resetPassword(new PasswordReset("valid-first-token", "AnotherPass123!")));
        assertThrows(BusinessException.class, () -> accounts.resetPassword(new PasswordReset("valid-sibling-token", "AnotherPass123!")));
        assertTrue(encoder.matches("RecoveredPass123!", users.findById(customer.getId()).orElseThrow().getPasswordHash()));
        assertFalse(revocation.isCurrent(customer.getId(), jwt.parseAccessToken(issued.getAccessToken()).orElseThrow().getTokenVersion()));
        recovery(customer, "expired-token", Instant.now().minusSeconds(60));
        assertThrows(BusinessException.class, () -> accounts.resetPassword(new PasswordReset("expired-token", "AnotherPass123!")));
        assertThrows(BusinessException.class, () -> accounts.resetPassword(new PasswordReset("unknown-token", "AnotherPass123!")));
        assertTrue(encoder.matches("RecoveredPass123!", users.findById(customer.getId()).orElseThrow().getPasswordHash()));
    }

    @Test void recoveryWithoutConfiguredMailFailsHonestlyAndStoresNoUsableToken() {
        var customer = user("ROLE_CUSTOMER");
        var failure = assertThrows(BusinessException.class, () -> accounts.forgotPassword(customer.getEmail()));
        assertTrue(failure.getMessage().contains("chưa được cấu hình"));
        assertEquals(0L, tx(() -> em.createQuery("select count(r) from PasswordRecovery r where r.userId=:id", Long.class).setParameter("id", customer.getId()).getSingleResult()));
    }

    @Test void wishlistConcurrentAddsAreIdempotentAndCannotRemoveAnotherOwnersSelection() throws Exception {
        var customer = user("ROLE_CUSTOMER"); var other = user("ROLE_CUSTOMER");
        var product = products.saveAndFlush(Product.builder().name("Wishlist fixture").slug(UUID.randomUUID().toString()).build());
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return as(customer, () -> wishlist.add(product.getId())); });
            var b = pool.submit(() -> { start.await(); return as(customer, () -> wishlist.add(product.getId())); });
            start.countDown(); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1, as(customer, () -> wishlist.list().getData()).size());
        assertTrue(as(other, () -> wishlist.list().getData()).isEmpty());
        as(other, () -> wishlist.remove(product.getId()));
        assertEquals(1, as(customer, () -> wishlist.list().getData()).size());
        as(customer, () -> wishlist.remove(product.getId()));
        assertTrue(as(customer, () -> wishlist.list().getData()).isEmpty());
    }

    @Test void newsletterRequiresConsentAndConcurrentRepeatedSubscriptionCreatesOneNormalizedRecord() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        assertThrows(BusinessException.class, () -> accounts.subscribe(email, false));
        assertEquals(0, jdbc.queryForObject("select count(*) from newsletter_subscriptions where email=?", Integer.class, email));
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); accounts.subscribe(" " + email.toUpperCase(Locale.ROOT) + " ", true); return true; });
            var b = pool.submit(() -> { start.await(); accounts.subscribe(email, true); return true; });
            start.countDown(); assertTrue(a.get(20, TimeUnit.SECONDS)); assertTrue(b.get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("select count(*) from newsletter_subscriptions where email=? and active=true", Integer.class, email));
    }

    @Test void notificationDeliveryIsIdempotentAndReadAndPagingNeverCrossAccounts() {
        var customer = user("ROLE_CUSTOMER"); var other = user("ROLE_CUSTOMER");
        String event = UUID.randomUUID().toString();
        notifications.publish(customer.getId(), event, "ORDER", "Order changed", "Public status only", "profile?tab=orders&order=20");
        notifications.publish(customer.getId(), event, "ORDER", "Repeated", "Should not duplicate", null);
        for (int i = 0; i < 21; i++) notifications.publish(other.getId(), event + ":" + i, "ORDER", "Another account", "Private to owner", null);
        var rows = as(customer, () -> notifications.mine(0, 20));
        assertEquals(1, rows.getTotalElements()); assertEquals("Order changed", rows.getContent().getFirst().title());
        Long id = rows.getContent().getFirst().id();
        assertThrows(ResourceNotFoundException.class, () -> as(other, () -> notifications.read(id)));
        assertNull(as(customer, () -> notifications.mine(0, 20)).getContent().getFirst().readAt());
        var read = as(customer, () -> notifications.read(id)); assertNotNull(read.readAt());
        assertEquals(read.readAt(), as(customer, () -> notifications.read(id)).readAt());
        assertEquals(21, as(other, () -> notifications.mine(0, 20)).getTotalElements());
        assertEquals(1, as(other, () -> notifications.mine(1, 20)).getContent().size());
        assertThrows(BusinessException.class, () -> as(customer, () -> notifications.mine(-1, 20)));
        assertThrows(BusinessException.class, () -> as(customer, () -> notifications.mine(0, 101)));
    }

    @Test void staffGrantsAreAdminControlledRevokeSessionsAndLimitPrivateCareToAuthorizedActors() {
        var admin = user("ROLE_ADMIN"); var catalog = user("ROLE_STAFF"); var customer = user("ROLE_CUSTOMER");
        var issued = auth.login(new LoginRequest(catalog.getUsername(), "InitialPass123!"));
        as(admin, () -> permissions.replace(catalog.getId(), Set.of("CATALOG")));
        assertTrue(as(catalog, () -> permissions.has("CATALOG"))); assertFalse(as(catalog, () -> permissions.has("CRM")));
        assertFalse(revocation.isCurrent(catalog.getId(), jwt.parseAccessToken(issued.getAccessToken()).orElseThrow().getTokenVersion()));
        assertThrows(AccessDeniedException.class, () -> as(catalog, () -> permissions.replace(catalog.getId(), Set.of("CRM"))));
        assertThrows(BusinessException.class, () -> as(admin, () -> permissions.replace(catalog.getId(), Set.of("UNKNOWN"))));
        assertThrows(BusinessException.class, () -> as(admin, () -> permissions.replace(customer.getId(), Set.of("CRM"))));
        var appointment = Appointment.builder().userId(customer.getId()).build();
        var item = AppointmentItem.builder().appointment(appointment).staff(Staff.builder().userId(catalog.getId()).build()).build();
        appointment.setItems(List.of(item));
        assertThrows(AccessDeniedException.class, () -> as(catalog, () -> { spaAccess.requireCanViewCareSummary(appointment); return null; }));
        as(admin, () -> permissions.replace(catalog.getId(), Set.of("CRM")));
        as(catalog, () -> { spaAccess.requireCanViewCareSummary(appointment); return null; });
        as(admin, () -> permissions.replace(catalog.getId(), Set.of("SPA_EXECUTION")));
        as(catalog, () -> { spaAccess.requireCanViewCareSummary(appointment); spaAccess.requireCanPerformItem(item); return null; });
        item.setStaff(Staff.builder().userId(customer.getId()).build());
        assertThrows(AccessDeniedException.class, () -> as(catalog, () -> { spaAccess.requireCanViewCareSummary(appointment); return null; }));
        as(admin, () -> { spaAccess.requireCanViewCareSummary(appointment); return null; });
    }

    @Test void orderStatisticsUseAllPaidAmountsMinusRefundsAndExcludeAnotherAccountAndDeletedRows() {
        var customer = user("ROLE_CUSTOMER"); var other = user("ROLE_CUSTOMER");
        for (int i = 0; i < 25; i++) order(customer, new BigDecimal("100000"), new BigDecimal("10000"), false);
        order(other, new BigDecimal("999999"), BigDecimal.ZERO, false);
        order(customer, new BigDecimal("777777"), BigDecimal.ZERO, true);
        var stats = as(customer, accounts::statistics);
        assertEquals(25, stats.totalOrders()); assertEquals(0, new BigDecimal("2500000").compareTo(stats.totalPaid()));
        assertEquals(0, new BigDecimal("250000").compareTo(stats.totalRefunded()));
        assertEquals(0, new BigDecimal("2250000").compareTo(stats.netSpent()));
    }
    void order(User owner, BigDecimal paid, BigDecimal refunded, boolean deleted) {
        var row = Order.builder().orderNumber("ACC-" + UUID.randomUUID()).userId(owner.getId()).customerName("Fixture")
            .customerPhone("0912345678").shippingAddress("Fixture address").paymentMethod(PaymentMethod.BANK)
            .subTotal(paid).totalAmount(paid).paidAmount(paid).refundedAmount(refunded).build();
        row.setIsDeleted(deleted); orders.saveAndFlush(row);
    }
}
