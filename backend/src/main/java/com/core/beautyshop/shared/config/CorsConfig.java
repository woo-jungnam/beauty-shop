package com.core.beautyshop.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class CorsConfig {

    public static final List<String> ALLOWED_ORIGINS = List.of(
            "https://dermascan.world",
            "https://www.dermascan.world",
            "http://localhost:8080",
            "http://127.0.0.1:8080",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost"
    );

    @Value("${app.cors.allowed-origins:}")
    private String customOrigins;

    @Value("${app.customer-url:}")
    private String customerUrl;

    public List<String> getAllowedOrigins() {
        Set<String> origins = new LinkedHashSet<>(ALLOWED_ORIGINS);
        if (customOrigins != null && !customOrigins.isBlank()) {
            Arrays.stream(customOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }
        if (customerUrl != null && !customerUrl.isBlank()) {
            origins.add(customerUrl.trim().replaceAll("/+$", ""));
        }
        return new ArrayList<>(origins);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(getAllowedOrigins());

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Link", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
