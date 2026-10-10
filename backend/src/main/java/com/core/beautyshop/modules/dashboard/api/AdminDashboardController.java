package com.core.beautyshop.modules.dashboard.api;

import com.core.beautyshop.modules.dashboard.application.AdminDashboardService;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;

@Tag(name = "Dashboard & Báo cáo điều hành (Admin)", description = "API số liệu tổng quan doanh thu, đơn hàng, top sản phẩm bán chạy và hiệu suất lịch hẹn Spa")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService service;

    @Operation(summary = "Lấy tổng quan điều hành", description = "ADMIN hoặc STAFF. Khoảng Instant [from,to): mặc định 30 ngày trước đến hiện tại, to phải sau from. Tổng đơn theo createdAt; đơn đã trả/AOV theo paidAt; tiền vào theo ledger và hoàn theo confirmedAt. revenue là tiền vào gộp, không phải lợi nhuận hoặc doanh thu đã thực hiện; CASH/BANK/COD tách riêng. Khách hoạt động và tồn kho là số dư hiện tại.")
    @GetMapping
    public ApiResponse<AdminDashboardService.Overview> overview(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return ApiResponse.success(service.overview(from, to));
    }

    @Operation(summary = "Lấy xu hướng tiền vào và tiền hoàn", description = "Khoảng Instant [from,to), mặc định 30 ngày. Gom day/month theo giờ Việt Nam. revenue là tiền vào gộp, refundedAmount riêng; orderCount theo paidAt. Có cả ngày chỉ phát sinh hoàn tiền.")
    @GetMapping("/revenue")
    public ApiResponse<List<AdminDashboardService.RevenuePoint>> revenue(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @io.swagger.v3.oas.annotations.Parameter(description = "Độ chia thời gian theo giờ Việt Nam", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"day", "month"})) @RequestParam(defaultValue = "day") String period) {
        return ApiResponse.success(service.revenue(from, to, period));
    }

    @Operation(summary = "Lấy 10 sản phẩm bán chạy", description = "Đơn DELIVERED, lọc createdAt trong khoảng Instant [from,to), mặc định 30 ngày; sắp theo unitsSold rồi grossSales. Không phải xếp hạng doanh thu Spa.")
    @GetMapping("/top-products")
    public ApiResponse<List<AdminDashboardService.TopProduct>> topProducts(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return ApiResponse.success(service.topProducts(from, to));
    }

    @Operation(summary = "Lấy độ lấp lịch và thời gian phục vụ Spa", description = "Ngày from/to bao gồm hai đầu, giờ Việt Nam; mặc định toàn tháng hiện tại. booked/reserved là phút kế hoạch của nhân viên; served dùng giờ thực tế PERFORMED, giới hạn trong kỳ; legacyFinalized riêng. overCapacity chỉ so tổng phút với ca, không chứng minh đủ phòng/giường/máy hoặc đủ từng slot.")
    @GetMapping("/spa-occupancy")
    public ApiResponse<AdminDashboardService.SpaOccupancy> spaOccupancy(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return ApiResponse.success(service.spaOccupancy(from, to));
    }

    @GetMapping("/spa-financials")
    @Operation(summary="Lấy số dư tài chính Spa hiện tại", description="Không lọc theo ngày. Công nợ chỉ visit invoice COMPLETED/PENDING, total > paid. Chưa lập invoice: buổi COMPLETED có item PERFORMED ngoài vé, cộng giá snapshot rồi làm tròn từng buổi. Legacy: đếm COMPLETED/LEGACY_FINALIZED chưa invoice, không suy số tiền.")
    public ApiResponse<AdminDashboardService.SpaFinancials> spaFinancials() {
        return ApiResponse.success(service.spaFinancials());
    }
}
