package com.core.beautyshop.modules.identity.application.dto.response;

import com.core.beautyshop.modules.identity.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin hồ sơ chi tiết của người dùng hiện tại")
public class UserProfileResponse {

    @Schema(description = "ID người dùng", example = "1")
    private Long id;

    @Schema(description = "Tên đăng nhập", example = "nguyenan")
    private String username;

    @Schema(description = "Địa chỉ email", example = "nguyenan@gmail.com")
    private String email;

    @Schema(description = "Họ và tên đầy đủ", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "Số điện thoại", example = "0912345678")
    private String phone;

    @Schema(description = "URL ảnh đại diện", example = "https://cdn.beautyshop.com/avatars/user-1.png")
    private String avatarUrl;

    @Schema(description = "Tiểu sử cá nhân", example = "Khách hàng thân thiết của Beauty Shop.")
    private String bio;

    @Schema(description = "Giới tính", example = "MALE")
    private Gender gender;

    @Schema(description = "Ngày sinh", example = "1998-05-20")
    private LocalDate dateOfBirth;

    @Schema(description = "Danh sách vai trò", example = "[\"ROLE_CUSTOMER\"]")
    private List<String> roles;

    @Schema(description = "Hạng thành viên (MEMBER, SILVER, GOLD, PLATINUM)", example = "GOLD")
    private com.core.beautyshop.modules.identity.domain.enums.MembershipTier membershipTier;

    @Schema(description = "Điểm tích lũy thành viên", example = "450")
    private Integer loyaltyPoints;

    private com.core.beautyshop.modules.identity.domain.enums.AccountStatus status;
}
