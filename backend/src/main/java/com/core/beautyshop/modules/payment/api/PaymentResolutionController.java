package com.core.beautyshop.modules.payment.api;
import com.core.beautyshop.modules.payment.application.service.PaymentResolutionService;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/payments") @PreAuthorize("hasRole('ADMIN')")
public class PaymentResolutionController {
 private final PaymentResolutionService service;
 @GetMapping("/exceptions")public ApiResponse<?> exceptions(@RequestParam(required=false)com.core.beautyshop.modules.payment.domain.enums.TransactionStatus status,@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable p){return ApiResponse.success(PageResponse.of(service.exceptions(status,p)));}
 @GetMapping("/resolutions")public ApiResponse<?> list(@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable p){return ApiResponse.success(PageResponse.of(service.list(p)));}
 @PostMapping("/{id}/resolution")public ApiResponse<?> resolve(@PathVariable Long id,@RequestBody PaymentResolutionService.Command c){return ApiResponse.success(service.resolve(id,c));}
}
