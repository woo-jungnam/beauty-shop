package com.core.beautyshop.modules.spa.api;

import com.core.beautyshop.modules.spa.application.service.SpaPackageRequestService;
import com.core.beautyshop.modules.spa.domain.SpaPackageRequest;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
@Tag(name = "Yêu cầu xử lý gói Spa", description = "Khách hàng gửi yêu cầu đổi, bảo lưu, chuyển nhượng gói liệu trình và Admin phê duyệt")
public class SpaPackageRequestController {
    private final SpaPackageRequestService service;
    private final com.core.beautyshop.modules.spa.application.service.SpaRefundService refunds;

    @Operation(summary = "Xem báo giá quyết toán gói liệu trình", description = "Tính toán số tiền hoàn dự kiến dựa trên số buổi chưa sử dụng và chính sách khấu trừ")
    @GetMapping("/spa/tickets/{ticketId}/settlement-quote")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<com.core.beautyshop.modules.spa.application.service.SpaRefundService.PackageQuote> settlementQuote(@PathVariable Long ticketId) {
        return ApiResponse.success(refunds.userPackageQuote(ticketId));
    }

    @Operation(summary = "Danh sách yêu cầu xử lý gói chờ duyệt (Admin)", description = "Lấy toàn bộ yêu cầu đổi gói, bảo lưu hoặc chuyển nhượng đang chờ Admin phê duyệt")
    @GetMapping("/admin/spa/package-requests") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SpaPackageRequest>> pending() { return ApiResponse.success(service.pending()); }

    @Operation(summary = "Lịch sử yêu cầu của vé liệu trình", description = "Xem danh sách các yêu cầu xử lý đã gửi cho một vé dịch vụ cụ thể")
    @GetMapping({"/spa/tickets/{ticketId}/requests","/admin/spa/tickets/{ticketId}/requests"})
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SpaPackageRequest>> list(@PathVariable Long ticketId) { return ApiResponse.success(service.list(ticketId)); }

    @Operation(summary = "Gửi yêu cầu xử lý gói liệu trình", description = "Khách hàng tạo yêu cầu chuyển nhượng, bảo lưu hoặc đổi gói dịch vụ")
    @PostMapping("/spa/tickets/{ticketId}/requests") @PreAuthorize("isAuthenticated()")
    public ApiResponse<SpaPackageRequest> create(@PathVariable Long ticketId,@RequestBody SpaPackageRequestService.Command command) { return ApiResponse.success(service.create(ticketId,command)); }

    @Operation(summary = "Phê duyệt hoặc từ chối yêu cầu gói (Admin)", description = "Quản trị viên xét duyệt yêu cầu xử lý vé gói liệu trình của khách hàng")
    @PostMapping("/admin/spa/tickets/{ticketId}/requests/{requestId}/review") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SpaPackageRequest> review(@PathVariable Long ticketId,@PathVariable Long requestId,@RequestBody SpaPackageRequestService.Decision decision) { return ApiResponse.success(service.review(ticketId,requestId,decision)); }
}
