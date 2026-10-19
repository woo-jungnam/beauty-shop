package com.core.beautyshop.modules.dashboard.api;
import com.core.beautyshop.modules.dashboard.application.ProfitabilityService;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
import java.time.Instant;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/dashboard") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class ProfitabilityController {
 private final ProfitabilityService service;
 @GetMapping("/profitability")public ApiResponse<?> report(@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to){return ApiResponse.success(service.report(from,to));}
 @GetMapping("/expenses")public ApiResponse<?> list(@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable p){return ApiResponse.success(PageResponse.of(service.list(p)));}
 @PostMapping("/expenses") @PreAuthorize("hasRole('ADMIN')")public ApiResponse<?> expense(@RequestBody ProfitabilityService.Expense c){return ApiResponse.success(service.record(c));}
}
