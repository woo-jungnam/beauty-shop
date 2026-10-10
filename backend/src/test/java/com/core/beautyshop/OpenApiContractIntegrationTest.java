package com.core.beautyshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api_contract;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    private JsonNode document;

    @BeforeEach void loadDocument() throws Exception { document = json("/v3/api-docs"); }
    private JsonNode json(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private JsonNode operation(String path, String method) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertFalse(operation.isMissingNode(), method + " " + path);
        return operation;
    }
    private JsonNode schema(JsonNode node) {
        return node.has("$ref") ? document.at(node.path("$ref").asText().substring(1)) : node;
    }

    @Test void documentsEveryMappedApiWithSummaryTagsUniqueIdsAndResolvableReferences() {
        int count = 0;
        Set<String> ids = new HashSet<>();
        Set<String> registeredTags = new HashSet<>();
        document.path("tags").forEach(tag -> registeredTags.add(tag.path("name").asText()));
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (entry.getValue().hasMethodAnnotation(Hidden.class) || entry.getValue().getBeanType().isAnnotationPresent(Hidden.class)) continue;
            for (String mappedPath : entry.getKey().getPatternValues()) {
                if (!mappedPath.startsWith("/api/v1/")) continue;
                // OpenAPI strips Spring's path-variable regex, e.g. {id:[0-9]+} -> {id}.
                String path = mappedPath.replaceAll("\\{([^}:]+):[^}]+\\}", "{$1}");
                for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                    JsonNode operation = operation(path, method.name().toLowerCase(Locale.ROOT));
                    assertFalse(operation.path("summary").asText().isBlank(), method + " " + path + " summary");
                    assertTrue(operation.path("tags").size() > 0, path + " tags");
                    operation.path("tags").forEach(tag -> assertTrue(registeredTags.contains(tag.asText()), tag.asText()));
                    assertTrue(ids.add(operation.path("operationId").asText()), "Duplicate operationId: " + operation.path("operationId"));
                    count++;
                }
            }
        }
        assertTrue(count > 200, "Inventory unexpectedly lost API operations: " + count);
        assertReferences(document, document);
    }

    @Test void searchDocumentsOptionalCombinedFiltersAndBoundedPagination() {
        JsonNode search = operation("/api/v1/products/search", "get");
        assertEquals(0, search.path("security").size());
        Map<String, JsonNode> parameters = new HashMap<>();
        for (JsonNode parameter : search.path("parameters")) {
            assertEquals("query", parameter.path("in").asText());
            assertNull(parameters.put(parameter.path("name").asText(), parameter), "Duplicate search parameter");
            assertFalse(parameter.path("required").asBoolean(), parameter.toString());
        }
        Set<String> expected = Set.of("keyword", "brandId", "brandIds", "categoryId", "categoryIds", "tagIds",
                "productType", "skinType", "targetGender", "minPrice", "maxPrice", "minRating", "isFeatured",
                "onSale", "inStock", "hasFragrance", "hasAlcohol", "originCountry", "ingredientIds",
                "skinConcernIds", "page", "size", "sort");
        assertEquals(expected, parameters.keySet());
        assertEquals(200, parameters.get("keyword").path("schema").path("maxLength").asInt());
        assertEquals(0, parameters.get("page").path("schema").path("minimum").asInt(-1));
        assertEquals(1, parameters.get("size").path("schema").path("minimum").asInt());
        assertEquals(100, parameters.get("size").path("schema").path("maximum").asInt());
        assertEquals("array", parameters.get("brandIds").path("schema").path("type").asText());
        assertEquals("array", parameters.get("sort").path("schema").path("type").asText());
        assertTrue(parameters.get("skinType").path("schema").path("enum").toString().contains("ALL_SKIN"));
        assertTrue(search.path("responses").has("400"));
    }

    private void assertReferences(JsonNode node, JsonNode root) {
        if (node.has("$ref") && node.path("$ref").asText().startsWith("#/")) {
            assertFalse(root.at(node.path("$ref").asText().substring(1)).isMissingNode(), "Broken reference " + node.path("$ref"));
        }
        node.elements().forEachRemaining(child -> assertReferences(child, root));
    }

    @Test void securitySeparatesPublicGuestJwtAndWebhookAndMatchesHttpAccess() throws Exception {
        assertEquals(0, operation("/api/v1/auth/login", "post").path("security").size());
        assertEquals(0, operation("/api/v1/products", "get").path("security").size());
        assertTrue(operation("/api/v1/auth/profile", "get").path("security").get(0).has("bearerAuth"));
        assertTrue(operation("/api/v1/products", "post").path("security").get(0).has("bearerAuth"));
        JsonNode guest = operation("/api/v1/orders/checkout", "post").path("security");
        assertEquals(2, guest.size()); assertEquals(0, guest.get(0).size()); assertTrue(guest.get(1).has("bearerAuth"));
        JsonNode webhook = operation("/api/v1/payment/sepay-webhook", "post").path("security");
        assertEquals(1, webhook.size()); assertTrue(webhook.get(0).has("sepayApiKey"));
        assertEquals("Authorization", document.path("components").path("securitySchemes").path("sepayApiKey").path("name").asText());
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/profile")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/payment/sepay-webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void groupsAreCompleteOrderedReachableAndManagementExcludesPublicReads() throws Exception {
        JsonNode config = json("/v3/api-docs/swagger-config");
        assertEquals(13, config.path("urls").size());
        List<String> names = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (JsonNode group : config.path("urls")) {
            names.add(group.path("name").asText());
            JsonNode spec = json(group.path("url").asText());
            assertTrue(spec.path("paths").size() > 0, group.toString());
            assertReferences(spec, spec);
            if (!group.path("url").asText().endsWith("/00-all") && !group.path("url").asText().endsWith("/01-admin")) spec.path("paths").fieldNames().forEachRemaining(covered::add);
        }
        assertEquals(names.stream().sorted().toList(), names);
        document.path("paths").fieldNames().forEachRemaining(path -> assertTrue(covered.contains(path), "Missing business group: " + path));
        JsonNode admin = json("/v3/api-docs/01-admin").path("paths");
        assertTrue(admin.path("/api/v1/categories").has("post"));
        assertFalse(admin.path("/api/v1/categories").has("get"));
        assertTrue(admin.path("/api/v1/appointments/{id}/check-in").has("put"));
        assertTrue(admin.path("/api/v1/spa/appointments/{id}/care-summary").has("get"));
        assertTrue(admin.path("/api/v1/spa/appointments/{id}/preparation/items/{itemId}/acknowledge-warnings").has("post"));
        assertTrue(admin.path("/api/v1/spa/appointments/{id}/preparation/items/{itemId}/override").has("post"));
        assertTrue(admin.path("/api/v1/spa/appointments/{id}/follow-up-instructions/items/{itemId}").has("post"));
        assertTrue(json("/v3/api-docs/06-spa").path("paths").has("/api/v1/admin/dashboard/spa-financials"));
    }

    @Test @WithMockUser(roles = "CATALOG_STAFF")
    void catalogStaffCanReadDermatologyReferenceData() throws Exception {
        mvc.perform(get("/api/v1/admin/dermatology/skin-types"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/v1/admin/dermatology/skin-concerns"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }

    @Test void schemasDescribeHeadersMultipartPaginationAndSuccessStatus() {
        JsonNode invoice = operation("/api/v1/appointments/{id}/invoice", "post");
        assertTrue(hasParameter(invoice, "Idempotency-Key", "header", true));
        JsonNode cash = operation("/api/v1/appointments/{id}/invoice/cash-receipts", "post");
        assertTrue(hasParameter(cash, "Idempotency-Key", "header", true));
        JsonNode upload = schema(operation("/api/v1/admin/media/upload", "post").path("requestBody").path("content").path("multipart/form-data").path("schema"));
        assertEquals("binary", upload.path("properties").path("file").path("format").asText());
        JsonNode page = operation("/api/v1/admin/audit-logs", "get");
        assertTrue(hasParameter(page, "page", "query", false));
        assertTrue(hasParameter(page, "size", "query", false));
        assertFalse(hasParameter(page, "pageable", "query", false));
        assertTrue(operation("/api/v1/auth/register", "post").path("responses").has("201"));
        assertTrue(operation("/api/v1/orders/checkout", "post").path("responses").has("201"));
        assertEquals("#/components/schemas/ApiErrorResponse", invoice.path("responses").path("400").path("content").path("application/json").path("schema").path("$ref").asText());
        assertTrue(document.path("components").path("schemas").path("ApiErrorResponse").path("properties").path("timestamp").path("format").isMissingNode());
        JsonNode invoiceResult = schema(invoice.path("responses").path("200").path("content").path("application/json").path("schema"));
        assertEquals("string", invoiceResult.path("properties").path("timestamp").path("type").asText());
        assertTrue(invoiceResult.path("properties").path("timestamp").path("format").isMissingNode());
    }
    private boolean hasParameter(JsonNode operation, String name, String in, boolean required) {
        for (JsonNode parameter : operation.path("parameters")) if (parameter.path("name").asText().equals(name) && parameter.path("in").asText().equals(in) && (!required || parameter.path("required").asBoolean())) return true;
        return false;
    }

    @Test @WithMockUser(roles = "ADMIN")
    void missingRequiredHeaderAndMultipartPartAreClientErrors() throws Exception {
        mvc.perform(post("/api/v1/appointments/1/invoice").contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"BANK\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("SYS_002"));
        mvc.perform(multipart("/api/v1/admin/media/upload"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("SYS_002"));
    }

    @Test void exportVerifiedSnapshot() throws Exception {
        Path output = Path.of("target-spa-validation", "api-docs");
        Files.createDirectories(output);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("openapi.json").toFile(), document);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("swagger-config.json").toFile(), json("/v3/api-docs/swagger-config"));
    }
}
