package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.ServiceBookingOrderQuery;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ServiceBookingOrderController {
    private final ServiceBookingOrderQuery bookings;

    @GetMapping("/api/v1/appointments/my-service-orders")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<ServiceBookingOrderQuery.Booking>> mine(@PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.success(PageResponse.of(bookings.list(SecurityUtils.getCurrentUserId(), pageable)));
    }

    @GetMapping("/api/v1/appointments/admin/service-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ServiceBookingOrderQuery.Booking>> admin(@PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.success(PageResponse.of(bookings.list(null, pageable)));
    }
}
