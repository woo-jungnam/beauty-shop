package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;
import com.core.beautyshop.modules.spa.application.service.AdminSpaTicketService;
import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/spa/tickets")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSpaTicketController {
    private final AdminSpaTicketService service;
    @GetMapping public ApiResponse<PageResponse<UserServiceTicketResponse>> find(@RequestParam(required = false) Long userId, @RequestParam(required = false) TicketStatus status, Pageable pageable) { return ApiResponse.success(PageResponse.of(service.find(userId, status, pageable))); }
    @PutMapping("/{id}/extend") public ApiResponse<UserServiceTicketResponse> extend(@PathVariable Long id, @RequestBody ExtendRequest request) { return ApiResponse.success(service.extend(id, request.days())); }
    @PutMapping("/{id}/compensate") public ApiResponse<UserServiceTicketResponse> compensate(@PathVariable Long id, @RequestBody CompensationRequest request) { return ApiResponse.success(service.compensate(id, request.serviceId(), request.sessions())); }
    public record ExtendRequest(int days) { }
    public record CompensationRequest(Long serviceId, int sessions) { }
}
