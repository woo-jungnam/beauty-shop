package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.order.api.dto.SpaVisitInvoiceResult;
import com.core.beautyshop.modules.spa.application.dto.request.*;
import com.core.beautyshop.modules.spa.application.service.*;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/appointments")
@Tag(name = "Thanh toán buổi Spa", description = "Hóa đơn buổi đã hoàn tất và phiếu thu CASH/BANK; không thu lại dịch vụ dùng vé")
public class SpaAppointmentCheckoutController {
    private final SpaAppointmentCheckoutService checkout;
    @Operation(summary = "Xem quy tắc chốt hóa đơn Spa", description = "Mọi tài khoản đăng nhập. Mặc định invoice sau COMPLETED, chỉ tính item PERFORMED không dùng vé, làm tròn tổng VND HALF_UP. Chưa bật cọc/phí hủy/phí no-show hoặc tự hoàn mục bỏ qua.")
    @GetMapping("/checkout-policy")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SpaCheckoutPolicy> policy() { return ApiResponse.success(SpaCheckoutPolicy.defaults()); }

    @Operation(summary = "Lập hóa đơn cố định cho buổi Spa", description = "ADMIN hoặc STAFF. Buổi COMPLETED, mỗi item phải PERFORMED/SKIPPED; LEGACY_FINALIZED cần đối soát. Chỉ tính giá/tên đã đặt của mục PERFORMED không dùng vé, tổng làm tròn VND HALF_UP; 0 đồng là PAID không tạo phiếu thu. Một lịch có một invoice; replay trả invoice hiện có, cùng khóa khác nội dung bị từ chối. HTTP 200 cho cả tạo mới/replay. Chỉ BANK/CASH; không vận chuyển, trừ tồn kho hoặc cấp vé.")
    @PostMapping("/{id}/invoice")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<SpaVisitInvoiceResult> create(@PathVariable Long id, @Valid @RequestBody CreateSpaVisitInvoiceRequest request,
                                                    @Parameter(description = "Bắt buộc, không trắng, tối đa 128 ký tự; giữ nguyên cho cùng yêu cầu lập invoice", example = "spa-invoice-101-001") @RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success(checkout.create(id, request, key));
    }
    @Operation(summary = "Xem hóa đơn và số tiền Spa còn thiếu", description = "Chủ invoice hoặc ADMIN/STAFF. Trả snapshot, thực thu và số còn thiếu; QR BANK phản ánh số thiếu hiện tại.")
    @GetMapping("/{id}/invoice")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SpaVisitInvoiceResult> get(@PathVariable Long id) { return ApiResponse.success(checkout.get(id)); }

    @Operation(summary = "Ghi nhận một phiếu thu tiền mặt Spa", description = "ADMIN hoặc STAFF. Invoice buổi COMPLETED; số tiền VND nguyên, dương, không vượt số còn thiếu và chưa hoàn. Có thể thu nhiều lần hoặc thu phần thiếu của invoice BANK. Cùng khóa/số tiền/đơn gửi lại không thu thêm; khóa cũ khác nội dung bị từ chối. Ghi actor trong ledger CASH; không tự hoàn tiền.")
    @PostMapping("/{id}/invoice/cash-receipts")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiResponse<SpaVisitInvoiceResult> cash(@PathVariable Long id, @Valid @RequestBody SpaCashReceiptRequest request,
                                                  @Parameter(description = "Mã phiếu thu ổn định, không trắng, tối đa 128 ký tự. Mỗi phiếu thu mới dùng khóa riêng; retry dùng lại đúng khóa", example = "spa-cash-receipt-20261005-001") @RequestHeader("Idempotency-Key") String receiptKey) {
        return ApiResponse.success(checkout.collectCash(id, request, receiptKey));
    }
}
