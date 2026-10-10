package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.AttributeDefinitionRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.AttributeValueRequest;
import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.catalog.domain.ProductAttributeDefinitionRepository;
import com.core.beautyshop.modules.catalog.domain.ProductAttributeValueRepository;
import com.core.beautyshop.modules.catalog.domain.enums.AttributeDataType;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductAttributeServiceIntegrationTest {
    @Autowired ProductAttributeService service;
    @Autowired ProductRepository products;
    @Autowired ProductAttributeDefinitionRepository definitions;
    @Autowired ProductAttributeValueRepository values;
    @Autowired MockMvc mvc;
    @Autowired com.core.beautyshop.modules.catalog.domain.ProductVariantRepository variants;

    @Test
    @WithMockUser(roles = "CATALOG_STAFF")
    void httpAttributeValueAcceptsPathIdWithoutRepeatingItInJson() throws Exception {
        Product product = products.saveAndFlush(Product.builder().name("HTTP path regression")
                .slug("http-path-regression-" + UUID.randomUUID()).build());
        AttributeDefinitionRequest definitionRequest = new AttributeDefinitionRequest();
        definitionRequest.setName("HTTP path regression " + UUID.randomUUID());
        definitionRequest.setDataType(AttributeDataType.STRING);
        var definition = service.createDefinition(definitionRequest);
        try {
            mvc.perform(post("/api/v1/attributes/{id}/values", definition.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":" + product.getId() + ",\"value\":\"blue\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.attributeDefinitionId").value(definition.getId().intValue()))
                    .andExpect(jsonPath("$.data.value").value("blue"));
        } finally {
            definitions.deleteById(definition.getId());
            products.deleteById(product.getId());
        }
    }

    @Test
    void descriptionRoundTripsAndOmittedUpdatePreservesItAndOtherDefinitionSettings() {
        AttributeDefinitionRequest request = new AttributeDefinitionRequest();
        request.setName("Description regression " + UUID.randomUUID());
        request.setDataType(AttributeDataType.STRING);
        request.setDescription("Original description");
        var created = service.createDefinition(request);
        try {
            assertEquals("Original description", created.getDescription());
            assertEquals("Original description", service.getDefinitionById(created.getId()).getDescription());
            var stored = definitions.findById(created.getId()).orElseThrow();
            String originalCode = stored.getAttributeCode();
            stored.setUnit("ml");
            stored.setIsFilterable(true);
            definitions.saveAndFlush(stored);

            request.setDescription("Updated description");
            assertEquals("Updated description", service.updateDefinition(created.getId(), request).getDescription());
            assertEquals("Updated description", service.getDefinitionById(created.getId()).getDescription());

            request.setDescription(null);
            service.updateDefinition(created.getId(), request);
            var unchanged = definitions.findById(created.getId()).orElseThrow();
            assertEquals("Updated description", unchanged.getDescription());
            assertEquals(originalCode, unchanged.getAttributeCode());
            assertEquals("ml", unchanged.getUnit());
            assertTrue(unchanged.getIsFilterable());
            assertEquals("Updated description", service.getAllDefinitions().stream()
                    .filter(value -> value.getId().equals(created.getId())).findFirst().orElseThrow().getDescription());
        } finally { definitions.deleteById(created.getId()); }
    }

    @Test
    void pathIdAllowsOmittedBodyIdAndOverridesBodyIdWhileInternalApiRequiresIt() {
        Product product = products.saveAndFlush(Product.builder().name("Path regression")
                .slug("path-regression-" + UUID.randomUUID()).build());
        AttributeDefinitionRequest definitionRequest = new AttributeDefinitionRequest();
        definitionRequest.setName("Path regression " + UUID.randomUUID());
        definitionRequest.setDataType(AttributeDataType.STRING);
        var definition = service.createDefinition(definitionRequest);
        try {
            AttributeValueRequest request = new AttributeValueRequest();
            request.setProductId(product.getId());
            request.setValue("sample");
            assertThrows(BusinessException.class, () -> service.addValue(request));
            assertEquals(definition.getId(), service.addValue(definition.getId(), request).getAttributeDefinitionId());
            request.setAttributeDefinitionId(Long.MAX_VALUE);
            assertEquals(definition.getId(), service.addValue(definition.getId(), request).getAttributeDefinitionId());
        } finally {
            definitions.deleteById(definition.getId());
            products.deleteById(product.getId());
        }
    }

    @Test
    @WithMockUser(roles = "CATALOG_STAFF")
    void productReplacementIsAtomicAndRoundTripsInProductDetail() throws Exception {
        Product product = products.saveAndFlush(Product.builder().name("Replacement regression")
                .slug("replacement-regression-" + UUID.randomUUID()).build());
        var definitionRequest = new AttributeDefinitionRequest();
        definitionRequest.setName("Replacement " + UUID.randomUUID());
        definitionRequest.setDataType(AttributeDataType.NUMBER);
        var definition = service.createDefinition(definitionRequest);
        var request = new com.core.beautyshop.modules.catalog.application.dto.request.ProductAttributeRequest();
        request.setAttributeDefinitionId(definition.getId());
        request.setValue("50");
        try {
            var result = service.replaceProductValues(product.getId(), java.util.List.of(request));
            assertEquals("50", result.getFirst().getValue());
            assertEquals(AttributeDataType.NUMBER, result.getFirst().getDataType());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/products/{id}", product.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.attributeValues[0].value").value("50"));
            assertThrows(BusinessException.class, () -> service.replaceProductValues(product.getId(), java.util.List.of(request, request)));
            request.setValue("invalid");
            assertThrows(BusinessException.class, () -> service.replaceProductValues(product.getId(), java.util.List.of(request)));
            assertEquals("50", service.getValuesByDefinitionId(definition.getId()).getFirst().getValue());
            assertThrows(BusinessException.class, () -> service.replaceProductValues(product.getId(), null));
            request.setValue("51");
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/products/{id}/attributes", product.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"attributeDefinitionId\":" + definition.getId() + ",\"value\":\"51\"}]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].value").value("51"));
            assertTrue(service.replaceProductValues(product.getId(), java.util.List.of()).isEmpty());
            assertTrue(service.getValuesByDefinitionId(definition.getId()).isEmpty());
        } finally {
            definitions.deleteById(definition.getId());
            products.deleteById(product.getId());
        }
    }

    @Test
    void replacementChecksVariantOwnershipAndPreservesOriginalValues() {
        Product product = products.saveAndFlush(Product.builder().name("Scope test").slug("scope-" + UUID.randomUUID()).build());
        Product other = products.saveAndFlush(Product.builder().name("Other scope").slug("other-scope-" + UUID.randomUUID()).build());
        var variant = variants.saveAndFlush(com.core.beautyshop.modules.catalog.domain.ProductVariant.builder()
                .product(product).sku("scope-sku-" + UUID.randomUUID()).variantName("Test")
                .price(java.math.BigDecimal.ZERO).build());
        var definitionRequest = new AttributeDefinitionRequest();
        definitionRequest.setName("Scope " + UUID.randomUUID());
        definitionRequest.setDataType(AttributeDataType.BOOLEAN);
        var definition = service.createDefinition(definitionRequest);
        var request = new com.core.beautyshop.modules.catalog.application.dto.request.ProductAttributeRequest();
        request.setAttributeDefinitionId(definition.getId());
        request.setProductVariantId(variant.getId());
        request.setValue("false");
        try {
            var saved = service.replaceProductValues(product.getId(), java.util.List.of(request)).getFirst();
            assertEquals(variant.getId(), saved.getProductVariantId());
            assertEquals("false", saved.getValue());
            assertThrows(BusinessException.class, () -> service.replaceProductValues(other.getId(), java.util.List.of(request)));
            assertEquals("false", service.getValuesByDefinitionId(definition.getId()).getFirst().getValue());
        } finally {
            definitions.deleteById(definition.getId());
            variants.deleteById(variant.getId());
            products.deleteById(product.getId());
            products.deleteById(other.getId());
        }
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotReplaceProductAttributes() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/products/1/attributes")
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void numericValuesRoundTripOutsideAnOpenPersistenceContextAndRejectInvalidInput() {
        Product product = products.saveAndFlush(Product.builder().name("Attribute regression")
                .slug("attribute-regression-" + UUID.randomUUID()).build());
        AttributeDefinitionRequest definitionRequest = new AttributeDefinitionRequest();
        definitionRequest.setName("Numeric regression " + UUID.randomUUID());
        definitionRequest.setDataType(AttributeDataType.NUMBER);
        var definition = service.createDefinition(definitionRequest);
        try {
            assertEquals(AttributeDataType.NUMBER, definition.getDataType());
            AttributeValueRequest request = new AttributeValueRequest();
            request.setProductId(product.getId());
            request.setAttributeDefinitionId(definition.getId());
            request.setValue("12.50");
            var saved = service.addValue(request);
            assertEquals("12.5", saved.getValue());
            assertEquals(12.5, values.findById(saved.getId()).orElseThrow().getValueNumber());
            assertEquals("12.5", service.getValuesByDefinitionId(definition.getId()).getFirst().getValue());

            request.setValue("not-a-number");
            assertThrows(BusinessException.class, () -> service.addValue(request));
            definitionRequest.setDataType(AttributeDataType.BOOLEAN);
            assertThrows(BusinessException.class, () -> service.updateDefinition(definition.getId(), definitionRequest));
        } finally {
            definitions.deleteById(definition.getId());
            products.deleteById(product.getId());
        }
    }
}
