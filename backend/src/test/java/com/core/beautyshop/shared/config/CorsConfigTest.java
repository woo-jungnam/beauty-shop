package com.core.beautyshop.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {
    @Test
    void onlyExactProductionOriginsAreAllowedWithCredentials() {
        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/products");
        var configuration = new CorsConfig().corsConfigurationSource().getCorsConfiguration(request);
        assertNotNull(configuration);
        assertEquals("https://dermascan.world", configuration.checkOrigin("https://dermascan.world"));
        assertEquals("https://www.dermascan.world", configuration.checkOrigin("https://www.dermascan.world"));
        assertEquals("http://localhost:8080", configuration.checkOrigin("http://localhost:8080"));
        assertNull(configuration.checkOrigin("https://untrusted.example"));
        assertNull(configuration.checkOrigin("https://dermascan.world.untrusted.example"));
        assertNull(configuration.checkOrigin("http://dermascan.world"));
        assertNull(configuration.checkOrigin("null"));
        assertTrue(configuration.getAllowCredentials());
        assertTrue(configuration.getAllowedMethods().contains("OPTIONS"));
        assertNull(configuration.getAllowedOriginPatterns());
    }

    @Test
    void customOriginsAreIncludedWhenConfigured() {
        var corsConfig = new CorsConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(corsConfig, "customOrigins", "https://staging.dermascan.world, https://preview.example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(corsConfig, "customerUrl", "https://app.dermascan.world/");

        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/products");
        var configuration = corsConfig.corsConfigurationSource().getCorsConfiguration(request);
        assertNotNull(configuration);
        assertEquals("https://staging.dermascan.world", configuration.checkOrigin("https://staging.dermascan.world"));
        assertEquals("https://preview.example.com", configuration.checkOrigin("https://preview.example.com"));
        assertEquals("https://app.dermascan.world", configuration.checkOrigin("https://app.dermascan.world"));
    }
}
