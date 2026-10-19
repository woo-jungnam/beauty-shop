package com.core.beautyshop.modules.crm.api;
import com.core.beautyshop.modules.crm.application.CrmWorkService;
import com.core.beautyshop.shared.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/crm/work-items") @PreAuthorize("@staffPermissions.has('CRM')")
public class CrmWorkController {
    private final CrmWorkService work;
    @GetMapping public ApiResponse<PageResponse<CrmWorkService.View>> list(@RequestParam(required=false) String status,@RequestParam(required=false) String kind,@RequestParam(required=false) Instant dueBefore,@RequestParam(required=false) Long assigneeId,@RequestParam(required=false) Long customerId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(work.list(status,kind,dueBefore,assigneeId,customerId,page,size));}
    @PostMapping public ApiResponse<CrmWorkService.View> create(@Valid @RequestBody CrmWorkService.Create c){return ApiResponse.created(work.create(c),"Đã tạo công việc chăm sóc");}
    @PutMapping("/{id}") public ApiResponse<CrmWorkService.View> update(@PathVariable Long id,@Valid @RequestBody CrmWorkService.Update c){return ApiResponse.success(work.update(id,c));}
}
