package com.core.beautyshop.modules.crm.api;

import com.core.beautyshop.modules.crm.application.CustomerCareService;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "CRM & Chăm sóc khách hàng (Admin)", description = "Dành cho ADMIN hoặc STAFF: hồ sơ khách, ghi chú nội bộ và lịch sử chăm sóc")
@RestController @RequestMapping("/api/v1/admin/crm") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminCrmController {
    private final CustomerCareService service;

    @Operation(summary = "Tìm khách hàng, tối đa 100 kết quả", description = "ADMIN/CS_STAFF. API list tương thích trả trang đầu tối đa 100; dùng /customers/page để xem các bản ghi tiếp theo. Tìm theo tên/username/email/phone, keyword tối đa 100; %/_ là ký tự thường.")
    @GetMapping("/customers")
    public ApiResponse<List<CustomerCareService.CustomerView>> customers(@Parameter(description = "Tên, username, email hoặc số điện thoại; tối đa 100 ký tự", schema = @Schema(maxLength = 100)) @RequestParam(required=false) String keyword) {
        return ApiResponse.success(service.search(keyword));
    }

    @Operation(summary = "Phân trang tìm kiếm hồ sơ khách hàng", description = "ADMIN/CS_STAFF; trả content và tổng/trang, theo lifetimeValue giảm dần rồi ID giảm dần. Offset phải không tràn Integer.MAX_VALUE.")
    @GetMapping("/customers/page")
    public ApiResponse<PageResponse<CustomerCareService.CustomerView>> customersPage(@Parameter(description = "Từ khóa tối đa 100 ký tự; %/_ được tìm như ký tự thường", schema = @Schema(maxLength = 100)) @RequestParam(required=false) String keyword,
            @Parameter(description = "Trang từ 0", schema = @Schema(minimum = "0")) @RequestParam(defaultValue="0") int page, @Parameter(description = "Số bản ghi 1–100", schema = @Schema(minimum = "1", maximum = "100")) @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.searchPage(keyword, page, size));
    }

    @Operation(summary = "Xem thông tin và tổng hợp hoạt động khách", description = "ADMIN/CS_STAFF; tài khoản chưa xóa. Trả thông tin liên hệ, membership, số order/lịch hẹn và tổng paidAmount của orders PAID; lifetimeValue này không phải lợi nhuận hoặc tiền thu ròng sau hoàn. Ghi chú nội bộ dùng endpoint notes riêng.")
    @GetMapping("/customers/{id}")
    public ApiResponse<CustomerCareService.CustomerView> customerDetail(@PathVariable Long id) {
        return ApiResponse.success(service.getCustomer(id));
    }

    @Operation(summary = "Xem tối đa 100 ghi chú chăm sóc mới nhất", description = "ADMIN/CS_STAFF; API list tương thích chỉ trang đầu tối đa 100. Ghi chú chưa xóa, sắp createdAt rồi ID giảm dần; dùng /notes/page để xem lịch sử đầy đủ.")
    @GetMapping("/customers/{id}/notes")
    public ApiResponse<List<CustomerCareService.NoteView>> notes(@PathVariable Long id) {
        return ApiResponse.success(service.notes(id));
    }

    @Operation(summary = "Phân trang lịch sử ghi chú chăm sóc", description = "ADMIN/CS_STAFF; khách chưa xóa, ghi chú chưa xóa theo createdAt/ID giảm dần. Đây là dữ liệu nội bộ, không dành cho khách tự đọc.")
    @GetMapping("/customers/{id}/notes/page")
    public ApiResponse<PageResponse<CustomerCareService.NoteView>> notesPage(@PathVariable Long id,
            @Parameter(description = "Trang từ 0", schema = @Schema(minimum = "0")) @RequestParam(defaultValue="0") int page, @Parameter(description = "Số ghi chú 1–100", schema = @Schema(minimum = "1", maximum = "100")) @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.notesPage(id, page, size));
    }

    @Operation(summary = "Thêm ghi chú nội bộ về chăm sóc khách", description = "ADMIN/CS_STAFF; content 1–10000 ký tự, noteType tối đa 30, các trường hồ sơ tối đa 1000. followUpAt chỉ lưu thời điểm, không tự tạo instruction/gửi thông báo. HTTP thực tế 200, body ApiResponse.status=201 khi tạo thành công.")
    @PostMapping("/customers/{id}/notes")
    public ApiResponse<CustomerCareService.NoteView> add(@PathVariable Long id, @RequestBody CustomerCareService.NoteCommand command) {
        return ApiResponse.created(service.add(id, command), "Care note created");
    }

    @Operation(summary = "Xóa mềm ghi chú chăm sóc", description = "ADMIN/CS_STAFF; không xóa hồ sơ khách. Ghi chú sẽ không còn trong care-summary, nên hash cảnh báo của lịch thay đổi và cần acknowledgment mới khi policy yêu cầu.")
    @DeleteMapping("/notes/{noteId}")
    public ApiResponse<Void> deleteNote(@PathVariable Long noteId) {
        service.deleteNote(noteId);
        return ApiResponse.success(null);
    }
}
