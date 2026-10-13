package com.core.beautyshop.modules.identity.api;

import com.core.beautyshop.modules.identity.application.dto.request.*;
import com.core.beautyshop.modules.identity.application.dto.response.AuthResponse;
import com.core.beautyshop.modules.identity.application.service.*;
import com.core.beautyshop.modules.identity.domain.*;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.jwt.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:identity_admin_fix;DB_CLOSE_DELAY=-1;MODE=MySQL")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IdentityAdministrationIntegrationTest {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RefreshTokenSessionRepository sessions;
    @Autowired UserStatusHistoryRepository history;
    @Autowired UserService userService;
    @Autowired RoleService roleService;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired CacheManager cache;
    @Autowired JwtUtils jwt;
    @Autowired MockMvc mvc;
    @Autowired com.core.beautyshop.modules.spa.domain.AppointmentRepository appointments;
    @Autowired com.core.beautyshop.shared.audit.domain.AuditLogRepository auditLogs;
    Role adminRole;
    Role customerRole;
    User admin;

    @AfterEach
    void clearAuthentication() { org.springframework.security.test.context.TestSecurityContextHolder.clearContext(); }

    @BeforeEach
    void setup() {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        appointments.deleteAll(); auditLogs.deleteAll(); sessions.deleteAll(); history.deleteAll(); users.deleteAll(); roles.deleteAll();
        cache.getCache("token_versions").clear();
        adminRole = roles.save(Role.builder().name("ROLE_ADMIN").build());
        customerRole = roles.save(Role.builder().name("ROLE_CUSTOMER").build());
        admin = create("administrator", "administrator@example.com", adminRole);
    }

    @Test
    void protectsSystemRoleCodesAndTheLastActiveAdministrator() {
        UpdateRoleRequest rename = new UpdateRoleRequest(); rename.setRoleName("ROLE_RENAMED_ADMIN");
        assertThrows(BusinessException.class, () -> roleService.updateRole(adminRole.getId(), rename));
        assertThrows(BusinessException.class, () -> roleService.deleteRole(adminRole.getId()));
        assertThrows(BusinessException.class, () -> userService.updateStatus(admin.getId(), AccountStatus.BLOCKED, "test"));
        assertThrows(BusinessException.class, () -> userService.updateRoles(admin.getId(), List.of(customerRole.getId())));
        assertEquals(AccountStatus.ACTIVE, users.findById(admin.getId()).orElseThrow().getStatus());
        assertEquals("ROLE_ADMIN", roles.findById(adminRole.getId()).orElseThrow().getName());
    }

    @Test
    void concurrentDemotionsLeaveOneActiveAdministrator() throws Exception {
        User second = create("second_admin", "second@example.com", adminRole);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Long id : List.of(admin.getId(), second.getId())) results.add(executor.submit(() -> {
                start.await();
                try { userService.updateRoles(id, List.of(customerRole.getId())); return true; }
                catch (BusinessException expected) { return false; }
            }));
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) if (result.get(15, TimeUnit.SECONDS)) succeeded++;
            assertEquals(1, succeeded);
            assertEquals(1, users.countActiveAdminsExcluding(-1L));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void renamingAndDeletingCustomRolesRevokeIssuedTokens() throws Exception {
        Role custom = roles.save(Role.builder().name("ROLE_TEST_CUSTOM").build());
        User customer = create("customer", "customer@example.com", customerRole, custom);
        AuthResponse first = login(customer);
        UpdateRoleRequest rename = new UpdateRoleRequest(); rename.setRoleName("ROLE_TEST_RENAMED");
        roleService.updateRole(custom.getId(), rename);
        assertProtected(first.getAccessToken(), 401);
        assertTrue(sessions.findByUserIdOrderByCreatedAtDesc(customer.getId()).stream().allMatch(session -> session.getRevokedAt() != null));
        AuthResponse second = login(customer);
        roleService.deleteRole(custom.getId());
        assertProtected(second.getAccessToken(), 401);
        assertEquals(List.of("ROLE_CUSTOMER"), userService.getUserById(customer.getId()).getRoles());
    }

    @Test
    void repeatedLogoutCannotRevokeNewLoginOrAnotherDevice() throws Exception {
        User customer = create("customer", "customer@example.com", customerRole);
        AuthResponse first = login(customer);
        AuthResponse otherDevice = login(customer);
        auth.logout(new RefreshTokenRequest(first.getRefreshToken()));
        assertProtected(first.getAccessToken(), 401);
        assertProtected(otherDevice.getAccessToken(), 200);
        AuthResponse fresh = login(customer);
        auth.logout(new RefreshTokenRequest(first.getRefreshToken()));
        assertProtected(fresh.getAccessToken(), 200);
    }

    @Test
    void individualSessionRevocationChecksOwnerAndKeepsOtherDevice() throws Exception {
        User customer = create("customer", "customer@example.com", customerRole);
        User other = create("other_customer", "other@example.com", customerRole);
        AuthResponse first = login(customer);
        AuthResponse second = login(customer);
        String family = jwt.parseAccessToken(first.getAccessToken()).orElseThrow().getSessionFamilyId();
        Long sessionId = sessions.findByUserIdOrderByCreatedAtDesc(customer.getId()).stream()
                .filter(session -> family.equals(session.getFamilyId())).findFirst().orElseThrow().getId();
        assertThrows(ResourceNotFoundException.class, () -> auth.revokeSession(other.getId(), sessionId));
        assertProtected(first.getAccessToken(), 200);
        auth.revokeSession(customer.getId(), sessionId);
        assertProtected(first.getAccessToken(), 401);
        assertProtected(second.getAccessToken(), 200);
    }

    @Test
    void reusedRefreshTokenRevokesRotatedAccessToken() throws Exception {
        User customer = create("customer", "customer@example.com", customerRole);
        AuthResponse first = login(customer);
        AuthResponse rotated = auth.refreshToken(new RefreshTokenRequest(first.getRefreshToken()));
        assertThrows(BusinessException.class, () -> auth.refreshToken(new RefreshTokenRequest(first.getRefreshToken())));
        assertProtected(rotated.getAccessToken(), 401);
    }

    @Test
    void namespaceIsUnambiguousAndAdminFiltersAndHistoryWork() {
        User customer = create("customer", "customer@example.com", customerRole);
        create("customer@example.com", "legacy@example.com", customerRole);
        assertEquals(customer.getId(), auth.login(new LoginRequest("customer@example.com", "Password123!")).getId());
        RegisterRequest invalid = new RegisterRequest(); invalid.setUsername("administrator@example.com");
        invalid.setEmail("new@example.com"); invalid.setPassword("Password123!"); invalid.setFullName("New user");
        assertThrows(BusinessException.class, () -> auth.register(invalid));
        userService.updateStatus(customer.getId(), AccountStatus.BLOCKED, "Customer requested lock");
        var filtered = userService.getAllUsers("customer", AccountStatus.BLOCKED, "CUSTOMER", PageRequest.of(0, 10));
        assertEquals(1, filtered.getTotalElements());
        assertEquals(customer.getId(), filtered.getContent().getFirst().getId());
        assertEquals("Customer requested lock", userService.getStatusHistory(customer.getId(), PageRequest.of(0, 10)).getContent().getFirst().getReason());
    }

    @Test
    void spaAdminStatusMutationPersistsActorResourceAndActionAudit() throws Exception {
        User customer = create("spa_customer", "spa@example.com", customerRole);
        var appointment = appointments.save(com.core.beautyshop.modules.spa.domain.Appointment.builder()
                .userId(customer.getId()).appointmentDate(java.time.LocalDate.of(2026, 10, 3))
                .startTime(java.time.LocalTime.of(10, 0)).endTime(java.time.LocalTime.of(11, 0))
                .status(com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.PENDING).items(new ArrayList<>()).build());
        String accessToken = login(admin).getAccessToken();
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(put("/api/v1/appointments/admin/{id}/status", appointment.getId())
                .header("Authorization", "Bearer " + accessToken).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\",\"notes\":\"Customer requested cancellation\"}"))
                .andExpect(status().isOk());
        assertEquals(com.core.beautyshop.modules.spa.domain.enums.AppointmentStatus.CANCELLED, appointments.findById(appointment.getId()).orElseThrow().getStatus());
        var logs = auditLogs.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("APPOINTMENT", appointment.getId().toString(), PageRequest.of(0, 10)).getContent();
        assertEquals(1, logs.size());
        var log = logs.getFirst();
        assertEquals(admin.getId(), log.getUserId());
        assertEquals(admin.getUsername(), log.getUsername());
        assertEquals("UPDATE_APPOINTMENT_STATUS", log.getAction());
        assertEquals("SUCCESS", log.getStatus());
    }

    private User create(String username, String email, Role... assigned) {
        return users.save(User.builder().username(username).email(email).fullName(username)
                .passwordHash(encoder.encode("Password123!")).roles(new ArrayList<>(List.of(assigned))).build());
    }
    private AuthResponse login(User user) { return auth.login(new LoginRequest(user.getUsername().contains("@") ? user.getEmail() : user.getUsername(), "Password123!")); }
    private void assertProtected(String token, int expected) throws Exception {
        // Direct service login sets the caller's context; HTTP assertions must start as a fresh request.
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().is(expected));
    }
}
