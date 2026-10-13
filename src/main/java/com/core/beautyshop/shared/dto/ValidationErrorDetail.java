package com.core.beautyshop.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chi tiết lỗi kiểm thực trường dữ liệu")
public class ValidationErrorDetail {

    @Schema(description = "Tên trường gặp lỗi xác thực", example = "customerPhone")
    private String field;

    @Schema(description = "Thông điệp lỗi chi tiết", example = "Định dạng số điện thoại không hợp lệ")
    private String message;

    @Schema(description = "Máy chủ trả null để tránh tiết lộ mật khẩu hoặc dữ liệu cá nhân trong lỗi kiểm thực")
    private Object rejectedValue;
}
