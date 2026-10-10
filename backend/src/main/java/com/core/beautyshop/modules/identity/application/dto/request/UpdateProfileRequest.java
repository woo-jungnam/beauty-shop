package com.core.beautyshop.modules.identity.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.core.beautyshop.modules.identity.domain.enums.Gender;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chỉ cập nhật field có giá trị khác null; không thay thông tin đăng nhập hoặc vai trò")
public class UpdateProfileRequest {

    @Size(max = 100)
    @Schema(description = "Họ tên", maxLength = 100)
    private String fullName;

    @Size(max = 20)
    @Schema(description = "Số điện thoại", maxLength = 20)
    private String phone;

    @Size(max = 500)
    @Schema(description = "URL ảnh đại diện", maxLength = 500)
    private String avatarUrl;

    @Size(max = 500)
    @Schema(description = "Giới thiệu", maxLength = 500)
    private String bio;

    @Schema(description = "Giới tính", example = "FEMALE")
    private Gender gender;

    @Schema(description = "Ngày sinh", type = "string", format = "date", example = "1998-05-20")
    private LocalDate dateOfBirth;
}
