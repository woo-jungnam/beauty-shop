package com.core.beautyshop;

import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.domain.ProductVariant;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.inventory.domain.Warehouse;
import com.core.beautyshop.modules.inventory.domain.WarehouseRepository;
import com.core.beautyshop.modules.procurement.application.ProcurementReceiptService;
import com.core.beautyshop.modules.procurement.application.ProcurementService;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:procurement_history_test;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProcurementHistoryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired WarehouseRepository warehouses;
    @Autowired ProcurementService procurementService;
    @Autowired ProcurementReceiptService receiptService;

    private User createUser(String roleName, Set<String> permissions) {
        Role role = roles.findByName(roleName)
                .orElseGet(() -> roles.saveAndFlush(Role.builder().name(roleName).build()));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(User.builder()
                .username("proc_" + suffix)
                .email("proc_" + suffix + "@example.test")
                .fullName("Kho Test")
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
    void procurementHistoryEndpointsFilterOrdersMovementsAndSummaryCleanly() throws Exception {
        User staffWithoutPerm = createUser("ROLE_STAFF", Set.of());
        User staffWithPerm = createUser("ROLE_STAFF", Set.of("PROCUREMENT"));

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = products.saveAndFlush(Product.builder()
                .name("Sữa rửa mặt dịu nhẹ")
                .slug("srm-" + suffix)
                .build());
        ProductVariant variant = variants.saveAndFlush(ProductVariant.builder()
                .product(product)
                .sku("SRM-HIST-" + suffix)
                .variantName("150ml")
                .price(new BigDecimal("250000"))
                .isActive(true)
                .build());
        Warehouse wh = warehouses.saveAndFlush(Warehouse.builder()
                .code("WH-PROC-" + suffix)
                .name("Kho Trung Tâm")
                .build());

        var supplier = procurementService.saveSupplier(null, new ProcurementService.SupplierCommand(
                "NCC-HIST", "Công ty Dược Mỹ Phẩm ABC", "Minh", null, "0909000111", "HCM", null, true));

        var po = procurementService.create(new ProcurementService.OrderCommand(
                supplier.id(), wh.getId(), LocalDate.now().plusDays(3), "Đợt nhập tháng 11",
                List.of(new ProcurementService.ItemCommand(variant.getId(), 10, new BigDecimal("140000.00"), "LOT-A1", LocalDate.now().plusYears(1)))));
        procurementService.approve(po.id());

        var poDetail = procurementService.order(po.id());
        Long itemId = poDetail.items().getFirst().id();
        receiptService.receive(po.id(), new ProcurementReceiptService.Receipt(
                "RCV-HIST-01", "Nhận đợt 1 (4 chai)",
                List.of(new ProcurementReceiptService.Line(itemId, 4, "LOT-A1", LocalDate.now().plusYears(1)))));

        // Staff without PROCUREMENT gets 403
        mvc.perform(get("/api/v1/admin/procurement/movements").with(authentication(authOf(staffWithoutPerm))))
                .andExpect(status().isForbidden());

        // Staff with PROCUREMENT queries summary, movements, and filtered POs
        mvc.perform(get("/api/v1/admin/procurement/history/summary").with(authentication(authOf(staffWithPerm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptCount").value(1))
                .andExpect(jsonPath("$.data.totalReceiptAmount").value(560000))
                .andExpect(jsonPath("$.data.receivedOrdersCount").value(1))
                .andExpect(jsonPath("$.data.receivedOrdersValue").value(560000))
                .andExpect(jsonPath("$.data.outstandingPayableAmount").value(560000));

        mvc.perform(get("/api/v1/admin/procurement/movements?kind=RECEIPT&keyword=RCV-HIST-01")
                        .with(authentication(authOf(staffWithPerm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(po.orderNumber()))
                .andExpect(jsonPath("$.data.content[0].supplierName").value("Công ty Dược Mỹ Phẩm ABC"))
                .andExpect(jsonPath("$.data.content[0].totalQuantity").value(4))
                .andExpect(jsonPath("$.data.content[0].lines[0].sku").value("SRM-HIST-" + suffix))
                .andExpect(jsonPath("$.data.content[0].lines[0].productName").value("Sữa rửa mặt dịu nhẹ"));

        mvc.perform(get("/api/v1/admin/procurement/orders?statuses=PARTIALLY_RECEIVED,RECEIVED&useReceivedDate=true&supplierId=" + supplier.id())
                        .with(authentication(authOf(staffWithPerm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].receivedQuantity").value(4))
                .andExpect(jsonPath("$.data.content[0].totalQuantity").value(10))
                .andExpect(jsonPath("$.data.content[0].receivedAmount").value(560000));
    }
}
