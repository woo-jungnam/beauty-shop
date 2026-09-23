package com.core.beautyshop.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Beauty Shop Modular Monolith API Specification")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Beauty Shop Engineering Team")
                                .email("dev@beautyshop.com")
                                .url("https://beautyshop.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("/").description("Môi trường hiện tại (Local / Proxy Gateway)")
                ))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập JWT Access Token để xác thực người dùng")));
    }

    @Bean
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("0. Toàn bộ Hệ thống (All APIs)")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi catalogApis() {
        return GroupedOpenApi.builder()
                .group("1. Catalog & Sản phẩm")
                .pathsToMatch("/api/v1/products/**", "/api/v1/categories/**", "/api/v1/brands/**", "/api/v1/tags/**", "/api/v1/attributes/**")
                .build();
    }

    @Bean
    public GroupedOpenApi identityApis() {
        return GroupedOpenApi.builder()
                .group("2. Định danh & Người dùng")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/test/**")
                .build();
    }

    @Bean
    public GroupedOpenApi orderCartApis() {
        return GroupedOpenApi.builder()
                .group("3. Giỏ hàng & Đơn hàng")
                .pathsToMatch("/api/v1/orders/**", "/api/v1/cart/**", "/api/v1/admin/orders/**")
                .build();
    }

    @Bean
    public GroupedOpenApi paymentApis() {
        return GroupedOpenApi.builder()
                .group("4. Thanh toán & Webhooks")
                .pathsToMatch("/api/v1/payment/**")
                .build();
    }

    @Bean
    public GroupedOpenApi inventoryApis() {
        return GroupedOpenApi.builder()
                .group("5. Kho hàng & Tồn kho")
                .pathsToMatch("/api/v1/inventory/**", "/api/v1/warehouses/**", "/api/v1/admin/warehouses/**")
                .build();
    }

    @Bean
    public GroupedOpenApi spaApis() {
        return GroupedOpenApi.builder()
                .group("6. Dịch vụ Spa & Lịch hẹn")
                .pathsToMatch("/api/v1/spa/**", "/api/v1/appointments/**")
                .build();
    }

    @Bean
    public GroupedOpenApi chatbotApis() {
        return GroupedOpenApi.builder()
                .group("7. Trợ lý Chatbot AI")
                .pathsToMatch("/api/v1/chatbot/**")
                .build();
    }
}
