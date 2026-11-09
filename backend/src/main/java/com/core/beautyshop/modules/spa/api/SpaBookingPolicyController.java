package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.SpaBookingPolicyService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/spa/booking-policy") @RequiredArgsConstructor
@Tag(name = "Chính sách đặt lịch Spa", description = "Chính sách đặt lịch, hủy lịch và quy định thời gian hẹn Spa")
public class SpaBookingPolicyController {
    private final SpaBookingPolicyService policies;

    @Operation(summary = "Xem chính sách đặt lịch Spa hiện tại", description = "Cung cấp quy định về thời gian đặt trước, hủy lịch, hoàn tiền và số lần đổi lịch tối đa")
    @GetMapping
    public ApiResponse<SpaBookingPolicyService.Policy> current() { return ApiResponse.success(policies.current()); }
}
