package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.ServiceBookingOrderQuery;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Đơn đặt dịch vụ Spa", description = "Tra cứu danh sách đơn đặt lịch hẹn dịch vụ của khách hàng và quản trị viên")
public class ServiceBookingOrderController {
    private final ServiceBookingOrderQuery bookings;

    @Operation(summary = "Danh sách đơn đặt lịch của tôi", description = "Khách hàng tra cứu lịch sử các đơn đặt lịch dịch vụ Spa của chính mình")
    @GetMapping("/api/v1/appointments/my-service-orders")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<ServiceBookingOrderQuery.Booking>> mine(@PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.success(PageResponse.of(bookings.list(SecurityUtils.getCurrentUserId(), pageable)));
    }

    @Operation(summary = "Quản lý toàn bộ đơn đặt lịch dịch vụ (Admin)", description = "ADMIN tra cứu và phân trang danh sách tất cả các đơn đặt lịch Spa trên hệ thống")
    @GetMapping("/api/v1/appointments/admin/service-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ServiceBookingOrderQuery.Booking>> admin(@PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.success(PageResponse.of(bookings.list(null, pageable)));
    }
}
