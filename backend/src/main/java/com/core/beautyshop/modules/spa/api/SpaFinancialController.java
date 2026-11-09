package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.SpaFinancialService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.math.BigDecimal;

@RestController @RequiredArgsConstructor
@Tag(name = "Tài chính & Đặt cọc Spa", description = "Chính sách tài chính Spa, thanh toán cọc, phiếu thu cọc/phí và hoàn cọc lịch hẹn")
public class SpaFinancialController {
    private final SpaFinancialService finance;

    @Operation(summary = "Xem chính sách tài chính Spa", description = "Công khai chính sách đặt cọc, phí hủy lịch và quy định hoàn tiền lịch hẹn")
    @GetMapping("/api/v1/spa/financial-policy") public ApiResponse<SpaFinancialService.Policy> policy() { return ApiResponse.success(finance.policy()); }

    @Operation(summary = "Cập nhật chính sách tài chính Spa (Admin)", description = "Quản trị viên cấu hình mức cọc, thời hạn thanh toán cọc và phí phạt hủy")
    @PreAuthorize("hasRole('ADMIN')") @PutMapping("/api/v1/admin/spa/financial-policy") public ApiResponse<SpaFinancialService.Policy> policy(@RequestBody SpaFinancialService.Policy p) { return ApiResponse.success(finance.savePolicy(p)); }

    @Operation(summary = "Báo cáo doanh thu tài chính Spa (Admin)", description = "Tổng hợp doanh thu tiền mặt, chuyển khoản, tiền cọc và tiền phạt của Spa")
    @PreAuthorize("hasRole('ADMIN')") @GetMapping("/api/v1/admin/spa/financial-report") public ApiResponse<Map<String,BigDecimal>> report() { return ApiResponse.success(finance.report()); }

    @Operation(summary = "Xem thông tin tài chính lịch hẹn", description = "Chi tiết số tiền cọc đã đóng, phí dịch vụ và trạng thái thanh toán của lịch hẹn")
    @PreAuthorize("isAuthenticated()") @GetMapping("/api/v1/spa/appointments/{id}/finance") public ApiResponse<SpaFinancialService.Finance> view(@PathVariable Long id) { return ApiResponse.success(finance.view(id)); }

    @Operation(summary = "Tạo yêu cầu thanh toán cọc lịch hẹn", description = "Khách hàng khởi tạo thanh toán tiền đặt cọc cho lịch hẹn Spa qua cổng trực tuyến")
    @PreAuthorize("isAuthenticated()") @PostMapping("/api/v1/spa/appointments/{id}/deposit-payment") public ApiResponse<SpaFinancialService.Finance> payment(@PathVariable Long id) { return ApiResponse.success(finance.depositPayment(id)); }

    @Operation(summary = "Ghi nhận phiếu thu tiền cọc (Staff/Admin)", description = "Nhân viên ghi nhận thu tiền cọc trực tiếp tại quầy bằng tiền mặt hoặc chuyển khoản")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping("/api/v1/spa/appointments/{id}/deposit-receipts") public ApiResponse<SpaFinancialService.Finance> deposit(@PathVariable Long id,@RequestBody SpaFinancialService.Receipt c,@RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(finance.receipt(id,c,key,false)); }

    @Operation(summary = "Ghi nhận phiếu thu phí phát sinh (Staff/Admin)", description = "Ghi nhận thu các khoản phụ phí hoặc phí hủy muộn của lịch hẹn")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping("/api/v1/spa/appointments/{id}/fee-receipts") public ApiResponse<SpaFinancialService.Finance> fee(@PathVariable Long id,@RequestBody SpaFinancialService.Receipt c,@RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(finance.receipt(id,c,key,true)); }

    @Operation(summary = "Xác nhận hoàn tiền cọc lịch hẹn (Admin)", description = "Quản trị viên xác nhận đã hoàn lại tiền đặt cọc cho khách hàng")
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/api/v1/spa/appointments/{id}/deposit-refund-confirmations") public ApiResponse<SpaFinancialService.Finance> refund(@PathVariable Long id,@RequestBody SpaFinancialService.Receipt c,@RequestHeader("Idempotency-Key") String key) { return ApiResponse.success(finance.refund(id,c,key)); }
}
