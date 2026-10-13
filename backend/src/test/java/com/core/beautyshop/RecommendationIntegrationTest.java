package com.core.beautyshop;

import com.core.beautyshop.modules.catalog.api.event.ProductInteractionEvent;
import com.core.beautyshop.modules.catalog.application.service.ProductInteractionRecorder;
import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.modules.catalog.domain.enums.*;
import com.core.beautyshop.modules.catalog.infrastructure.RecommendationClient;
import com.core.beautyshop.modules.order.api.event.OrderEvents;
import com.core.beautyshop.shared.config.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:recommendation;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SystemConfigService configs;
    @Autowired ProductInteractionRecorder recorder;
    @Autowired UserProductInteractionRepository interactions;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @MockitoBean RecommendationClient client;
    @MockitoBean com.core.beautyshop.modules.identity.application.listener.IdentityOrderEventListener identityEvents;
    @MockitoBean com.core.beautyshop.modules.inventory.application.listener.InventoryOrderEventListener inventoryEvents;
    @MockitoBean com.core.beautyshop.modules.promotion.application.listener.PromotionOrderEventListener promotionEvents;

    @Test @WithMockUser(roles="USER") void customerCannotManageAlgorithm() throws Exception {
        mvc.perform(get("/api/v1/admin/recommendations/config")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/recommendations/refresh")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles="ADMIN") void adminSavesValidatedControlsAndAppliesThem() throws Exception {
        when(client.refresh()).thenReturn(Map.of("status","ok"));
        mvc.perform(put("/api/v1/admin/recommendations/config").contentType("application/json")
            .content("{\"weight.purchase\":8,\"history_window\":150,\"exclude_seen\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(true))
            .andExpect(jsonPath("$.data.config.history_window").value(150));
        assertEquals(8.0, configs.getRecommendationConfig().get("weight.purchase"));
        verify(client).refresh();
    }

    @Test @WithMockUser(roles="ADMIN") void invalidValuesDoNotReplaceConfirmedConfig() throws Exception {
        Object previous = configs.getRecommendationConfig().get("history_window");
        mvc.perform(put("/api/v1/admin/recommendations/config").contentType("application/json")
            .content("{\"history_window\":1.5}")) .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/recommendations/config").contentType("application/json")
            .content("{\"min_similarity\":\"NaN\"}")) .andExpect(status().isBadRequest());
        assertEquals(previous, configs.getRecommendationConfig().get("history_window"));
        verifyNoInteractions(client);
    }

    @Test @WithMockUser(roles="ADMIN") void zeroSignalsAreRejected() throws Exception {
        mvc.perform(put("/api/v1/admin/recommendations/config").contentType("application/json")
            .content("{\"weight.view\":0,\"weight.search_click\":0,\"weight.add_to_cart\":0,\"weight.purchase\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(roles="ADMIN") void previewPreservesScoreAndFallbackSource() throws Exception {
        when(client.preview(1L,null,null,8)).thenReturn(Map.of("source","CONTENT_BASED","items",List.of(Map.of("id",2,"score",0.5))));
        mvc.perform(get("/api/v1/admin/recommendations/preview?productId=1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].score").value(0.5));
    }

    @Test void rolledBackCartDoesNotRecordAnInteraction() {
        String session = "rec-" + UUID.randomUUID();
        new TransactionTemplate(manager).execute(tx -> {
            publisher.publishEvent(new ProductInteractionEvent(null,session,1L,"ADD_TO_CART",null));
            tx.setRollbackOnly(); return null;
        });
        assertEquals(0, interactions.findAll().stream().filter(i -> session.equals(i.getSessionId())).count());
    }

    @Test void committedCartRecordsOnceAcrossRapidDuplicates() {
        String session = "rec-" + UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(manager);
        for (int n=0;n<2;n++) tx.execute(status -> { publisher.publishEvent(new ProductInteractionEvent(null,session,1L,"ADD_TO_CART",null)); return null; });
        assertEquals(1, interactions.findAll().stream().filter(i -> session.equals(i.getSessionId())).count());
    }

    @Test void purchaseIsIdempotentAcrossPaidDeliveredAndRemovedAfterReturn() {
        String key = UUID.randomUUID().toString();
        Product p = products.saveAndFlush(Product.builder().name("Recommendation fixture").slug(key)
                .status(ProductStatus.ACTIVE).productType(ProductType.PRODUCT).skinType(SkinType.NORMAL)
                .targetGender(TargetGender.UNISEX).build());
        ProductVariant v = variants.saveAndFlush(ProductVariant.builder().product(p).sku(key).variantName("SKU")
                .price(BigDecimal.TEN).isActive(true).build());
        Long orderId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000000000) + 1;
        String source = "order:" + orderId + ":product:" + p.getId();
        var items = List.of(OrderEvents.OrderItemSummary.builder().variantId(v.getId()).quantity(1).build());
        TransactionTemplate tx = new TransactionTemplate(manager);
        tx.execute(status -> { publisher.publishEvent(OrderEvents.OrderPaidEvent.builder().orderId(orderId).userId(9001L).items(items).build()); return null; });
        tx.execute(status -> { publisher.publishEvent(OrderEvents.OrderDeliveredEvent.builder().orderId(orderId).userId(9001L).items(items).build()); return null; });
        assertEquals(1, interactions.findAll().stream().filter(i -> source.equals(i.getSourceKey())).count());
        tx.execute(status -> { publisher.publishEvent(OrderEvents.OrderReturnedEvent.builder().orderId(orderId).userId(9001L).items(items).build()); return null; });
        assertFalse(interactions.existsBySourceKey(source));
    }

    @Test void explicitSourceKeyDeduplicatesRetries() {
        String source = "fixture:" + UUID.randomUUID();
        var event = new ProductInteractionEvent(null,"fixture-session",1L,"PURCHASE",source);
        recorder.record(event); recorder.record(event);
        assertEquals(1, interactions.findAll().stream().filter(i -> source.equals(i.getSourceKey())).count());
    }
}
