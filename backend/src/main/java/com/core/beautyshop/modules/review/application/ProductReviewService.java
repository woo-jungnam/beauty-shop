package com.core.beautyshop.modules.review.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.review.domain.*;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductReviewService {
    private final ProductReviewRepository repository;
    private final CatalogFacade catalogFacade;
    private final OrderFacade orderFacade;

    @Transactional
    public ReviewView create(ReviewCommand command) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!catalogFacade.productExistsById(command.productId())) throw new ResourceNotFoundException("Product not found");
        if (!orderFacade.isDeliveredProductPurchase(command.orderId(), userId, command.productId())) {
            throw new BusinessException("Only delivered purchases can be reviewed");
        }
        if (repository.existsByProductIdAndUserIdAndOrderId(command.productId(), userId, command.orderId())) {
            throw new BusinessException("This purchase has already been reviewed");
        }
        ProductReview review = ProductReview.builder().productId(command.productId()).userId(userId)
                .orderId(command.orderId()).rating(command.rating()).title(command.title()).content(command.content().trim())
                .status(ReviewStatus.PENDING).build();
        return ReviewView.from(repository.save(review));
    }

    @Transactional(readOnly = true)
    public Page<ReviewView> publicReviews(Long productId, Pageable pageable) {
        return repository.findByProductIdAndStatusAndIsDeletedFalse(productId, ReviewStatus.APPROVED, pageable).map(ReviewView::from);
    }

    @Transactional(readOnly = true)
    public Page<ReviewView> adminReviews(ReviewStatus status, Pageable pageable) {
        Page<ProductReview> page = status == null ? repository.findByIsDeletedFalse(pageable)
                : repository.findByStatusAndIsDeletedFalse(status, pageable);
        return page.map(ReviewView::from);
    }

    @Transactional
    public ReviewView moderate(Long id, ReviewStatus status) {
        if (status == null || status == ReviewStatus.PENDING) throw new BusinessException("Invalid moderation status");
        ProductReview review = find(id);
        review.setStatus(status);
        review.setApprovedAt(status == ReviewStatus.APPROVED ? Instant.now() : null);
        refreshRating(review.getProductId());
        return ReviewView.from(review);
    }

    @Transactional
    public ReviewView reply(Long id, String reply) {
        if (reply == null || reply.isBlank()) throw new BusinessException("Reply cannot be blank");
        ProductReview review = find(id);
        review.setAdminReply(reply.trim());
        review.setRepliedAt(Instant.now());
        return ReviewView.from(review);
    }

    private ProductReview find(Long id) {
        return repository.findById(id).filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    private void refreshRating(Long productId) {
        repository.flush();
        Object[] summary = repository.summarizeApproved(productId);
        double average = summary[0] == null ? 0 : ((Number) summary[0]).doubleValue();
        int count = summary[1] == null ? 0 : ((Number) summary[1]).intValue();
        catalogFacade.updateProductRating(productId, Math.round(average * 100.0) / 100.0, count);
    }

    public record ReviewCommand(Long productId, Long orderId, Integer rating, String title, String content) {
        public ReviewCommand {
            if (productId == null || orderId == null) throw new IllegalArgumentException("Product and order are required");
            if (rating == null || rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be from 1 to 5");
            if (content == null || content.isBlank() || content.length() > 2000) throw new IllegalArgumentException("Review content is required and at most 2000 characters");
        }
    }

    public record ReviewView(Long id, Long productId, Long userId, Long orderId, Integer rating, String title,
            String content, ReviewStatus status, String adminReply, Instant repliedAt, Instant approvedAt, Instant createdAt) {
        static ReviewView from(ProductReview review) {
            return new ReviewView(review.getId(), review.getProductId(), review.getUserId(), review.getOrderId(),
                    review.getRating(), review.getTitle(), review.getContent(), review.getStatus(), review.getAdminReply(),
                    review.getRepliedAt(), review.getApprovedAt(), review.getCreatedAt());
        }
    }
}
