package com.core.beautyshop.modules.review.application;

import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.review.domain.ProductReview;
import com.core.beautyshop.modules.review.domain.ProductReviewRepository;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ProductReviewServiceIntegrationTest {
    @Autowired ProductReviewService service;
    @Autowired ProductReviewRepository reviews;
    @Autowired ProductRepository products;
    private final List<Long> reviewIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        reviews.deleteAllById(reviewIds);
        products.deleteAllById(productIds);
    }

    @Test
    void moderationAndDeletionRecalculateTheRealRepositoryAggregate() {
        Product product = product();
        ProductReview five = review(product.getId(), 5);
        ProductReview one = review(product.getId(), 1);

        service.moderate(five.getId(), ReviewStatus.APPROVED);
        service.moderate(one.getId(), ReviewStatus.APPROVED);
        assertAggregate(product.getId(), 3.0, 2);

        service.moderate(one.getId(), ReviewStatus.REJECTED);
        assertAggregate(product.getId(), 5.0, 1);
        service.delete(five.getId());
        assertAggregate(product.getId(), 0.0, 0);
    }

    @Test
    void concurrentModerationKeepsBothApprovedReviewsInTheProductAggregate() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 5; round++) {
                Product product = product();
                ProductReview five = review(product.getId(), 5);
                ProductReview one = review(product.getId(), 1);
                CountDownLatch start = new CountDownLatch(1);
                var first = executor.submit(() -> { start.await(); return service.moderate(five.getId(), ReviewStatus.APPROVED); });
                var second = executor.submit(() -> { start.await(); return service.moderate(one.getId(), ReviewStatus.APPROVED); });
                start.countDown();
                first.get(20, TimeUnit.SECONDS);
                second.get(20, TimeUnit.SECONDS);
                assertAggregate(product.getId(), 3.0, 2);
            }
        }
    }

    private Product product() {
        Product row = products.saveAndFlush(Product.builder().name("Review regression")
                .slug("review-regression-" + UUID.randomUUID()).build());
        productIds.add(row.getId());
        return row;
    }

    private ProductReview review(Long productId, int rating) {
        ProductReview row = reviews.saveAndFlush(ProductReview.builder().productId(productId)
                .userId(42L).orderId(100L + reviewIds.size()).rating(rating).isVerifiedPurchase(true).content("Review regression").build());
        reviewIds.add(row.getId());
        return row;
    }

    private void assertAggregate(Long productId, double average, int count) {
        Product row = products.findById(productId).orElseThrow();
        assertEquals(average, row.getAverageRating(), 0.001);
        assertEquals(count, row.getTotalReviews());
    }
}
