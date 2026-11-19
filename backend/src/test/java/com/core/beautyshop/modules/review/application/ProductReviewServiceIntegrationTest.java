package com.core.beautyshop.modules.review.application;

import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.ProductRepository;
import com.core.beautyshop.modules.review.domain.ProductReview;
import com.core.beautyshop.modules.review.domain.ProductReviewRepository;
import com.core.beautyshop.modules.review.domain.enums.ReviewSentiment;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void sentimentIsIndependentOfModerationAndFiltersBeforePagination() throws Exception {
        Product product = product();
        ProductReview negative = review(product.getId(), 1);
        ProductReview pending = review(product.getId(), 5);
        service.moderate(negative.getId(), ReviewStatus.APPROVED);

        String version = "a".repeat(64);
        Instant analyzed = Instant.now();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            try (exchange) {
                if (exchange.getRequestHeaders().getFirst("Content-Length") == null) {
                    exchange.sendResponseHeaders(411, -1);
                    return;
                }
                byte[] body = """
                        {"modelVersion":"%s","results":[{"label":"NEGATIVE","score":1.0},{"label":"POSITIVE","score":-1.0}]}
                        """.formatted(version).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            new ReviewSentimentWorker(reviews, service, "http://127.0.0.1:" + server.getAddress().getPort())
                    .analyzePendingReviews();
        } finally {
            server.stop(0);
        }
        assertEquals(ReviewSentiment.POSITIVE, reviews.findById(pending.getId()).orElseThrow().getSentimentState());
        assertEquals(0, reviews.completeSentimentAttempt(negative.getId(), 0, ReviewSentiment.PENDING,
                ReviewSentiment.POSITIVE, null, version, analyzed));

        var matches = service.adminReviews(ReviewStatus.APPROVED, ReviewSentiment.NEGATIVE, PageRequest.of(0, 1));
        assertEquals(1, matches.getTotalElements());
        var admin = matches.getContent().getFirst();
        assertEquals(negative.getId(), admin.review().id());
        assertEquals("COMPLETED", admin.sentimentStatus());
        var mapper = new ObjectMapper().findAndRegisterModules();
        var adminJson = mapper.valueToTree(admin);
        assertEquals(negative.getId().longValue(), adminJson.path("id").asLong());
        assertEquals("NEGATIVE", adminJson.path("sentiment").asText());
        assertEquals(ReviewStatus.APPROVED, reviews.findById(negative.getId()).orElseThrow().getStatus());
        var publicReview = service.publicReviews(product.getId(), PageRequest.of(0, 10)).getContent().getFirst();
        assertFalse(mapper.valueToTree(publicReview).has("sentiment"));

        assertEquals(ReviewStatus.REJECTED, service.moderate(negative.getId(), ReviewStatus.REJECTED).status());
        assertEquals(List.of(pending.getId()), service.publicReviews(product.getId(), PageRequest.of(0, 10))
                .map(ProductReviewService.ReviewView::id).getContent());
        assertAggregate(product.getId(), 5.0, 1);
        assertEquals("COMPLETED", service.adminReviews(ReviewStatus.REJECTED, ReviewSentiment.NEGATIVE,
                PageRequest.of(0, 10)).getContent().getFirst().sentimentStatus());

        assertEquals(ReviewStatus.APPROVED, service.moderate(negative.getId(), ReviewStatus.APPROVED).status());
        assertEquals(2, service.publicReviews(product.getId(), PageRequest.of(0, 10)).getTotalElements());
        assertAggregate(product.getId(), 3.0, 2);
        assertEquals(ReviewSentiment.NEGATIVE, reviews.findById(negative.getId()).orElseThrow().getSentimentState());
    }


    @Test
    void negativeSentimentAutomaticallyRejectsPendingReviewsAndAdminCanOverride() {
        Product product = product();
        ProductReview row = review(product.getId(), 5);
        String version = "a".repeat(64);

        service.applySentiment(row.getId(), 0, ReviewSentiment.NEGATIVE, version);

        assertEquals(ReviewStatus.REJECTED, service.get(row.getId()).review().status());
        assertEquals(0, service.publicReviews(product.getId(), PageRequest.of(0, 10)).getTotalElements());
        assertAggregate(product.getId(), 0.0, 0);

        service.moderate(row.getId(), ReviewStatus.APPROVED);
        service.applySentiment(row.getId(), 0, ReviewSentiment.NEGATIVE, version);

        assertEquals(ReviewStatus.APPROVED, service.get(row.getId()).review().status());
        assertEquals(ReviewSentiment.NEGATIVE, service.get(row.getId()).sentiment());
        assertAggregate(product.getId(), 5.0, 1);
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
