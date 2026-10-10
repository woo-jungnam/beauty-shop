package com.core.beautyshop.modules.review.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.review.domain.*;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import com.core.beautyshop.shared.exception.*;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductReviewService {
    private final ProductReviewRepository repository;
    private final CatalogFacade catalogFacade;
    private final OrderFacade orderFacade;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewView create(ReviewCommand command) {
        Long userId = SecurityUtils.getCurrentUserId();
        catalogFacade.lockProductForRating(command.productId());
        if (!catalogFacade.productExistsById(command.productId())) throw new ResourceNotFoundException("Product not found");
        boolean verifiedPurchase = command.orderId() != null;
        if (verifiedPurchase) {
            if (command.rating() == null || command.rating() < 1 || command.rating() > 5) {
                throw new BusinessException("Rating must be from 1 to 5 for verified purchases");
            }
            if (!orderFacade.isDeliveredProductPurchase(command.orderId(), userId, command.productId())) {
                throw new BusinessException("Only delivered purchases can be rated");
            }
            if (repository.existsByProductIdAndUserIdAndOrderId(command.productId(), userId, command.orderId())) {
                throw new BusinessException("This purchase has already been reviewed");
            }
        }
        ProductReview review = ProductReview.builder().productId(command.productId()).userId(userId)
                .orderId(verifiedPurchase ? command.orderId() : null)
                .rating(verifiedPurchase ? command.rating() : null)
                .isVerifiedPurchase(verifiedPurchase)
                .title(command.title()).content(command.content().trim())
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewView moderate(Long id, ReviewStatus status) {
        if (status == null || status == ReviewStatus.PENDING) throw new BusinessException("Invalid moderation status");
        ProductReview review = findForRatingUpdate(id);
        review.setStatus(status);
        review.setApprovedAt(status == ReviewStatus.APPROVED ? Instant.now() : null);
        refreshRating(review.getProductId());
        return ReviewView.from(review);
    }

    @Transactional
    public ReviewView reply(Long id, String reply) {
        if (reply == null || reply.isBlank()) throw new BusinessException("Reply cannot be blank");
        ProductReview review = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
        review.setAdminReply(reply.trim());
        review.setRepliedAt(Instant.now());
        return ReviewView.from(review);
    }

    @Transactional(readOnly = true)
    public ReviewView get(Long id) {
        return ReviewView.from(find(id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        ProductReview review = findForRatingUpdate(id);
        review.setIsDeleted(true);
        refreshRating(review.getProductId());
    }

    private ProductReview find(Long id) {
        return repository.findById(id).filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    private void refreshRating(Long productId) {
        repository.flush();
        ApprovedReviewSummary summary = repository.summarizeApproved(productId);
        double average = summary.averageRating() == null ? 0 : summary.averageRating();
        int count = Math.toIntExact(summary.totalReviews());
        catalogFacade.updateProductRating(productId, Math.round(average * 100.0) / 100.0, count);
    }

    private ProductReview findForRatingUpdate(Long id) {
        Long productId = repository.findProductIdForReview(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
        // Serialize aggregate changes before loading the mutable review; READ_COMMITTED
        // ensures the aggregate sees reviews committed while waiting for this lock.
        catalogFacade.lockProductForRating(productId);
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    @Schema(name = "ProductReviewCreateRequest", description = "Bình luận sản phẩm; đơn đã giao mới được kèm rating")
    public record ReviewCommand(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ID sản phẩm", example = "101") Long productId,
            @Schema(description = "ID đơn DELIVERED thuộc người gửi; có giá trị thì được chấm sao", example = "201") Long orderId,
            @Schema(minimum = "1", maximum = "5", description = "Số sao, chỉ dùng khi có orderId", example = "5") Integer rating,
            @Schema(description = "Tiêu đề tùy chọn") String title,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Nội dung không để trống", maxLength = 2000, example = "Sản phẩm phù hợp với nhu cầu của tôi.") String content) {
        public ReviewCommand {
            if (productId == null) throw new IllegalArgumentException("Product is required");
            if (rating != null && (rating < 1 || rating > 5)) throw new IllegalArgumentException("Rating must be from 1 to 5");
            if (content == null || content.isBlank() || content.length() > 2000) throw new IllegalArgumentException("Review content is required and at most 2000 characters");
        }
    }

    @Schema(name = "ProductReviewView", description = "Thông tin bình luận/đánh giá; danh sách công khai chỉ trả APPROVED chưa xóa")
    public record ReviewView(@Schema(description = "ID đánh giá") Long id,
            @Schema(description = "ID sản phẩm") Long productId, @Schema(description = "ID người đánh giá") Long userId,
            @Schema(description = "ID đơn mua, null nếu chỉ bình luận") Long orderId, @Schema(minimum = "1", maximum = "5", description = "Số sao, null nếu chỉ bình luận") Integer rating,
            @Schema(description = "Đã xác thực mua hàng") Boolean isVerifiedPurchase,
            @Schema(description = "Tiêu đề") String title, @Schema(description = "Nội dung") String content,
            @Schema(description = "Trạng thái kiểm duyệt") ReviewStatus status,
            @Schema(description = "Phản hồi của ADMIN/CS_STAFF, có thể null") String adminReply,
            @Schema(description = "Thời điểm phản hồi UTC, có thể null", format = "date-time") Instant repliedAt,
            @Schema(description = "Thời điểm duyệt UTC, có thể null", format = "date-time") Instant approvedAt,
            @Schema(description = "Thời điểm gửi UTC", format = "date-time") Instant createdAt) {
        static ReviewView from(ProductReview review) {
            return new ReviewView(review.getId(), review.getProductId(), review.getUserId(), review.getOrderId(),
                    review.getRating(), Boolean.TRUE.equals(review.getIsVerifiedPurchase()), review.getTitle(), review.getContent(), review.getStatus(), review.getAdminReply(),
                    review.getRepliedAt(), review.getApprovedAt(), review.getCreatedAt());
        }
    }
}
