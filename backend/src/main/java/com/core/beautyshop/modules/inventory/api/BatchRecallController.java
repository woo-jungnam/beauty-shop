package com.core.beautyshop.modules.inventory.api;
import com.core.beautyshop.modules.inventory.application.service.BatchRecallService;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/inventory/recalls") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@Tag(name = "Thu hồi lô hàng (Admin)", description = "Quản lý chiến dịch thu hồi lô sản phẩm lỗi, truy vết khách hàng đã mua và liên hệ khắc phục")
public class BatchRecallController {
    private final BatchRecallService service;

    @Operation(summary = "Danh sách các đợt thu hồi lô hàng (Admin)", description = "Phân trang danh sách các chiến dịch thu hồi sản phẩm lỗi theo lô")
    @GetMapping public ApiResponse<?> list(@org.springframework.data.web.PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC)Pageable p){return ApiResponse.success(PageResponse.of(service.list(p)));}

    @Operation(summary = "Khởi tạo đợt thu hồi lô hàng mới (Admin)", description = "Kích hoạt cảnh báo thu hồi lô hàng, khóa tồn kho bán và lập danh sách khách hàng cần liên hệ")
    @PostMapping public ApiResponse<?> create(@RequestBody BatchRecallService.Command c){return ApiResponse.success(service.recall(c));}

    @Operation(summary = "Truy vết khách hàng đã mua sản phẩm thuộc lô (Admin)", description = "Lấy danh sách khách hàng và đơn hàng liên quan đến lô hàng bị thu hồi")
    @GetMapping("/{id}/customers")public ApiResponse<?> trace(@PathVariable Long id){return ApiResponse.success(service.trace(id));}

    @Operation(summary = "Ghi nhận liên hệ khách hàng trong đợt thu hồi (Admin)", description = "Lưu lại nhật ký gọi điện, email thông báo và thỏa thuận đổi trả với khách")
    @PostMapping("/{id}/outreach")public ApiResponse<?> contact(@PathVariable Long id,@RequestBody BatchRecallService.Contact c){return ApiResponse.success(service.contact(id,c));}

    @Operation(summary = "Lịch sử tiếp cận và xử lý thu hồi (Admin)", description = "Xem tiến độ liên hệ và kết quả xử lý của toàn bộ khách hàng trong đợt thu hồi")
    @GetMapping("/{id}/outreach-history")public ApiResponse<?> history(@PathVariable Long id){return ApiResponse.success(service.history(id));}

    public record Closure(String reason){}

    @Operation(summary = "Đóng đợt thu hồi lô hàng (Admin)", description = "Quản trị viên xác nhận hoàn tất chiến dịch thu hồi lô sản phẩm")
    @PostMapping("/{id}/close") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<?> close(@PathVariable Long id,@RequestBody Closure c){service.close(id,c.reason());return ApiResponse.success("Recall closed");}
}
