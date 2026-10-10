package com.core.beautyshop.modules.promotion.api;

import com.core.beautyshop.modules.promotion.application.VoucherService;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Khuyến mãi & Mã giảm giá Voucher (Admin)", description = "API quản trị chính sách khuyến mãi, tạo mã coupon giảm giá và hạn ngạch sử dụng")
@RestController
@RequestMapping("/api/v1/admin/vouchers")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminVoucherController {
    private final VoucherService service;

    @Operation(summary = "Lấy danh sách mã giảm giá Voucher (phân trang)")
    @GetMapping
    public ApiResponse<PageResponse<VoucherService.VoucherView>> list(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.findAll(pageable)));
    }

    @Operation(summary = "Xem chi tiết Voucher theo ID")
    @GetMapping("/{id}")
    public ApiResponse<VoucherService.VoucherView> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @Operation(summary = "Tạo voucher", description = "ADMIN; code trim/viết hoa và duy nhất. PERCENTAGE phải >0 và ≤100; FIXED_AMOUNT >0. startsAt/endsAt bắt buộc và endsAt>startsAt. usageLimit/perUserLimit nếu có phải dương; mặc định perUserLimit=1, active=true, minOrderAmount=0. Không có Idempotency-Key. Voucher được áp qua checkout của người đăng nhập.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Voucher mới đã tạo")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VoucherService.VoucherView> create(@Valid @RequestBody VoucherService.VoucherCommand command) {
        return ApiResponse.created(service.create(command), "Voucher created");
    }

    @Operation(summary = "Thay cấu hình voucher", description = "Kiểm như tạo; cần gửi lại code/name/type/value/times. usedCount được giữ; usageLimit không được dưới usedCount. Trường tùy chọn thiếu được áp mặc định/null như khi tạo, không phải patch.")
    @PutMapping("/{id}")
    public ApiResponse<VoucherService.VoucherView> update(@PathVariable Long id, @Valid @RequestBody VoucherService.VoucherCommand command) {
        return ApiResponse.success(service.update(id, command));
    }

    @Operation(summary = "Xóa mềm voucher", description = "Đặt isDeleted=true và isActive=false; giữ lịch sử usage, không hoàn lại hạn ngạch của các đơn đã dùng.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
