package com.core.beautyshop;

import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.modules.order.domain.enums.PaymentStatus;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order_reconciliation_test;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrderReconciliationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired OrderRepository orders;
    @Autowired PasswordEncoder encoder;

    private User createUser(String roleName, Set<String> permissions) {
        Role role = roles.findByName(roleName)
                .orElseGet(() -> roles.saveAndFlush(Role.builder().name(roleName).build()));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(User.builder()
                .username("rec_" + suffix)
                .email("rec_" + suffix + "@example.test")
                .fullName("Nhân sự Test")
                .passwordHash(encoder.encode("Pass1234!"))
                .roles(new ArrayList<>(List.of(role)))
                .staffPermissions(new HashSet<>(permissions))
                .build());
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        UserDetailsImpl principal = UserDetailsImpl.build(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void reconciliationFilterSummaryAndAdminBatchCodSettlementWorkEndToEnd() throws Exception {
        User admin = createUser("ROLE_ADMIN", Set.of());
        User staffWithoutOrders = createUser("ROLE_STAFF", Set.of());
        User staff = createUser("ROLE_STAFF", Set.of("ORDERS"));

        Order shipperOrder = orders.saveAndFlush(Order.builder()
                .orderNumber("ORD-REC-1")
                .customerName("Khách Shipper")
                .customerPhone("0901111111")
                .shippingAddress("12 Lê Lợi")
                .subTotal(new BigDecimal("350000"))
                .totalAmount(new BigDecimal("350000"))
                .paidAmount(new BigDecimal("350000"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.DELIVERED)
                .deliveredAt(Instant.now())
                .shipperId(77L)
                .shipperName("Nguyễn Văn Tài")
                .shipperPhone("0909888777")
                .shipperCodCollected(new BigDecimal("350000"))
                .shipperCodRemitted(BigDecimal.ZERO)
                .build());

        Order carrierOrder = orders.saveAndFlush(Order.builder()
                .orderNumber("ORD-REC-2")
                .customerName("Khách Vận Chuyển")
                .customerPhone("0902222222")
                .shippingAddress("45 Nguyễn Huệ")
                .subTotal(new BigDecimal("220000"))
                .totalAmount(new BigDecimal("220000"))
                .paidAmount(BigDecimal.ZERO)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.DELIVERED)
                .deliveredAt(Instant.now())
                .carrierName("GHN")
                .trackingCode("GHN-999")
                .build());

        orders.saveAndFlush(Order.builder()
                .orderNumber("ORD-REC-3")
                .customerName("Khách Đang Giao")
                .customerPhone("0903333333")
                .shippingAddress("78 Hai Bà Trưng")
                .subTotal(new BigDecimal("180000"))
                .totalAmount(new BigDecimal("180000"))
                .paidAmount(BigDecimal.ZERO)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.SHIPPED)
                .shipperId(77L)
                .shipperName("Nguyễn Văn Tài")
                .shipperPhone("0909888777")
                .build());

        // Staff without ORDERS permission cannot view reconciliation summary
        mvc.perform(get("/api/v1/admin/orders/reconciliation/summary").with(authentication(authOf(staffWithoutOrders))))
                .andExpect(status().isForbidden());

        // Staff with ORDERS permission can view reconciliation summary & filtered lists
        mvc.perform(get("/api/v1/admin/orders/reconciliation/summary").with(authentication(authOf(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCodOrders").value(2))
                .andExpect(jsonPath("$.data.pendingCodAmount").value(570000))
                .andExpect(jsonPath("$.data.inTransitCodOrders").value(1))
                .andExpect(jsonPath("$.data.shipperPendingCodAmount").value(350000))
                .andExpect(jsonPath("$.data.carrierPendingCodAmount").value(220000))
                .andExpect(jsonPath("$.data.byShipper.length()").value(2));

        mvc.perform(get("/api/v1/admin/orders?codReconciliation=PENDING&shipperId=77").with(authentication(authOf(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value("ORD-REC-1"))
                .andExpect(jsonPath("$.data.content[0].unsettledCod").value(350000));

        mvc.perform(get("/api/v1/admin/orders?codReconciliation=PENDING&carrierOnly=true").with(authentication(authOf(staff))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value("ORD-REC-2"));

        // Staff is blocked from financial batch COD settlement
        String batchPayload = """
                {
                  "orderIds": [%d, %d],
                  "reference": "BATCH-TEST-01",
                  "reason": "Đối soát cuối ngày"
                }
                """.formatted(shipperOrder.getId(), carrierOrder.getId());

        mvc.perform(post("/api/v1/admin/commerce/orders/cod-settlement/batch")
                        .with(authentication(authOf(staff)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchPayload))
                .andExpect(status().isForbidden());

        // Admin executes batch COD settlement atomically
        mvc.perform(post("/api/v1/admin/commerce/orders/cod-settlement/batch")
                        .with(authentication(authOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settledCount").value(2))
                .andExpect(jsonPath("$.data.totalSettledAmount").value(570000))
                .andExpect(jsonPath("$.data.batchReference").value("BATCH-TEST-01"));

        // Verify summary updates cleanly & vouchers appear in settlement history
        mvc.perform(get("/api/v1/admin/orders/reconciliation/summary").with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCodOrders").value(0))
                .andExpect(jsonPath("$.data.pendingCodAmount").value(0))
                .andExpect(jsonPath("$.data.settledCodOrders").value(2))
                .andExpect(jsonPath("$.data.settledCodAmount").value(570000));

        mvc.perform(get("/api/v1/admin/orders?codReconciliation=SETTLED").with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mvc.perform(get("/api/v1/admin/orders/reconciliation/settlements?keyword=BATCH-TEST-01")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }
}

