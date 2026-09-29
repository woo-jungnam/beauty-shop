package com.core.beautyshop.modules.review.api;

import com.core.beautyshop.modules.review.application.ProductReviewService;
import com.core.beautyshop.shared.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ProductReviewController {
    private final ProductReviewService service;

    @GetMapping
    public ApiResponse<PageResponse<ProductReviewService.ReviewView>> list(@RequestParam Long productId, Pageable pageable) {
        return ApiResponse.success(PageResponse.of(service.publicReviews(productId, pageable)));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ProductReviewService.ReviewView> create(@Valid @RequestBody ProductReviewService.ReviewCommand command) {
        return ApiResponse.created(service.create(command), "Review submitted for moderation");
    }
}
