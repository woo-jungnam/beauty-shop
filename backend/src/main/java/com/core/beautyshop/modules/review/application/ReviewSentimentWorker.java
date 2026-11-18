package com.core.beautyshop.modules.review.application;

import com.core.beautyshop.modules.review.domain.ProductReview;
import com.core.beautyshop.modules.review.domain.ProductReviewRepository;
import com.core.beautyshop.modules.review.domain.enums.ReviewSentiment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "review.sentiment.enabled", havingValue = "true")
@Slf4j
public class ReviewSentimentWorker {
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 5;

    private final ProductReviewRepository repository;
    private final RestClient client;

    public ReviewSentimentWorker(ProductReviewRepository repository,
            @Value("${review.sentiment.url:http://review-sentiment:8002}") String serviceUrl) {
        this.repository = repository;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        this.client = RestClient.builder().baseUrl(serviceUrl).requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setContentLength(body.length);
                    return execution.execute(request, body);
                })
                .build();
    }

    @Scheduled(fixedDelayString = "${review.sentiment.poll-delay-ms:5000}")
    public void analyzePendingReviews() {
        // The committed review row is also the durable job, including for historical reviews.
        List<ProductReview> reviews = repository.findSentimentBatch(
                ReviewSentiment.PENDING, Instant.now(), PageRequest.of(0, BATCH_SIZE));
        if (reviews.isEmpty()) {
            return;
        }

        Prediction prediction;
        try {
            prediction = client.post().uri("/predict")
                    .body(new PredictionRequest(reviews.stream().map(ProductReview::getContent).toList()))
                    .retrieve().body(Prediction.class);
            if (!isValid(prediction, reviews.size())) {
                throw new RestClientException("Invalid review sentiment prediction");
            }
        } catch (RestClientException exception) {
            log.warn("Review sentiment request failed for {} reviews ({})",
                    reviews.size(), exception.getClass().getSimpleName());
            Instant now = Instant.now();
            for (ProductReview review : reviews) {
                int attempt = review.getSentimentAttempts() + 1;
                boolean exhausted = attempt >= MAX_ATTEMPTS;
                repository.completeSentimentAttempt(review.getId(), review.getSentimentAttempts(), ReviewSentiment.PENDING,
                        exhausted ? ReviewSentiment.FAILED : ReviewSentiment.PENDING,
                        exhausted ? null : now.plusSeconds(30L * attempt), null, null);
            }
            return;
        }

        Instant analyzedAt = Instant.now();
        for (int index = 0; index < reviews.size(); index++) {
            ProductReview review = reviews.get(index);
            repository.completeSentimentAttempt(review.getId(), review.getSentimentAttempts(), ReviewSentiment.PENDING,
                    prediction.results().get(index).label(), null, prediction.modelVersion(), analyzedAt);
        }
    }

    private static boolean isValid(Prediction prediction, int expectedSize) {
        return prediction != null && prediction.modelVersion() != null
                && prediction.modelVersion().matches("[a-f0-9]{64}")
                && prediction.results() != null && prediction.results().size() == expectedSize
                && prediction.results().stream().allMatch(result ->
                        result != null && result.label() != null && result.label().isLabel() && Double.isFinite(result.score()));
    }

    private record PredictionRequest(List<String> texts) {}
    private record Prediction(String modelVersion, List<Result> results) {}
    private record Result(ReviewSentiment label, double score) {}
}
