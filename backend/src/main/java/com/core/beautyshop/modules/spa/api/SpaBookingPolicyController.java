package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.SpaBookingPolicyService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/spa/booking-policy") @RequiredArgsConstructor
public class SpaBookingPolicyController {
    private final SpaBookingPolicyService policies;
    @GetMapping
    public ApiResponse<SpaBookingPolicyService.Policy> current() { return ApiResponse.success(policies.current()); }
}
