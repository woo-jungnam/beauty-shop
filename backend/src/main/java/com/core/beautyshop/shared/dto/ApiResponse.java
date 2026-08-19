package com.core.beautyshop.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;
    private Object errors;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

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

    public static <T> ApiResponse<T> error(
            int status,
            String message,
            Object errors) {

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
