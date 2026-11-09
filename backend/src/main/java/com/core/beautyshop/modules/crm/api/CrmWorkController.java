package com.core.beautyshop.modules.crm.api;
import com.core.beautyshop.modules.crm.application.CrmWorkService;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/crm/work-items") @PreAuthorize("@staffPermissions.has('CRM')")
@Tag(name = "Công việc chăm sóc CRM (Admin)", description = "Quản lý danh sách nhiệm vụ chăm sóc khách hàng, phân công nhân viên và theo dõi hạn xử lý")
public class CrmWorkController {
    private final CrmWorkService work;

    @Operation(summary = "Danh sách công việc chăm sóc khách hàng (CRM)", description = "Tra cứu và lọc danh sách nhiệm vụ gọi điện, tư vấn, nhắc lịch theo trạng thái và hạn xử lý")
    @GetMapping public ApiResponse<PageResponse<CrmWorkService.View>> list(@RequestParam(required=false) String status,@RequestParam(required=false) String kind,@RequestParam(required=false) Instant dueBefore,@RequestParam(required=false) Long assigneeId,@RequestParam(required=false) Long customerId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(work.list(status,kind,dueBefore,assigneeId,customerId,page,size));}

    @Operation(summary = "Tạo nhiệm vụ chăm sóc khách hàng mới (CRM)", description = "Giao việc chăm sóc, nhắc tái khám hoặc theo dõi phản hồi cho nhân viên")
    @PostMapping public ApiResponse<CrmWorkService.View> create(@Valid @RequestBody CrmWorkService.Create c){return ApiResponse.created(work.create(c),"Đã tạo công việc chăm sóc");}

    @Operation(summary = "Cập nhật trạng thái và kết quả công việc CRM", description = "Ghi nhận kết quả liên hệ, đổi trạng thái sang hoàn tất hoặc dời hạn nhiệm vụ")
    @PutMapping("/{id}") public ApiResponse<CrmWorkService.View> update(@PathVariable Long id,@Valid @RequestBody CrmWorkService.Update c){return ApiResponse.success(work.update(id,c));}
}
