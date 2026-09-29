package com.core.beautyshop.modules.review.api;

import com.core.beautyshop.modules.review.application.ProductReviewService;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import com.core.beautyshop.shared.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReviewController {
    private final ProductReviewService service;

    @GetMapping
    public ApiResponse<PageResponse<ProductReviewService.ReviewView>> list(@RequestParam(required = false) ReviewStatus status, Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.adminReviews(status, pageable)));
    }
    @PutMapping("/{id}/status")
    public ApiResponse<ProductReviewService.ReviewView> moderate(@PathVariable Long id, @RequestBody StatusRequest request) {
        return ApiResponse.success(service.moderate(id, request.status()));
    }
    @PutMapping("/{id}/reply")
    public ApiResponse<ProductReviewService.ReviewView> reply(@PathVariable Long id, @RequestBody ReplyRequest request) {
        return ApiResponse.success(service.reply(id, request.reply()));
    }
    public record StatusRequest(ReviewStatus status) { }
    public record ReplyRequest(String reply) { }
}
