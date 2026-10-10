package com.core.beautyshop.modules.identity.application.dto.request;

import com.core.beautyshop.modules.identity.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dữ liệu yêu cầu đăng ký tài khoản khách hàng mới")
public class RegisterRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
    @jakarta.validation.constraints.Pattern(regexp = "^[^@\\s]+$", message = "Tên đăng nhập không được chứa @ hoặc khoảng trắng")
    @Schema(description = "Tên đăng nhập duy nhất, 3–50 ký tự, không chứa @ hoặc khoảng trắng", minLength = 3, maxLength = 50, example = "nguyenan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    @Schema(description = "Email duy nhất dùng đăng nhập/nhận thông báo; không có bước kích hoạt email", example = "nguyenan@gmail.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 100, message = "Mật khẩu phải có ít nhất 6 ký tự")
    @Schema(description = "Mật khẩu 6–100 ký tự", minLength = 6, maxLength = 100, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, example = "SecurePassword@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @lombok.ToString.Exclude
    private String password;

    @NotBlank(message = "Họ và tên không được để trống")
    @Schema(description = "Họ và tên đầy đủ của khách hàng", example = "Nguyễn Văn An", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 100)
    private String fullName;

    @Schema(description = "Số điện thoại liên hệ", example = "0912345678")
    @Size(max = 20)
    private String phone;

    @Schema(description = "Giới tính (MALE, FEMALE, OTHER)", example = "FEMALE")
    private Gender gender;

    @Schema(description = "Ngày tháng năm sinh (YYYY-MM-DD)", example = "1998-05-20")
    private LocalDate dateOfBirth;

    @Schema(description = "Tiểu sử / Giới thiệu bản thân", example = "Khách hàng yêu thích các sản phẩm chăm sóc da hữu cơ.")
    @Size(max = 500)
    private String bio;

    @Schema(description = "URL ảnh đại diện avatar", example = "https://cdn.beautyshop.com/avatars/user-1.png")
    @Size(max = 500)
    private String avatarUrl;
}
