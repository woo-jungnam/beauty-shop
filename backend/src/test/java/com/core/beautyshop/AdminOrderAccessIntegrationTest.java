package com.core.beautyshop;

import com.core.beautyshop.modules.order.domain.*;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminOrderAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;

    Order fixture() {
        return orders.save(Order.builder().orderNumber("ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .guestSessionId(UUID.randomUUID().toString()).customerName("Guest").customerPhone("0987654321")
                .shippingAddress("Test address").paymentMethod(PaymentMethod.BANK).subTotal(BigDecimal.TEN)
                .totalAmount(BigDecimal.TEN).notes("Leave at reception").items(new ArrayList<>()).statusHistories(new ArrayList<>()).build());
    }

    @Test
    @WithMockUser(roles = "ORDER_STAFF")
    void staffReadsAdminDetailButCannotUseCustomerRouteToReadAnyGuestOrder() throws Exception {
        Order order = fixture();
        mvc.perform(get("/api/v1/admin/orders/" + order.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.notes").value("Leave at reception"));
        mvc.perform(get("/api/v1/orders/" + order.getId())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotOpenAdminOrderDetail() throws Exception {
        mvc.perform(get("/api/v1/admin/orders/" + fixture().getId())).andExpect(status().isForbidden());
    }
}
