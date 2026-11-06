package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.SpaPackageRequestService;
import com.core.beautyshop.modules.spa.domain.SpaPackageRequest;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class SpaPackageRequestController {
    private final SpaPackageRequestService service;
    @GetMapping("/admin/spa/package-requests") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SpaPackageRequest>> pending() { return ApiResponse.success(service.pending()); }
    @GetMapping({"/spa/tickets/{ticketId}/requests","/admin/spa/tickets/{ticketId}/requests"})
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SpaPackageRequest>> list(@PathVariable Long ticketId) { return ApiResponse.success(service.list(ticketId)); }
    @PostMapping("/spa/tickets/{ticketId}/requests") @PreAuthorize("isAuthenticated()")
    public ApiResponse<SpaPackageRequest> create(@PathVariable Long ticketId,@RequestBody SpaPackageRequestService.Command command) { return ApiResponse.success(service.create(ticketId,command)); }
    @PostMapping("/admin/spa/tickets/{ticketId}/requests/{requestId}/review") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SpaPackageRequest> review(@PathVariable Long ticketId,@PathVariable Long requestId,@RequestBody SpaPackageRequestService.Decision decision) { return ApiResponse.success(service.review(ticketId,requestId,decision)); }
}
