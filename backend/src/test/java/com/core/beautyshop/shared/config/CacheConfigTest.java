package com.core.beautyshop.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.shared.dto.CacheablePage;
import com.core.beautyshop.modules.spa.application.dto.response.BeautyServiceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    @Test
    void productionCacheManagerIsRedisBacked() {
        CacheManager manager = new CacheConfig()
                .redisCacheManager(
                        mock(RedisConnectionFactory.class),
                        new ObjectMapper().findAndRegisterModules(),
                        900_000L);

        assertInstanceOf(RedisCacheManager.class, manager);
    }

    @Test
    void productPageCanRoundTripAsJson() {
        ProductListResponse product = ProductListResponse.builder()
                .id(10L)
                .name("Serum")
                .minPrice(new BigDecimal("250000"))
                .maxPrice(new BigDecimal("250000"))
                .status(ProductStatus.ACTIVE)
                .build();
        CacheablePage<ProductListResponse> page = CacheablePage.from(new PageImpl<>(
                List.of(product), PageRequest.of(0, 20), 1));
        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(new ObjectMapper().findAndRegisterModules())
                .defaultTyping(true)
                .build();

        byte[] serialized = serializer.serialize(page);
        Object restored = serializer.deserialize(serialized);

        assertNotNull(serialized);
        assertTrue(new String(serialized, StandardCharsets.UTF_8).startsWith("{"));
        CacheablePage<?> restoredPage = assertInstanceOf(CacheablePage.class, restored);
        ProductListResponse restoredProduct = assertInstanceOf(
                ProductListResponse.class, restoredPage.getContent().getFirst());
        assertEquals(new BigDecimal("250000"), restoredProduct.getBasePrice());
    }

    @Test
    void immutableDtoListCanRoundTripAsJson() {
        BeautyServiceResponse service = BeautyServiceResponse.builder()
                .id(5L)
                .name("Facial")
                .basePrice(new BigDecimal("500000"))
                .build();
        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(new ObjectMapper().findAndRegisterModules())
                .defaultTyping(true)
                .build();

        byte[] serialized = serializer.serialize(new ArrayList<>(List.of(service)));
        Object restored = serializer.deserialize(serialized);

        List<?> restoredList = assertInstanceOf(List.class, restored);
        BeautyServiceResponse restoredService = assertInstanceOf(
                BeautyServiceResponse.class, restoredList.getFirst());
        assertEquals("Facial", restoredService.getName());
    }
}
