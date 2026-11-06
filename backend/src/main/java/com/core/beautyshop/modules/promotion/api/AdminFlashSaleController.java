package com.core.beautyshop.modules.promotion.api;

import com.core.beautyshop.modules.promotion.application.FlashSaleService;
import com.core.beautyshop.modules.promotion.application.FlashSaleService.*;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Chiến dịch Flash Sale (Admin)", description = "Quản lý chiến dịch Flash Sale theo khung giờ, hạn mức suất bán và SKU tham gia")
@RestController
@RequestMapping("/api/v1/admin/flash-sales")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminFlashSaleController {

    private final FlashSaleService flashSaleService;

    @Operation(summary = "Danh sách chiến dịch Flash Sale (phân trang)")
    @GetMapping
    public ApiResponse<PageResponse<CampaignView>> list(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(flashSaleService.findAll(pageable)));
    }

    @Operation(summary = "Chi tiết một chiến dịch Flash Sale kèm danh mục SKU")
    @GetMapping("/{id}")
    public ApiResponse<CampaignDetailView> getById(@PathVariable Long id) {
        return ApiResponse.success(flashSaleService.getById(id));
    }

    @Operation(summary = "Lấy danh sách biến thể SKU và các lô hàng kho để chọn vào Flash Sale")
    @GetMapping("/candidate-items")
    public ApiResponse<List<FlashSaleCandidateItem>> getCandidateItems() {
        return ApiResponse.success(flashSaleService.getCandidateItems());
    }

    @Operation(summary = "Tạo chiến dịch Flash Sale mới")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CampaignDetailView> create(@Valid @RequestBody CampaignCommand command) {
        return ApiResponse.created(flashSaleService.create(command), "Tạo chiến dịch Flash Sale thành công");
    }

    @Operation(summary = "Cập nhật chiến dịch Flash Sale")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CampaignDetailView> update(@PathVariable Long id, @Valid @RequestBody CampaignCommand command) {
        return ApiResponse.success(flashSaleService.update(id, command));
    }

    @Operation(summary = "Xóa chiến dịch Flash Sale")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        flashSaleService.delete(id);
        return ApiResponse.success(null);
    }
}
