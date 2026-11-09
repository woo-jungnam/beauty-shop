package com.core.beautyshop.modules.notification.api;
import com.core.beautyshop.modules.notification.application.service.CustomerNotificationService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/users/notifications") @PreAuthorize("isAuthenticated()")
@Tag(name = "Thông báo người dùng", description = "Xem danh sách thông báo và đánh dấu đã đọc thông báo tài khoản")
public class CustomerNotificationController {
    private final CustomerNotificationService notifications;

    @Operation(summary = "Danh sách thông báo của tôi", description = "Khách hàng tra cứu và phân trang danh sách thông báo về đơn hàng, lịch hẹn, khuyến mãi")
    @GetMapping public ApiResponse<PageResponse<CustomerNotificationService.View>> mine(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(notifications.mine(page,size));}

    @Operation(summary = "Đánh dấu thông báo đã đọc", description = "Cập nhật trạng thái thông báo thành đã xem")
    @PutMapping("/{id}/read") public ApiResponse<CustomerNotificationService.View> read(@PathVariable Long id){return ApiResponse.success(notifications.read(id));}
}
