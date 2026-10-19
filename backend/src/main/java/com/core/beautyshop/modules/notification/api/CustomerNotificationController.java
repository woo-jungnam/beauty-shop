package com.core.beautyshop.modules.notification.api;
import com.core.beautyshop.modules.notification.application.service.CustomerNotificationService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/users/notifications") @PreAuthorize("isAuthenticated()")
public class CustomerNotificationController {
    private final CustomerNotificationService notifications;
    @GetMapping public ApiResponse<PageResponse<CustomerNotificationService.View>> mine(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(notifications.mine(page,size));}
    @PutMapping("/{id}/read") public ApiResponse<CustomerNotificationService.View> read(@PathVariable Long id){return ApiResponse.success(notifications.read(id));}
}
