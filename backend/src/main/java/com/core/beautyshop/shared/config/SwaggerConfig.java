package com.core.beautyshop.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
                        .version("v1")
                        .description("API cửa hàng mỹ phẩm và Spa làm đẹp không xâm lấn. Chọn nhóm nghiệp vụ để tra cứu. "
                                + "JWT dùng bearerAuth; webhook SePay dùng sepayApiKey riêng. API công khai không yêu cầu JWT. "
                                + "Quyền sở hữu, phân công và điều kiện trạng thái được kiểm tra thêm tại từng nghiệp vụ. "
                                + "JSON thông thường bọc trong ApiResponse; SSE và OpenAPI của chatbot có định dạng riêng."))
                .servers(List.of(
                        new Server().url("/").description("Môi trường hiện tại (Local / Proxy Gateway)")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Chỉ nhập accessToken từ POST /api/v1/auth/login; không nhập refreshToken hoặc tiền tố Bearer."))
                        .addSecuritySchemes("sepayApiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("Authorization")
                                .description("Chỉ dùng cho webhook SePay. Nhập nguyên giá trị Apikey <secret> đã cấu hình trên máy chủ; đây không phải JWT khách hàng.")));
    }

    @Bean
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("00-all").displayName("00 · Toàn bộ API")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminPortalApis() {
        return GroupedOpenApi.builder()
                .group("01-admin").displayName("01 · Quản trị và nhân viên")
                .pathsToMatch("/api/v1/**")
                .addOpenApiMethodFilter(ApiDocumentationCustomizer::isManagementMethod)
                .build();
    }

    @Bean
    public GroupedOpenApi catalogApis() {
        return GroupedOpenApi.builder()
                .group("03-catalog").displayName("03 · Sản phẩm, danh mục và hình ảnh")
                .pathsToMatch("/api/v1/products/**", "/api/v1/categories/**", "/api/v1/brands/**", "/api/v1/tags/**", "/api/v1/attributes/**", "/api/v1/admin/products/**", "/api/v1/admin/ingredients/**", "/api/v1/admin/dermatology/**", "/api/v1/admin/media/**")
                .build();
    }

    @Bean
    public GroupedOpenApi identityApis() {
        return GroupedOpenApi.builder()
                .group("02-identity").displayName("02 · Xác thực, tài khoản và vai trò")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/test/**", "/api/v1/admin/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi orderCartApis() {
        return GroupedOpenApi.builder()
                .group("04-orders").displayName("04 · Giỏ hàng, đơn hàng và hoàn tiền")
                .pathsToMatch("/api/v1/orders/**", "/api/v1/cart/**", "/api/v1/admin/orders/**")
                .build();
    }

    @Bean
    public GroupedOpenApi paymentApis() {
        return GroupedOpenApi.builder()
                .group("05-payments").displayName("05 · Thu tiền, đối soát và webhook")
                .pathsToMatch("/api/v1/payment/**", "/api/v1/admin/payments/**", "/api/v1/appointments/*/invoice/**", "/api/v1/admin/orders/*/refund-confirmation")
                .build();
    }

    @Bean
    public GroupedOpenApi inventoryApis() {
        return GroupedOpenApi.builder()
                .group("08-inventory").displayName("08 · Kho, kiểm kê và mua hàng")
                .pathsToMatch("/api/v1/admin/warehouses/**", "/api/v1/admin/inventory/**", "/api/v1/admin/procurement/**")
                .build();
    }

    @Bean
    public GroupedOpenApi spaApis() {
        return GroupedOpenApi.builder()
                .group("06-spa").displayName("06 · Spa, liệu trình và lịch hẹn")
                .pathsToMatch("/api/v1/spa/**", "/api/v1/appointments/**", "/api/v1/admin/spa/**", "/api/v1/admin/staff/**", "/api/v1/admin/dashboard/spa-*")
                .build();
    }

    @Bean
    public GroupedOpenApi reviewApis() {
        return GroupedOpenApi.builder()
                .group("10-reviews").displayName("10 · Đánh giá và kiểm duyệt")
                .pathsToMatch("/api/v1/reviews/**", "/api/v1/admin/reviews/**")
                .build();
    }

    @Bean
    public GroupedOpenApi voucherApis() {
        return GroupedOpenApi.builder()
                .group("09-promotions").displayName("09 · Khuyến mãi và voucher")
                .pathsToMatch("/api/v1/admin/vouchers/**")
                .build();
    }

    @Bean
    public GroupedOpenApi crmApis() {
        return GroupedOpenApi.builder()
                .group("07-crm").displayName("07 · CRM, chuẩn bị và chăm sóc sau buổi")
                .pathsToMatch("/api/v1/admin/crm/**", "/api/v1/spa/appointments/**", "/api/v1/admin/spa/preparation/**")
                .build();
    }

    @Bean
    public GroupedOpenApi systemOperationsApis() {
        return GroupedOpenApi.builder()
                .group("11-operations").displayName("11 · Dashboard, cấu hình và kiểm toán")
                .pathsToMatch("/api/v1/admin/system-configs/**", "/api/v1/admin/audit-logs/**", "/api/v1/admin/operational-alerts/**", "/api/v1/admin/dashboard/**")
                .build();
    }

    @Bean
    public GroupedOpenApi chatbotApis() {
        return GroupedOpenApi.builder()
                .group("12-chatbot").displayName("12 · Trợ lý chatbot")
                .pathsToMatch("/api/v1/chatbot/**")
                .build();
    }
}
