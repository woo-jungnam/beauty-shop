package com.core.beautyshop.modules.promotion.api;

import com.core.beautyshop.modules.promotion.application.VoucherService;
import com.core.beautyshop.shared.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminVoucherController {
    private final VoucherService service;

    @GetMapping
    public ApiResponse<PageResponse<VoucherService.VoucherView>> list(Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.findAll(pageable)));
    }
    @GetMapping("/{id}")
    public ApiResponse<VoucherService.VoucherView> get(@PathVariable Long id) { return ApiResponse.success(service.get(id)); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VoucherService.VoucherView> create(@Valid @RequestBody VoucherService.VoucherCommand command) {
        return ApiResponse.created(service.create(command), "Voucher created");
    }
    @PutMapping("/{id}")
    public ApiResponse<VoucherService.VoucherView> update(@PathVariable Long id, @Valid @RequestBody VoucherService.VoucherCommand command) {
        return ApiResponse.success(service.update(id, command));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.success(null); }
}
