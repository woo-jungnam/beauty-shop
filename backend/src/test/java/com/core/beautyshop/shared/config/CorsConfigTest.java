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
        assertNull(configuration.checkOrigin("https://untrusted.example"));
        assertNull(configuration.checkOrigin("https://dermascan.world.untrusted.example"));
        assertNull(configuration.checkOrigin("http://dermascan.world"));
        assertNull(configuration.checkOrigin("null"));
        assertTrue(configuration.getAllowCredentials());
        assertTrue(configuration.getAllowedMethods().contains("OPTIONS"));
        assertNull(configuration.getAllowedOriginPatterns());
    }
}
