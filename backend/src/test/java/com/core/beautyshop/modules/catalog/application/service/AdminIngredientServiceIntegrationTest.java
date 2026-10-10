package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminIngredientServiceIntegrationTest {
    @Autowired AdminIngredientService service;
    @Autowired ProductRepository products;
    @Autowired IngredientRepository ingredients;
    @Autowired ProductService productService;

    @Test
    void metadataAndMappingsRoundTripAndInvalidReplacementPreservesData() {
        var product = products.saveAndFlush(Product.builder().name("Ingredient test")
                .slug("ingredient-test-" + UUID.randomUUID()).build());
        var command = new AdminIngredientService.IngredientCommand("Niacinamide", "Niacinamide",
                "test-" + UUID.randomUUID(), "Description", List.of("humectant"), List.of("Hydrating"),
                List.of("Caution"), 1, true);
        var ingredient = service.save(null, command);
        try {
            var edited = service.save(ingredient.id(), command);
            assertEquals(List.of("humectant"), edited.functions());
            assertEquals(List.of("Hydrating"), edited.benefits());
            assertEquals(List.of("Caution"), edited.potentialConcerns());
            var mapping = new AdminIngredientService.ProductIngredientCommand(ingredient.id(), new BigDecimal("10"), "%", true, 0);
            var result = service.replaceProductIngredients(product.getId(), List.of(mapping));
            assertEquals(0, new BigDecimal("10").compareTo(result.getFirst().concentration()));
            assertTrue(result.getFirst().keyActive());
            assertEquals(ingredient.id(), service.productIngredients(product.getId()).getFirst().ingredientId());
            var response = productService.getProductByIdForAdmin(product.getId());
            assertEquals("Niacinamide", response.getIngredientSummary().getKeyActives().getFirst());
            assertEquals(List.of("Caution"), response.getIngredientsList().getFirst().getPotentialConcerns());
            assertThrows(BusinessException.class, () -> service.replaceProductIngredients(product.getId(), List.of(mapping, mapping)));
            assertEquals(1, service.productIngredients(product.getId()).size());
            assertTrue(service.replaceProductIngredients(product.getId(), List.of()).isEmpty());
        } finally {
            service.replaceProductIngredients(product.getId(), List.of());
            products.deleteById(product.getId());
            ingredients.deleteById(ingredient.id());
        }
    }
}
