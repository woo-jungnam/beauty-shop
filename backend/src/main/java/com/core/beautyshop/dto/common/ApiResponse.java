package com.core.beautyshop.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    public static <T> ApiResponse<T> success(T data, String path) {
        return ApiResponse.<T>builder()
                .status(200)
                .message("Success")
                .data(data)
                .path(path)
                .build();
    }

    public static <T> ApiResponse<T> created(T data, String message, String path) {
        return ApiResponse.<T>builder()
                .status(201)
                .message(message)
                .data(data)
                .path(path)
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message, Object errors, String path) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .errors(errors)
                .path(path)
                .build();
    }
}
