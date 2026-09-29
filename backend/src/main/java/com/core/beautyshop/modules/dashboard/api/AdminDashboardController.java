package com.core.beautyshop.modules.dashboard.api;

import com.core.beautyshop.modules.dashboard.application.AdminDashboardService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService service;
    @GetMapping public ApiResponse<AdminDashboardService.Overview> overview(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) { return ApiResponse.success(service.overview(from, to)); }
    @GetMapping("/revenue") public ApiResponse<List<AdminDashboardService.RevenuePoint>> revenue(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "day") String period) { return ApiResponse.success(service.revenue(from, to, period)); }
    @GetMapping("/top-products") public ApiResponse<List<AdminDashboardService.TopProduct>> topProducts(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) { return ApiResponse.success(service.topProducts(from, to)); }
    @GetMapping("/spa-occupancy") public ApiResponse<AdminDashboardService.SpaOccupancy> spaOccupancy(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) { return ApiResponse.success(service.spaOccupancy(from, to)); }
}
