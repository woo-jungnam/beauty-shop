package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.SpaFinancialService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.math.BigDecimal;
@RestController @RequiredArgsConstructor
public class SpaFinancialController {
 private final SpaFinancialService finance;
 @GetMapping("/api/v1/spa/financial-policy") public ApiResponse<SpaFinancialService.Policy> policy() { return ApiResponse.success(finance.policy()); }
 @PreAuthorize("hasRole('ADMIN')") @PutMapping("/api/v1/admin/spa/financial-policy") public ApiResponse<SpaFinancialService.Policy> policy(@RequestBody SpaFinancialService.Policy p) { return ApiResponse.success(finance.savePolicy(p)); }
 @PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/v1/admin/spa/financial-report") public ApiResponse<Map<String,BigDecimal>> report() { return ApiResponse.success(finance.report()); }
 @PreAuthorize("isAuthenticated()") @GetMapping("/api/v1/spa/appointments/{id}/finance") public ApiResponse<SpaFinancialService.Finance> view(@PathVariable Long id) { return ApiResponse.success(finance.view(id)); }
 @PreAuthorize("isAuthenticated()") @PostMapping("/api/v1/spa/appointments/{id}/deposit-payment") public ApiResponse<SpaFinancialService.Finance> payment(@PathVariable Long id) { return ApiResponse.success(finance.depositPayment(id)); }
 @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping("/api/v1/spa/appointments/{id}/deposit-receipts") public ApiResponse<SpaFinancialService.Finance> deposit(@PathVariable Long id,@RequestBody SpaFinancialService.Receipt c,@RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(finance.receipt(id,c,key,false)); }
 @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping("/api/v1/spa/appointments/{id}/fee-receipts") public ApiResponse<SpaFinancialService.Finance> fee(@PathVariable Long id,@RequestBody SpaFinancialService.Receipt c,@RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(finance.receipt(id,c,key,true)); }
 @PreAuthorize("hasRole('ADMIN')") @PostMapping("/api/v1/spa/appointments/{id}/deposit-refund-confirmations") public ApiResponse<SpaFinancialService.Finance> refund(@PathVariable Long id,@RequestBody SpaFinancialService.Receipt c,@RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(finance.refund(id,c,key)); }
}
