package com.core.beautyshop.modules.spa.api;
import com.core.beautyshop.modules.spa.application.service.SpaRefundService;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/v1/admin/spa") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
@io.swagger.v3.oas.annotations.tags.Tag(name="Quyết toán dịch vụ Spa", description="ADMIN xem trước và ghi nhận hoàn tiền thực tế, không tự chuyển tiền ngân hàng")
public class SpaRefundController {
    private final SpaRefundService refunds;
    @io.swagger.v3.oas.annotations.Operation(summary="Báo giá quyết toán gói lượt", description="Chỉ gói thông thường; thiếu giá lúc mua, có lịch giữ lượt hoặc hoàn tiền chưa đối soát sẽ trả blockedReason. Lượt bù không tăng tiền hoàn.")
    @GetMapping("/tickets/{id}/settlement") public ApiResponse<SpaRefundService.PackageQuote> quote(@PathVariable Long id) { return ApiResponse.success(refunds.packageQuote(id)); }
    @io.swagger.v3.oas.annotations.Operation(summary="Xác nhận quyết toán và thu hồi vé", description="Số tiền phải khớp báo giá khi khóa đơn/vé; gồm đóng vé với số hoàn 0. Cùng chứng từ và nội dung gửi lại không hoàn thêm; chứng từ khác nội dung bị chặn. Tiền hoàn và thu hồi vé cùng giao dịch.")
    @PostMapping("/tickets/{id}/settlement") public ApiResponse<com.core.beautyshop.modules.spa.domain.SpaServiceRefund> settle(@PathVariable Long id,@RequestBody SpaRefundService.Confirmation c) { return ApiResponse.success(refunds.settlePackage(id,c)); }
    @io.swagger.v3.oas.annotations.Operation(summary="Xem hạn mức hoàn từng dòng hóa đơn Spa", description="Hóa đơn đã thu đủ; phân bổ giảm giá theo tỷ trọng giá dòng, làm tròn tích lũy nguyên VND. Không có dòng thanh toán bằng vé/bỏ qua.")
    @GetMapping("/appointments/{id}/service-refunds") public ApiResponse<SpaRefundService.VisitQuote> visit(@PathVariable Long id) { return ApiResponse.success(refunds.visitQuote(id)); }
    @io.swagger.v3.oas.annotations.Operation(summary="Ghi nhận hoàn tiền một phần theo dịch vụ", description="Không vượt hạn mức còn lại của dòng và tiền thực thu chưa hoàn. Yêu cầu số tiền nguyên VND dương, chứng từ và lý do; cùng nội dung/chứng từ gửi lại không tạo giao dịch mới.")
    @PostMapping("/appointments/{id}/items/{itemId}/service-refunds") public ApiResponse<com.core.beautyshop.modules.spa.domain.SpaServiceRefund> refund(@PathVariable Long id,@PathVariable Long itemId,@RequestBody SpaRefundService.Confirmation c) { return ApiResponse.success(refunds.refundVisitLine(id,itemId,c)); }
}
