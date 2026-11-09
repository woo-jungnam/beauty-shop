package com.core.beautyshop.modules.payment.api;
import com.core.beautyshop.modules.payment.application.service.PaymentResolutionService;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/payments") @PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Xử lý sai lệch thanh toán (Admin)", description = "ADMIN tra cứu giao dịch ngoại lệ, sai lệch số tiền và ghi nhận quyết định xử lý")
public class PaymentResolutionController {
    private final PaymentResolutionService service;

    @Operation(summary = "Danh sách giao dịch thanh toán bất thường (Admin)", description = "Tra cứu các giao dịch sai số tiền, sai mã đơn hoặc không khớp tài khoản")
    @GetMapping("/exceptions")public ApiResponse<?> exceptions(@RequestParam(required=false)com.core.beautyshop.modules.payment.domain.enums.TransactionStatus status,@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable p){return ApiResponse.success(PageResponse.of(service.exceptions(status,p)));}

    @Operation(summary = "Lịch sử xử lý sai lệch thanh toán (Admin)", description = "Xem danh sách các quyết định xử lý giao dịch ngoại lệ đã thực hiện")
    @GetMapping("/resolutions")public ApiResponse<?> list(@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable p){return ApiResponse.success(PageResponse.of(service.list(p)));}

    @Operation(summary = "Ghi nhận quyết định xử lý giao dịch (Admin)", description = "Quản trị viên quyết định khớp lệnh thủ công, hoàn tiền hoặc ghi nhận ngoại lệ")
    @PostMapping("/{id}/resolution")public ApiResponse<?> resolve(@PathVariable Long id,@RequestBody PaymentResolutionService.Command c){return ApiResponse.success(service.resolve(id,c));}
}
