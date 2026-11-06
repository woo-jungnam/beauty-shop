package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.response.BannerResponse;
import com.core.beautyshop.modules.catalog.domain.Banner;
import com.core.beautyshop.modules.catalog.domain.BannerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BannerCacheSerializationTest {
    @Test void activeBannersSurviveTheProductionRedisSerializerRoundTrip() {
        var repository = mock(BannerRepository.class);
        var banner = Banner.builder().title("Image banner").imageUrl("/uploads/banner.webp").targetUrl("booking").build();
        when(repository.findByIsDeletedFalseAndIsActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(banner));
        var service = new BannerServiceImpl(repository);
        var serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(new ObjectMapper().registerModule(new JavaTimeModule())).defaultTyping(true).build();
        Object restored = serializer.deserialize(serializer.serialize(service.getActiveBanners(null)));
        assertInstanceOf(List.class, restored);
        var row = assertInstanceOf(BannerResponse.class, ((List<?>) restored).getFirst());
        assertEquals("/uploads/banner.webp", row.getImageUrl());
        assertEquals("booking", row.getTargetUrl());
        when(repository.findByIsDeletedFalseAndIsActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of());
        assertEquals(List.of(), serializer.deserialize(serializer.serialize(service.getActiveBanners(null))));
    }
}
