package com.core.beautyshop;

import com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest;
import com.core.beautyshop.modules.cart.application.service.CartService;
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
import com.core.beautyshop.modules.inventory.domain.WarehouseStock;
import com.core.beautyshop.modules.inventory.domain.WarehouseStockRepository;
import com.core.beautyshop.modules.order.application.dto.request.AssignShipperRequest;
import com.core.beautyshop.modules.order.application.dto.request.CheckoutRequest;
import com.core.beautyshop.modules.order.application.dto.request.UpdateOrderStatusRequest;
import com.core.beautyshop.modules.order.application.dto.response.OrderResponse;
import com.core.beautyshop.modules.order.application.service.OrderService;
import com.core.beautyshop.modules.order.application.service.ShipperOrderService;
import com.core.beautyshop.modules.order.domain.Order;
import com.core.beautyshop.modules.order.domain.OrderRepository;
import com.core.beautyshop.modules.order.domain.ProductReturnRepository;
import com.core.beautyshop.modules.order.domain.enums.OrderStatus;
import com.core.beautyshop.shared.config.SystemConfigService;
import com.core.beautyshop.shared.config.domain.SystemConfig;
import com.core.beautyshop.shared.config.domain.SystemConfigRepository;
import com.core.beautyshop.shared.domain.enums.PaymentMethod;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import com.core.beautyshop.shared.time.BusinessTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shipper_return_pickup_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "app.media.upload-dir=${java.io.tmpdir}/beautyshop-shipper-return-test-uploads"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ShipperReturnIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseStockRepository stocks;
    @Autowired CartService cartService;
    @Autowired OrderService orderService;
    @Autowired ShipperOrderService shipperOrderService;
    @Autowired OrderRepository orders;
    @Autowired ProductReturnRepository productReturns;
    @Autowired SystemConfigService systemConfigs;
    @Autowired SystemConfigRepository configRepository;
    @Autowired PlatformTransactionManager txManager;
    @Autowired DataSource dataSource;

    private String previousShippingThreshold;

    @BeforeEach
    void isolateFixtureConfig() {
        SpaOperationalJdbcFixture.ensure(dataSource);
        var prev = configRepository.findByConfigKeyAndIsDeletedFalse("shipping.free_threshold");
        previousShippingThreshold = prev.map(SystemConfig::getConfigValue).orElse(null);
        if (prev.isPresent()) {
            systemConfigs.update("shipping.free_threshold", "0");
        } else {
            systemConfigs.create("shipping.free_threshold", "0", "Isolated return pickup integration test policy", "DECIMAL");
        }
    }

    @AfterEach
    void restoreConfig() {
        if (previousShippingThreshold == null) {
            systemConfigs.delete("shipping.free_threshold");
        } else {
            systemConfigs.update("shipping.free_threshold", previousShippingThreshold);
        }
    }

    private User createUser(String roleName, String fullName) {
        Role role = roles.findByName(roleName)
                .orElseGet(() -> roles.saveAndFlush(Role.builder().name(roleName).build()));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(User.builder()
                .username("usr_" + suffix)
                .email("usr_" + suffix + "@example.test")
                .fullName(fullName)
                .phone("090" + suffix.replaceAll("[^0-9]", "1").substring(0, 7))
                .passwordHash(encoder.encode("Pass1234!"))
                .roles(new ArrayList<>(List.of(role)))
                .build());
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        UserDetailsImpl principal = UserDetailsImpl.build(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private long createVariantWithStock(int quantity, BigDecimal price) {
        return new TransactionTemplate(txManager).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Product product = products.save(Product.builder().name("Serum Test " + suffix).slug(suffix).build());
            ProductVariant variant = variants.save(ProductVariant.builder()
                    .product(product)
                    .sku("SKU-" + suffix.substring(0, 8))
                    .variantName("30ml")
                    .price(price)
                    .build());
            Warehouse warehouse = warehouses.save(Warehouse.builder().name("Kho Test").code("W-" + suffix.substring(0, 8)).build());
            stocks.save(WarehouseStock.builder()
                    .warehouse(warehouse)
                    .productVariantId(variant.getId())
                    .batchCode("BATCH-" + suffix.substring(0, 8))
                    .quantity(quantity)
                    .expirationDate(BusinessTime.today().plusDays(180))
                    .build());
            return variant.getId();
        });
    }

    private OrderResponse checkoutUserOrder(User customer, long variantId, int qty, PaymentMethod method) {
        String sessionId = "sess-" + UUID.randomUUID();
        AddToCartRequest addReq = new AddToCartRequest();
        addReq.setVariantId(variantId);
        addReq.setQuantity(qty);
        addReq.setSessionId(sessionId);
        cartService.addToCart(customer.getId(), addReq);

        CheckoutRequest checkout = new CheckoutRequest();
        checkout.setSessionId(sessionId);
        checkout.setCustomerName(customer.getFullName());
        checkout.setCustomerPhone("0901234567");
        checkout.setShippingAddress("12 Nguyễn Huệ, Quận 1");
        checkout.setPaymentMethod(method);
        checkout.setIdempotencyKey("ck-" + UUID.randomUUID());
        return orderService.checkout(customer.getId(), checkout);
    }

    private void transitionStatus(long orderId, OrderStatus status) {
        transitionStatus(orderId, status, null);
    }

    private void transitionStatus(long orderId, OrderStatus status, String notes) {
        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(status);
        req.setNotes(notes);
        orderService.updateOrderStatus(orderId, req);
    }

    private MockMultipartFile validPng(String name, String filename) {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, (byte) 0xc4, (byte) 0x89
        };
        return new MockMultipartFile(name, filename, "image/png", png);
    }

    @Test
    void shipperPickupFlowEnforcesCustodyProofReconciliationAndQuarantineReceipt() throws Exception {
        User admin = createUser("ROLE_ADMIN", "Quản trị viên");
        User customer = createUser("ROLE_CUSTOMER", "Nguyễn Minh Thư");
        User shipper1 = createUser("ROLE_SHIPPER", "Tài xế Tuấn");
        User shipper2 = createUser("ROLE_SHIPPER", "Tài xế Khánh");

        long variantId = createVariantWithStock(10, BigDecimal.valueOf(100000));

        // 1. Deliver a COD order (2 units * 100k = 200k COD collected by shipper1)
        OrderResponse deliveredOrder = checkoutUserOrder(customer, variantId, 2, PaymentMethod.COD);
        transitionStatus(deliveredOrder.getId(), OrderStatus.CONFIRMED);
        transitionStatus(deliveredOrder.getId(), OrderStatus.PROCESSING);
        orderService.assignShipper(deliveredOrder.getId(), new AssignShipperRequest(shipper1.getId(), "Giao giờ hành chính"));
        transitionStatus(deliveredOrder.getId(), OrderStatus.SHIPPED);

        shipperOrderService.startDelivery(deliveredOrder.getId(), shipper1.getId());
        shipperOrderService.completeDelivery(
                deliveredOrder.getId(),
                shipper1.getId(),
                validPng("proofImage", "delivery.png"),
                "Đã thu COD 200k"
        );

        long orderItemId = deliveredOrder.getItems().getFirst().getId();

        // 2. Customer creates a SHIPPER_PICKUP return request for 1 unit (100k)
        String createReturnBody = """
                {
                  "reason": "Vòi nhấn bị kẹt khi mở hộp",
                  "returnMethod": "SHIPPER_PICKUP",
                  "refundMethod": "VOUCHER",
                  "lines": [{"orderItemId": %d, "quantity": 1}],
                  "evidenceUrls": ["https://cdn.example.test/damaged.jpg"]
                }
                """.formatted(orderItemId);

        String createReturnResp = mvc.perform(post("/api/v1/orders/" + deliveredOrder.getId() + "/returns")
                        .with(authentication(authOf(customer)))
                        .header("Idempotency-Key", "ret-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReturnBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.returnMethod").value("SHIPPER_PICKUP"))
                .andExpect(jsonPath("$.data.refundAmount").value(100000))
                .andReturn().getResponse().getContentAsString();

        long returnId = objectMapper.readTree(createReturnResp).path("data").path("id").asLong();

        // 3. Admin approves the return request (stays APPROVED until pickup shipper assigned)
        mvc.perform(post("/api/v1/admin/commerce/returns/" + returnId + "/decision")
                        .with(authentication(authOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approved": true, "reason": "Đủ điều kiện đổi trả"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Warehouse receipt MUST fail before shipper picks up SHIPPER_PICKUP return
        mvc.perform(post("/api/v1/admin/commerce/returns/" + returnId + "/receive")
                        .with(authentication(authOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference": "RCV-PREMATURE", "reason": "Thử nhận sớm"}
                                """))
                .andExpect(status().isBadRequest());

        // Reconciliation shows pendingPickupReturnsCount == 1
        mvc.perform(get("/api/v1/admin/orders/reconciliation/summary")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCodAmount").value(200000))
                .andExpect(jsonPath("$.data.pendingPickupReturnsCount").value(1))
                .andExpect(jsonPath("$.data.inCustodyReturnsCount").value(0));

        // 4. Admin assigns pickup shipper1 -> PICKUP_ASSIGNED + SHIPPER_RETURN_PICKUP notification
        mvc.perform(post("/api/v1/admin/commerce/returns/" + returnId + "/assign-pickup")
                        .with(authentication(authOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipperId": %d, "notes": "Liên hệ khách trước khi tới"}
                                """.formatted(shipper1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKUP_ASSIGNED"))
                .andExpect(jsonPath("$.data.pickupShipperId").value(shipper1.getId()));

        mvc.perform(get("/api/v1/users/notifications")
                        .with(authentication(authOf(shipper1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].type", hasItem("SHIPPER_RETURN_PICKUP")))
                .andExpect(jsonPath("$.data.content[*].target", hasItem("/api/v1/shipper/returns/" + returnId)));

        // Non-owner shipper2 cannot access return detail
        mvc.perform(get("/api/v1/shipper/returns/" + returnId)
                        .with(authentication(authOf(shipper2))))
                .andExpect(status().isNotFound());

        // 5. Shipper1 cannot complete pickup before starting trip
        mvc.perform(multipart("/api/v1/shipper/returns/" + returnId + "/pickup")
                        .file(validPng("proofImage", "return-proof.png"))
                        .param("notes", "Thử lấy sớm")
                        .with(authentication(authOf(shipper1))))
                .andExpect(status().isBadRequest());

        // Start pickup -> report failed attempt -> reverts to APPROVED with attemptCount=1
        mvc.perform(patch("/api/v1/shipper/returns/" + returnId + "/start-pickup")
                        .with(authentication(authOf(shipper1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickupStartedAt").isNotEmpty());

        mvc.perform(post("/api/v1/shipper/returns/" + returnId + "/pickup-failed")
                        .with(authentication(authOf(shipper1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Khách bận hẹn chiều mai"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.pickupAttemptCount").value(1))
                .andExpect(jsonPath("$.data.pickupFailureReason").value("Khách bận hẹn chiều mai"));

        // 6. Re-assign, start, and complete pickup with mandatory photo -> PICKED_UP
        mvc.perform(post("/api/v1/admin/commerce/returns/" + returnId + "/assign-pickup")
                        .with(authentication(authOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipperId": %d}
                                """.formatted(shipper1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKUP_ASSIGNED"));

        mvc.perform(patch("/api/v1/shipper/returns/" + returnId + "/start-pickup")
                        .with(authentication(authOf(shipper1))))
                .andExpect(status().isOk());

        mvc.perform(multipart("/api/v1/shipper/returns/" + returnId + "/pickup")
                        .file(validPng("proofImage", "pickup-ok.png"))
                        .param("notes", "Đã kiểm đủ 1 chai serum")
                        .with(authentication(authOf(shipper1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.pickupProofUrl", containsString("/uploads/")))
                .andExpect(jsonPath("$.data.pickedUpAt").isNotEmpty());

        // 7. Reconciliation tracks both shipper COD (200k) and physical return custody (1 item = 100k)
        String custodySummary = mvc.perform(get("/api/v1/admin/orders/reconciliation/summary")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCodAmount").value(200000))
                .andExpect(jsonPath("$.data.pendingPickupReturnsCount").value(0))
                .andExpect(jsonPath("$.data.inCustodyReturnsCount").value(1))
                .andExpect(jsonPath("$.data.inCustodyReturnValue").value(100000))
                .andReturn().getResponse().getContentAsString();

        JsonNode shipperRow = objectMapper.readTree(custodySummary).path("data").path("byShipper").get(0);
        assertEquals(shipper1.getId(), shipperRow.path("shipperId").asLong());
        assertEquals(1, shipperRow.path("inCustodyReturnsCount").asLong());
        assertEquals(0, BigDecimal.valueOf(100000).compareTo(shipperRow.path("inCustodyReturnAmount").decimalValue()));
        assertEquals(0, BigDecimal.valueOf(200000).compareTo(shipperRow.path("pendingAmount").decimalValue()));

        // 8. Shipper reports handover to warehouse -> filtered admin returns list reflects it
        mvc.perform(post("/api/v1/shipper/returns/" + returnId + "/handover")
                        .with(authentication(authOf(shipper1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "Đã bàn giao thủ kho ca chiều"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoverRequestedAt").isNotEmpty());

        mvc.perform(get("/api/v1/admin/commerce/returns")
                        .param("status", "PICKED_UP")
                        .param("returnMethod", "SHIPPER_PICKUP")
                        .param("pickupShipperId", String.valueOf(shipper1.getId()))
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(returnId))
                .andExpect(jsonPath("$.data.content[0].handoverNotes").value("Đã bàn giao thủ kho ca chiều"));

        // 9. Warehouse receives return -> RECEIVED, moves 1 unit to quarantine, clears shipper custody
        mvc.perform(post("/api/v1/admin/commerce/returns/" + returnId + "/receive")
                        .with(authentication(authOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference": "RCV-WH-101", "reason": "Thủ kho đã kiểm nhận"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.receivedAt").isNotEmpty());

        List<WarehouseStock> variantStocks = stocks.findAll().stream()
                .filter(s -> Long.valueOf(variantId).equals(s.getProductVariantId()))
                .toList();
        assertEquals(1, variantStocks.stream().mapToInt(WarehouseStock::getQuarantinedQuantity).sum());

        mvc.perform(get("/api/v1/admin/orders/reconciliation/summary")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inCustodyReturnsCount").value(0))
                .andExpect(jsonPath("$.data.awaitingRefundReturnsCount").value(1))
                .andExpect(jsonPath("$.data.awaitingRefundReturnAmount").value(100000));

        // 10. Admin issues voucher refund -> REFUNDED; settle COD debt -> 0 pending COD
        mvc.perform(post("/api/v1/admin/commerce/returns/" + returnId + "/refund")
                        .with(authentication(authOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference": "RFD-101", "reason": "Hoàn voucher theo yêu cầu"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundVoucherCode").isNotEmpty());

        mvc.perform(post("/api/v1/admin/commerce/orders/" + deliveredOrder.getId() + "/cod-settlement")
                        .with(authentication(authOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 200000, "reference": "COD-SETTLE-101", "reason": "Đối soát shipper cuối ngày"}
                                """))
                .andExpect(status().isOk());

        // 11. Whole-order non-COD RETURNING_TO_SENDER failure is tracked under custody without polluting pending COD
        OrderResponse prepaidOrder = checkoutUserOrder(customer, variantId, 1, PaymentMethod.BANK);
        try {
            SecurityContextHolder.getContext().setAuthentication(authOf(admin));
            orderService.manualMarkPaid(prepaidOrder.getId(), "MANUAL-BANK-" + UUID.randomUUID());
        } finally {
            SecurityContextHolder.clearContext();
        }
        transitionStatus(prepaidOrder.getId(), OrderStatus.CONFIRMED);
        transitionStatus(prepaidOrder.getId(), OrderStatus.PROCESSING);
        orderService.assignShipper(prepaidOrder.getId(), new AssignShipperRequest(shipper1.getId(), "Giao sáng"));
        transitionStatus(prepaidOrder.getId(), OrderStatus.SHIPPED);
        transitionStatus(prepaidOrder.getId(), OrderStatus.RETURNING_TO_SENDER, "Khách không nghe máy nhiều lần, chuyển hoàn về kho");

        Order reloadedPrepaid = orders.findById(prepaidOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.RETURNING_TO_SENDER, reloadedPrepaid.getStatus());

        mvc.perform(get("/api/v1/admin/orders/reconciliation/summary")
                        .with(authentication(authOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCodAmount").value(0))
                .andExpect(jsonPath("$.data.awaitingRefundReturnsCount").value(0))
                .andExpect(jsonPath("$.data.inCustodyReturnsCount").value(1))
                .andExpect(jsonPath("$.data.inCustodyReturnValue").value(100000));
    }
}

