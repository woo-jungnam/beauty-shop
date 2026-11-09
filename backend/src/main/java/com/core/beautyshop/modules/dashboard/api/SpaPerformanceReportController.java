package com.core.beautyshop.modules.dashboard.api;
import com.core.beautyshop.modules.dashboard.application.SpaPerformanceReportService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.Instant;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/dashboard/spa-performance") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@Tag(name = "Báo cáo hiệu suất Spa (Admin)", description = "Báo cáo hiệu suất vận hành, tỷ lệ hoàn tất và công suất phục vụ của Spa")
public class SpaPerformanceReportController {
    private final SpaPerformanceReportService service;

    @Operation(summary = "Báo cáo hiệu suất vận hành Spa (Admin)", description = "Thống kê số lượt phục vụ, tỷ lệ hủy/hoàn thành, hiệu suất chuyên viên và phòng máy")
    @GetMapping public ApiResponse<?> report(@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to){return ApiResponse.success(service.report(from,to));}
}
