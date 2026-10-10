package com.core.beautyshop.modules.catalog.application.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductResponseJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void productListComputedPriceAliasesRemainReadableAndRoundTrip() throws Exception {
        var row = ProductListResponse.builder().minPrice(new BigDecimal("123.45")).maxPrice(new BigDecimal("150")).build();
        String json = mapper.writeValueAsString(row);
        assertEquals(new BigDecimal("123.45"), mapper.readTree(json).get("basePrice").decimalValue());
        assertEquals(new BigDecimal("123.45"), mapper.readTree(json).get("price").decimalValue());
        assertEquals(row.getMinPrice(), mapper.readValue(json, ProductListResponse.class).getMinPrice());
    }

    @Test
    void productDetailComputedBasePriceAliasRoundTrips() throws Exception {
        var row = ProductResponse.builder().minPrice(new BigDecimal("123.45")).maxPrice(new BigDecimal("150")).build();
        String json = mapper.writeValueAsString(row);
        assertEquals(new BigDecimal("123.45"), mapper.readTree(json).get("basePrice").decimalValue());
        assertEquals(row.getMinPrice(), mapper.readValue(json, ProductResponse.class).getMinPrice());
    }
}
