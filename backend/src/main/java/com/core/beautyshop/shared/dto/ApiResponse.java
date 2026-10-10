package com.core.beautyshop.shared.dto;

import com.core.beautyshop.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Đối tượng đóng gói phản hồi chuẩn của hệ thống")
public class ApiResponse<T> {

    @Schema(description = "Mã trạng thái HTTP", example = "200")
    private int status;

    @Schema(description = "Mã lỗi", example = "INV_001")
    private String errorCode;

    @Schema(description = "mô tả kết quả", example = "Thao tác thành công")
    private String message;

    @Schema(description = "Dữ liệu trả về")
    private T data;

    @Schema(description = "Chi tiết lỗi")
    private Object errors;

    @Builder.Default
    @Schema(implementation = String.class, description = "LocalDateTime máy chủ, không có offset/Z; không dùng OffsetDateTime để parse trường này", example = "2026-10-02T17:00:00")
    private LocalDateTime timestamp = LocalDateTime.now();

    @Schema(description = "Đường dẫn API được yêu cầu", example = "/api/v1/products")
    private String path;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message("Success")
                .data(data)
                .path(getCurrentRequestPath())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .path(getCurrentRequestPath())
                .build();
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return ApiResponse.<T>builder()
                .status(201)
                .message(message)
                .data(data)
                .path(getCurrentRequestPath())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .status(errorCode.getHttpStatus().value())
                .errorCode(errorCode.getCode())
                .message(errorCode.getDefaultMessage())
                .path(getCurrentRequestPath())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage) {
        return ApiResponse.<T>builder()
                .status(errorCode.getHttpStatus().value())
                .errorCode(errorCode.getCode())
                .message(customMessage != null ? customMessage : errorCode.getDefaultMessage())
                .path(getCurrentRequestPath())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage, Object errors) {
        return ApiResponse.<T>builder()
                .status(errorCode.getHttpStatus().value())
                .errorCode(errorCode.getCode())
                .message(customMessage != null ? customMessage : errorCode.getDefaultMessage())
                .errors(errors)
                .path(getCurrentRequestPath())
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String errorCode, String message, Object errors) {
        return ApiResponse.<T>builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .errors(errors)
                .path(getCurrentRequestPath())
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message, Object errors) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .errors(errors)
                .path(getCurrentRequestPath())
                .build();
    }

    private static String getCurrentRequestPath() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return null;
        }

        return attributes.getRequest().getRequestURI();
    }
}
