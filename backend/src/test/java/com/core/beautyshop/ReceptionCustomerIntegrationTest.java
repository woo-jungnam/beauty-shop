package com.core.beautyshop;

import com.core.beautyshop.modules.identity.domain.*;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reception_customer;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ReceptionCustomerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    String phone;
    @BeforeEach void setup() {
        phone = "09" + String.format("%08d", new Random().nextInt(100000000));
        for (String name : List.of("ROLE_CUSTOMER", "ROLE_STAFF")) {
            if (roles.findByName(name).isEmpty()) roles.saveAndFlush(Role.builder().name(name).build());
        }
    }
    String command(String name, String number) throws Exception { return json.writeValueAsString(Map.of("fullName", name, "phone", number)); }
    User existing(String roleName) {
        return users.saveAndFlush(User.builder().username(UUID.randomUUID().toString()).fullName("Tên gốc")
                .phone(phone).roles(new ArrayList<>(List.of(roles.findByName(roleName).orElseThrow()))).build());
    }
    @Test @WithMockUser(roles = "STAFF") void createsCustomerOnlyWithNormalizedPhoneAndNoFakeEmail() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/crm/customers").contentType("application/json")
                .content(command("  Khách tại quầy  ", "+84" + phone.substring(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.fullName").value("Khách tại quầy")).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("id").asLong();
        User user = users.findById(id).orElseThrow();
        assertNull(user.getEmail()); assertNotNull(user.getPasswordHash()); assertTrue(user.getPasswordHash().startsWith("$2"));
        assertEquals(List.of("ROLE_CUSTOMER"), user.getRoles().stream().map(Role::getName).toList());
        assertEquals(AccountStatus.ACTIVE, user.getStatus());
    }
    @Test @WithMockUser(roles = "STAFF") void retriesReuseCustomerWithoutOverwritingProfile() throws Exception {
        User user = existing("ROLE_CUSTOMER");
        mvc.perform(post("/api/v1/admin/crm/customers").contentType("application/json").content(command("Tên khác", phone)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.fullName").value("Tên gốc"));
        assertEquals(1, users.findByPhoneIn(List.of(phone)).size());
    }
    @Test @WithMockUser(roles = "STAFF") void operatorPhoneCannotBecomeCustomer() throws Exception {
        User operator = existing("ROLE_STAFF");
        mvc.perform(post("/api/v1/admin/crm/customers").contentType("application/json").content(command("Khách", phone)))
                .andExpect(status().isBadRequest());
        assertEquals("ROLE_STAFF", users.findById(operator.getId()).orElseThrow().getRoles().getFirst().getName());
    }
    @Test @WithMockUser(roles = "CUSTOMER") void customersCannotCreateReceptionProfiles() throws Exception {
        mvc.perform(post("/api/v1/admin/crm/customers").contentType("application/json").content(command("Khách", phone)))
                .andExpect(status().isForbidden());
        assertTrue(users.findByPhoneIn(List.of(phone)).isEmpty());
    }
    @Test @WithMockUser(roles = "STAFF") void invalidPhoneAndEmptyNameAreRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/crm/customers").contentType("application/json").content(command("Khách", "abc")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/crm/customers").contentType("application/json").content(command(" ", phone)))
                .andExpect(status().isBadRequest());
        assertTrue(users.findByPhoneIn(List.of(phone)).isEmpty());
    }
    @Test @WithMockUser(roles = "STAFF") void lockedProfilesAreNotReactivatedByReception() throws Exception {
        User user = existing("ROLE_CUSTOMER"); user.setStatus(AccountStatus.BLOCKED); users.saveAndFlush(user);
        mvc.perform(post("/api/v1/admin/crm/customers").contentType("application/json").content(command("Khách", phone)))
                .andExpect(status().isBadRequest());
        assertEquals(AccountStatus.BLOCKED, users.findById(user.getId()).orElseThrow().getStatus());
    }
}
