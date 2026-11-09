package com.core.beautyshop.modules.dashboard.api;
import com.core.beautyshop.modules.dashboard.application.ProfitabilityService;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
import java.time.Instant;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/dashboard") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@Tag(name = "Lợi nhuận & Chi phí vận hành (Admin)", description = "Báo cáo lợi nhuận gộp/ròng, quản lý chi phí vận hành và hạch toán dòng tiền")
public class ProfitabilityController {
    private final ProfitabilityService service;

    @Operation(summary = "Báo cáo lợi nhuận và doanh thu gộp (Admin)", description = "Tổng hợp doanh thu, giá vốn bán hàng, chi phí và lợi nhuận ròng trong kỳ")
    @GetMapping("/profitability")public ApiResponse<?> report(@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to){return ApiResponse.success(service.report(from,to));}

    @Operation(summary = "Danh sách chi phí vận hành (Admin)", description = "Tra cứu và phân trang các khoản chi phí mặt bằng, điện nước, nhân sự, tiếp thị")
    @GetMapping("/expenses")public ApiResponse<?> list(@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable p){return ApiResponse.success(PageResponse.of(service.list(p)));}

    @Operation(summary = "Ghi nhận khoản chi phí vận hành mới (Admin)", description = "Thêm mới một khoản mục chi phí thực tế phát sinh vào hệ thống")
    @PostMapping("/expenses") @PreAuthorize("hasRole('ADMIN')")public ApiResponse<?> expense(@RequestBody ProfitabilityService.Expense c){return ApiResponse.success(service.record(c));}
}
