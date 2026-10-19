package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.TreatmentNotificationService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/spa/treatment-notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Thông báo lộ trình", description = "Thông báo của chủ hồ sơ, không chứa ghi chú điều trị nội bộ")
public class TreatmentNotificationController {
    private final TreatmentNotificationService notifications;

    @GetMapping @Operation(summary = "Xem tối đa 100 thông báo lộ trình gần nhất của tôi")
    public ApiResponse<List<TreatmentNotificationService.View>> mine() { return ApiResponse.success(notifications.mine()); }

    @PutMapping("/{id}/read") @Operation(summary = "Đánh dấu đã đọc thông báo của tôi")
    public ApiResponse<TreatmentNotificationService.View> read(@PathVariable Long id) { return ApiResponse.success(notifications.read(id)); }
}
