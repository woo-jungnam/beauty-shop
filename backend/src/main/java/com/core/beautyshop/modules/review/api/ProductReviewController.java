package com.core.beautyshop.modules.review.api;

import com.core.beautyshop.modules.review.application.ProductReviewService;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import com.core.beautyshop.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Đánh giá sản phẩm", description = "Đánh giá công khai đã duyệt và gửi đánh giá từ đơn hàng của chính mình")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ProductReviewController {
    private final ProductReviewService service;

    @Operation(summary = "Xem đánh giá công khai của sản phẩm", description = "Không cần đăng nhập. Chỉ trả đánh giá APPROVED chưa xóa; page bắt đầu từ 0.")
    @GetMapping
    public ApiResponse<PageResponse<ProductReviewService.ReviewView>> list(@Parameter(description = "ID sản phẩm cần xem đánh giá", required = true) @RequestParam Long productId, @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.publicReviews(productId, pageable)));
    }

    @Operation(summary = "Kiểm tra điều kiện đánh giá sản phẩm của người dùng", description = "Yêu cầu đăng nhập. Kiểm tra xem người dùng đã mua và nhận sản phẩm này chưa.")
    @GetMapping("/eligibility")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProductReviewService.ReviewEligibilityResponse> checkEligibility(@Parameter(description = "ID sản phẩm", required = true) @RequestParam Long productId) {
        return ApiResponse.success(service.checkEligibility(productId));
    }

    @Operation(summary = "Gửi đánh giá từ đơn hàng đã giao", description = "Yêu cầu đăng nhập. Đơn DELIVERED phải thuộc người gửi và chứa sản phẩm; mỗi sản phẩm/đơn/người dùng chỉ có một đánh giá. Đánh giá từ người mua hợp lệ sẽ được phê duyệt tự động và hiển thị công khai ngay.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đã nhận đánh giá", useReturnTypeSchema = true)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProductReviewService.ReviewView> create(@Valid @RequestBody ProductReviewService.ReviewCommand command) {
        ProductReviewService.ReviewView view = service.create(command);
        String message = view.status() == ReviewStatus.APPROVED
                ? "Đánh giá đã được phê duyệt và hiển thị công khai"
                : "Review submitted for moderation";
        return ApiResponse.created(view, message);
    }
}
