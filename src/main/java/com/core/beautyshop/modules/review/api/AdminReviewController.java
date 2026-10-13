package com.core.beautyshop.modules.review.api;

import com.core.beautyshop.modules.review.application.ProductReviewService;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Kiểm duyệt đánh giá sản phẩm (Admin)", description = "ADMIN hoặc STAFF kiểm duyệt đánh giá, phản hồi và xóa")
@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminReviewController {
    private final ProductReviewService service;

    @Operation(summary = "Lấy danh sách đánh giá quản trị", description = "ADMIN hoặc STAFF. Không gửi status sẽ lấy mọi trạng thái, chưa xóa; page bắt đầu từ 0.")
    @GetMapping
    public ApiResponse<PageResponse<ProductReviewService.ReviewView>> list(@Parameter(description = "Bộ lọc PENDING, APPROVED hoặc REJECTED") @RequestParam(required = false) ReviewStatus status, @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.adminReviews(status, pageable)));
    }

    @Operation(summary = "Xem chi tiết đánh giá theo ID")
    @GetMapping("/{id}")
    public ApiResponse<ProductReviewService.ReviewView> getById(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @Operation(summary = "Kiểm duyệt trạng thái đánh giá (APPROVED hoặc REJECTED)", description = "ADMIN hoặc CS_STAFF. Không nhận PENDING; tính lại điểm và số lượt đánh giá APPROVED của sản phẩm.")
    @PutMapping("/{id}/status")
    public ApiResponse<ProductReviewService.ReviewView> moderate(@PathVariable Long id, @RequestBody StatusRequest request) {
        return ApiResponse.success(service.moderate(id, request.status()));
    }

    @Operation(summary = "Gửi phản hồi của Quản trị viên / CSKH cho đánh giá", description = "ADMIN hoặc CS_STAFF. Nội dung không được rỗng; thay thế phản hồi trước và cập nhật thời điểm phản hồi.")
    @PutMapping("/{id}/reply")
    public ApiResponse<ProductReviewService.ReviewView> reply(@PathVariable Long id, @RequestBody ReplyRequest request) {
        return ApiResponse.success(service.reply(id, request.reply()));
    }

    @Operation(summary = "Xóa đánh giá sản phẩm (Soft delete)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @Schema(name = "ReviewModerationRequest", description = "Chuyển đánh giá sang APPROVED hoặc REJECTED")
    public record StatusRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"APPROVED", "REJECTED"}, example = "APPROVED") ReviewStatus status) { }
    @Schema(name = "ReviewReplyRequest")
    public record ReplyRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Nội dung phản hồi không để trống", example = "Cảm ơn bạn đã chia sẻ trải nghiệm.") String reply) { }
}
