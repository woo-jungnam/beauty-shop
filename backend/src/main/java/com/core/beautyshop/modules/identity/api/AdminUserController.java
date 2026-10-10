package com.core.beautyshop.modules.identity.api;

import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import com.core.beautyshop.modules.identity.application.service.*;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quản lý người dùng & Bảo mật (Admin)", description = "API quản trị tài khoản, khóa/mở khóa, phân vai trò và quản lý phiên làm việc")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;
    private final AuthService authService;
    private final com.core.beautyshop.modules.identity.domain.RefreshTokenSessionRepository sessionRepository;

    @Operation(summary = "Lấy danh sách người dùng (ADMIN)", description = "Chỉ tài khoản chưa soft-delete. Có bộ lọc keyword/status/role và page/size/sort; page bắt đầu từ 0. Không giới hạn danh sách vào khách hàng.")
    @GetMapping
    public ApiResponse<PageResponse<UserProfileResponse>> list(@Parameter(description = "Tìm chứa chuỗi trong username, email, fullName hoặc phone; tên/email không phân biệt hoa thường") @RequestParam(required = false) String keyword,
            @Parameter(description = "Trạng thái tài khoản") @RequestParam(required = false) AccountStatus status,
            @Parameter(description = "Mã role, có hoặc không có tiền tố ROLE_; phân biệt hoa thường", example = "SPA_THERAPIST") @RequestParam(required = false) String role,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(userService.getAllUsers(keyword, status, role, pageable)));
    }

    @Operation(summary = "Xem thông tin chi tiết người dùng theo ID")
    @GetMapping("/{id}")
    public ApiResponse<UserProfileResponse> get(@PathVariable Long id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    @Operation(summary = "Cập nhật trạng thái tài khoản (ACTIVE, BLOCKED, SUSPENDED)", description = "ADMIN. Ghi lịch sử và thu hồi phiên khi đổi trạng thái; không được khóa quản trị viên ACTIVE cuối cùng. reason tối đa 500 ký tự.")
    @PutMapping("/{id}/status")
    public ApiResponse<UserProfileResponse> status(@PathVariable Long id, @RequestBody StatusRequest request) {
        UserProfileResponse result = userService.updateStatus(id, request.status(), request.reason());
        return ApiResponse.success(result);
    }

    @Operation(summary = "Buộc đăng xuất toàn bộ phiên của người dùng")
    @PostMapping("/{id}/force-logout")
    public ApiResponse<Void> forceLogout(@PathVariable Long id) {
        authService.forceLogoutUser(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Xem lịch sử phiên đăng nhập của người dùng", description = "ADMIN. Bao gồm các bản refresh session đã hết hạn/thu hồi/luân chuyển, theo createdAt giảm dần; các dòng cùng familyId thuộc cùng một họ phiên. Không trả token hoặc token hash.")
    @GetMapping("/{id}/sessions")
    public ApiResponse<java.util.List<SessionView>> sessions(@PathVariable Long id) {
        userService.getUserById(id);
        return ApiResponse.success(sessionRepository.findByUserIdOrderByCreatedAtDesc(id).stream()
                .map(session -> new SessionView(session.getId(), session.getFamilyId(), session.getCreatedAt(), session.getExpiresAt(), session.getRevokedAt())).toList());
    }

    @GetMapping("/{id}/status-history")
    @Operation(summary = "Xem lịch sử thay đổi trạng thái tài khoản")
    public ApiResponse<PageResponse<com.core.beautyshop.modules.identity.domain.UserStatusHistory>> statusHistory(@PathVariable Long id, @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(userService.getStatusHistory(id, pageable)));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @Operation(summary = "Thu hồi một họ phiên của đúng người dùng", description = "ADMIN. sessionId phải thuộc user id trong đường dẫn; thu hồi toàn bộ họ familyId của dòng session được chọn, gồm access token trong họ. Các họ khác tiếp tục hoạt động.")
    public ApiResponse<Void> revokeSession(@PathVariable Long id, @PathVariable Long sessionId) {
        authService.revokeSession(id, sessionId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Thay thế toàn bộ vai trò của người dùng", description = "ADMIN. roleIds phải không rỗng và tham chiếu role còn tồn tại/chưa xóa. Thay vai trò thu hồi mọi phiên của tài khoản; không được gỡ ADMIN của quản trị viên ACTIVE cuối cùng.")
    @PutMapping("/{id}/roles")
    public ApiResponse<UserProfileResponse> roles(@PathVariable Long id, @RequestBody RolesRequest request) {
        UserProfileResponse result = userService.updateRoles(id, request.roleIds());
        return ApiResponse.success(result);
    }

    @Operation(summary = "Đặt lại mật khẩu tạm thời cho người dùng", description = "Chỉ ADMIN. Tự động sinh mật khẩu tạm thời ngẫu nhiên an toàn, cập nhật tài khoản và thu hồi toàn bộ phiên đăng nhập hiện tại.")
    @PostMapping("/{id}/reset-password")
    public ApiResponse<com.core.beautyshop.modules.identity.application.dto.response.ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return ApiResponse.success(userService.resetPassword(id));
    }

    @Schema(name = "AdminUserStatusRequest", description = "Trạng thái tài khoản và lý do thay đổi")
    public record StatusRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SUSPENDED") AccountStatus status,
                                @Schema(description = "Lý do ghi vào lịch sử", maxLength = 500) String reason) { }
    @Schema(name = "AdminUserRolesRequest", description = "Danh sách thay thế toàn bộ role hiện tại")
    public record RolesRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ít nhất một ID role tồn tại", example = "[2, 11]") java.util.List<Long> roleIds) { }
    @Schema(name = "AdminUserSessionView", description = "Bản ghi refresh session; familyId liên kết các lần luân chuyển")
    public record SessionView(@Schema(description = "ID bản ghi session") Long id,
                              @Schema(description = "ID họ phiên") String familyId,
                              @Schema(description = "Thời điểm tạo, UTC") java.time.Instant createdAt,
                              @Schema(description = "Thời điểm hết hạn, UTC") java.time.Instant expiresAt,
                              @Schema(description = "Thời điểm thu hồi, null nếu chưa thu hồi") java.time.Instant revokedAt) { }
}
