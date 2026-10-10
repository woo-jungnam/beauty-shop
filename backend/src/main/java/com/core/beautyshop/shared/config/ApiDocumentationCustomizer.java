package com.core.beautyshop.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.*;

/** Documentation follows method authorization and the public routes in SecurityConfig. */
@Component
public class ApiDocumentationCustomizer implements GlobalOperationCustomizer, GlobalOpenApiCustomizer {
    private static final List<String> MODULE_ORDER = List.of("identity", "catalog", "cart", "order", "payment", "spa", "crm", "inventory", "procurement", "promotion", "review", "dashboard", "system", "chatbot");
    private static final List<String> PUBLIC_GET_PREFIXES = List.of("/api/v1/products", "/api/v1/categories", "/api/v1/brands", "/api/v1/attributes", "/api/v1/tags", "/api/v1/spa/services", "/api/v1/reviews");
    private static final Set<String> PUBLIC_ROUTES = Set.of("/api/v1/test/public", "/api/v1/chatbot/chat", "/api/v1/chatbot/chat/stream", "/api/v1/chatbot/health", "/api/v1/chatbot/openapi.json");

    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        String path = path(handler.getBeanType(), handler.getMethod());
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequestMapping.class);
        boolean get = mapping != null && Arrays.asList(mapping.method()).contains(RequestMethod.GET);
        PreAuthorize authorization = authorization(handler.getMethod(), handler.getBeanType());
        String mode;
        if (path.equals("/api/v1/payment/sepay-webhook")) {
            mode = "sepay-key";
            operation.setSecurity(List.of(new SecurityRequirement().addList("sepayApiKey")));
        } else if (authorization != null) {
            mode = "bearer";
            operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
            operation.addExtension("x-method-authorization", authorization.value());
        } else if (path.startsWith("/api/v1/cart") || path.equals("/api/v1/orders/checkout") || (get && path.matches("/api/v1/orders/[^/]+"))) {
            mode = "optional-bearer";
            operation.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList("bearerAuth")));
        } else if (path.startsWith("/api/v1/auth/") || PUBLIC_ROUTES.contains(path) || (get && PUBLIC_GET_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/")))) {
            mode = "public";
            operation.setSecurity(List.of());
        } else {
            mode = "bearer";
            operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
        }
        operation.addExtension("x-access-mode", mode);
        operation.addExtension("x-module", module(handler.getBeanType()));
        if (get && path.equals("/api/v1/products/search") && operation.getParameters() != null) {
            // @NotNull rejects explicitly blank pagination values; field defaults allow omitted parameters.
            // Springdoc otherwise interprets those validation constraints as required query parameters.
            operation.getParameters().stream().filter(parameter -> Set.of("page", "size").contains(parameter.getName()))
                    .forEach(parameter -> parameter.setRequired(false));
        }
        if (operation.getResponses() != null) {
            addError(operation, "400", "JSON, tham số hoặc dữ liệu không hợp lệ; quy tắc nghiệp vụ bị từ chối.");
            addError(operation, "401", mode.equals("sepay-key") ? "Thiếu hoặc sai API key SePay." : "Thiếu, sai hoặc hết hạn thông tin xác thực khi API yêu cầu.");
            if (!mode.equals("public") && !mode.equals("sepay-key")) addError(operation, "403", "Không đủ vai trò, quyền sở hữu hoặc phạm vi được phân công.");
            addError(operation, "404", "Không tìm thấy tài nguyên hoặc đường dẫn.");
            addError(operation, "409", "Dữ liệu trùng, xung đột đồng thời hoặc trạng thái nghiệp vụ không cho phép.");
            addError(operation, "415", "Content-Type không được hỗ trợ.");
            addError(operation, "500", "Lỗi nội bộ; phản hồi không chứa chi tiết kỹ thuật nhạy cảm.");
        }
        return operation;
    }

    private void addError(Operation operation, String status, String description) {
        ApiResponse response = operation.getResponses().get(status);
        if (response == null) response = new ApiResponse().description(description);
        else if (response.getDescription() == null || response.getDescription().equals("Bad Request") || response.getDescription().equals("Internal Server Error")) response.setDescription(description);
        response.setContent(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
        operation.getResponses().addApiResponse(status, response);
    }

    @Override
    public void customise(OpenAPI api) {
        if (api.getComponents() != null) api.getComponents().addSchemas("ApiErrorResponse", new ObjectSchema()
                .description("Phản hồi lỗi chung. Các trường null được bỏ qua; errorCode có thể vắng ở lỗi từ filter/webhook.")
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("errorCode", new StringSchema().example("SYS_002"))
                .addProperty("message", new StringSchema().example("Thiếu header bắt buộc: Idempotency-Key"))
                .addProperty("errors", new Schema<>().description("Chi tiết kiểm thực nếu có; thường là mảng field/message/rejectedValue, không chứa giá trị bị từ chối."))
                .addProperty("timestamp", new StringSchema().description("LocalDateTime của máy chủ, không có offset/Z; không phải RFC3339 Instant.").example("2026-10-02T17:00:00"))
                .addProperty("path", new StringSchema().example("/api/v1/appointments/1/invoice"))
                .required(List.of("status", "message", "timestamp")));
        if (api.getPaths() == null) return;
        Map<String, Integer> ranks = new HashMap<>();
        api.getPaths().values().forEach(item -> item.readOperations().forEach(operation -> {
            int rank = MODULE_ORDER.indexOf(String.valueOf(operation.getExtensions().get("x-module")));
            if (operation.getTags() != null) operation.getTags().forEach(tag -> ranks.merge(tag, rank < 0 ? 99 : rank, Math::min));
        }));
        Map<String, Tag> registered = new HashMap<>();
        if (api.getTags() != null) api.getTags().forEach(tag -> registered.put(tag.getName(), tag));
        api.setTags(ranks.keySet().stream().sorted(Comparator.comparingInt((String name) -> ranks.get(name)).thenComparing(name -> name))
                .map(name -> registered.getOrDefault(name, new Tag().name(name))).toList());
        var ordered = new io.swagger.v3.oas.models.Paths();
        api.getPaths().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            // Springdoc can reuse operations for aliases. Copy before assigning route-specific IDs.
            var item = new io.swagger.v3.oas.models.PathItem();
            BeanUtils.copyProperties(entry.getValue(), item);
            entry.getValue().readOperationsMap().forEach((method, operation) -> {
                Operation distinct = new Operation();
                BeanUtils.copyProperties(operation, distinct);
                String route = entry.getKey().replaceAll("\\{([^}]+)\\}", "by_$1")
                        .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
                distinct.setOperationId(method.name().toLowerCase(Locale.ROOT) + "_" + route);
                item.operation(method, distinct);
            });
            ordered.addPathItem(entry.getKey(), item);
        });
        api.setPaths(ordered);
    }

    public static boolean isManagementMethod(Method method) {
        PreAuthorize authorization = authorization(method, method.getDeclaringClass());
        String path = path(method.getDeclaringClass(), method);
        boolean staffServiceAction = path.matches("/api/v1/spa/appointments/[^/]+/(care-summary|preparation/items/[^/]+/(acknowledge-warnings|override)|follow-up-instructions/items/[^/]+)");
        return path.startsWith("/api/v1/admin/") || staffServiceAction
                || (authorization != null && authorization.value().contains("ADMIN"));
    }

    private static PreAuthorize authorization(Method method, Class<?> type) {
        PreAuthorize methodRule = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        return methodRule == null ? AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class) : methodRule;
    }
    private static String path(Class<?> type, Method method) {
        return firstPath(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class)) + firstPath(AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class));
    }
    private static String firstPath(RequestMapping mapping) {
        return mapping == null || mapping.path().length == 0 ? "" : mapping.path()[0];
    }
    private static String module(Class<?> type) {
        String[] segments = type.getPackageName().split("\\.");
        for (int index = 0; index + 1 < segments.length; index++) if (segments[index].equals("modules")) return segments[index + 1];
        return "system";
    }
}
