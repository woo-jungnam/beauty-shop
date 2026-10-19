package com.core.beautyshop.modules.dashboard.api;
import com.core.beautyshop.modules.dashboard.application.SpaPerformanceReportService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.Instant;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/dashboard/spa-performance") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class SpaPerformanceReportController {
 private final SpaPerformanceReportService service;
 @GetMapping public ApiResponse<?> report(@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to){return ApiResponse.success(service.report(from,to));}
}
