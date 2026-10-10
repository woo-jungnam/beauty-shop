package com.core.beautyshop;

import com.core.beautyshop.modules.identity.application.dto.request.LoginRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RefreshTokenRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RegisterRequest;
import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.identity.domain.enums.Gender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
public class ComprehensiveSwaggerAndBearerApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${sepay.webhook.api-key:your-sepay-api-key-here}")
    private String sePayWebhookApiKey;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    public void setup() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ROLE_ADMIN").description("Admin").build()));
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseGet(() ->
                roleRepository.save(Role.builder().name("ROLE_CUSTOMER").description("Customer").build()));

        if (!userRepository.existsByUsername("swagger_admin")) {
            userRepository.save(User.builder()
                    .username("swagger_admin")
                    .email("namnt4560@gmail.com")
                    .fullName("Swagger Admin")
                    .passwordHash(passwordEncoder.encode("Admin@123"))
                    .roles(List.of(adminRole))
                    .build());
        }

        if (!userRepository.existsByUsername("swagger_customer")) {
            userRepository.save(User.builder()
                    .username("swagger_customer")
                    .email("swagger_customer@beautyshop.com")
                    .fullName("Swagger Customer")
                    .passwordHash(passwordEncoder.encode("Customer@123"))
                    .roles(List.of(customerRole))
                    .build());
        }

        adminToken = loginAndGetToken("swagger_admin", "Admin@123");
        customerToken = loginAndGetToken("swagger_customer", "Customer@123");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest(username, password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.get("data").get("accessToken").asText();
    }

    @Test
    @DisplayName("Swagger OpenAPI JSON Spec (/v3/api-docs) khả dụng và chứa cấu hình bearerAuth")
    public void testSwaggerOpenApiDocs_WithBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Beauty Shop Modular Monolith API Specification"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths").isMap())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/checkout']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/spa/tickets/purchase']").exists());
    }

    @Test
    @DisplayName("Swagger UI HTML trang tài liệu mở công khai không yêu cầu xác thực")
    public void testSwaggerUiAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("Mọi nhóm hiển thị trong Swagger UI đều xuất được OpenAPI")
    public void testAllSwaggerGroups() throws Exception {
        JsonNode config = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(13, config.path("urls").size());
        for (JsonNode group : config.path("urls")) {
            mockMvc.perform(get(group.path("url").asText()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths").isMap());
        }
    }

    @Test
    @DisplayName("Truy cập endpoint yêu cầu xác thực nhưng KHÔNG truyền Bearer Token -> trả về 401")
    public void testProtectedEndpoints_WithoutBearerToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/orders/my-orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/appointments/my-appointments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/spa/tickets/my-tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Truy cập endpoint với Bearer Token sai/không hợp lệ -> trả về 401")
    public void testProtectedEndpoints_WithInvalidBearerToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/profile")
                        .header("Authorization", "Bearer this.is.an.invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Khách hàng (ROLE_CUSTOMER) truy cập API dành cho Quản trị viên (ADMIN) -> bị chặn 403 Forbidden")
    public void testCustomerToken_AccessingAdminEndpoints_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin/all")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/admin/warehouses")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/appointments/admin/all")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Quản trị viên (ROLE_ADMIN) truy cập API Quản trị với Bearer Token -> 200 OK")
    public void testAdminToken_AccessingAdminEndpoints_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/admin/warehouses")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/appointments/admin/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Quy trình Đăng ký -> Đăng nhập -> Hồ sơ -> Cấp mới Token (Silent Refresh)")
    public void testFullAuthLifecycle() throws Exception {
        String testUser = "lifecycle_user_" + System.currentTimeMillis();
        String testEmail = testUser + "@beautyshop.com";

        RegisterRequest reg = new RegisterRequest();
        reg.setUsername(testUser);
        reg.setEmail(testEmail);
        reg.setPassword("Pass@12345");
        reg.setFullName("Lifecycle Tester");
        reg.setGender(Gender.FEMALE);

        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn();

        JsonNode regNode = objectMapper.readTree(regRes.getResponse().getContentAsString()).get("data");
        String refreshToken = regNode.get("refreshToken").asText();
        String accessToken = regNode.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + regNode.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(testUser))
                .andExpect(jsonPath("$.data.email").value(testEmail));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(accessToken))))
                .andExpect(status().isBadRequest());

        RefreshTokenRequest refreshReq = new RefreshTokenRequest(refreshToken);
        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String newAccessToken = objectMapper.readTree(refreshRes.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(testUser));
    }

    @Test
    @DisplayName("Các API công khai của Catalog, Cart, Spa Services có thể gọi không cần Bearer")
    public void testPublicCatalogAndCartApis() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/attributes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/spa/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/cart").param("sessionId", "guest-session-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        mockMvc.perform(get("/api/v1/test/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("SePay Webhook yêu cầu xác thực API key (qua header Authorization / ApiKey)")
    public void testSePayWebhookApiKeySecurity() throws Exception {
        String payload = """
                {
                    "id": 99999,
                    "gateway": "MBBank",
                    "transactionDate": "2026-09-18 15:00:00",
                    "accountNumber": "123456789",
                    "subAccount": null,
                    "transferType": "in",
                    "transferAmount": 500000,
                    "accumulated": 500000,
                    "code": null,
                    "content": "ORD-TESTPAY1",
                    "referenceCode": "SEPAY_REF_TEST_SEC"
                }
                """;

        mockMvc.perform(post("/api/v1/payment/sepay-webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/payment/sepay-webhook")
                        .header("Authorization", "Bearer invalid-sepay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/payment/sepay-webhook")
                        .header("Authorization", "Bearer " + sePayWebhookApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}
