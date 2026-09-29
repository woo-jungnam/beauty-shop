package com.core.beautyshop.modules.review.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.review.domain.*;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTest {
    @Mock ProductReviewRepository reviews;
    @Mock CatalogFacade catalog;
    @Mock OrderFacade orders;
    @InjectMocks ProductReviewService service;

    @Test
    void approvingReviewRefreshesProductAggregate() {
        ProductReview review = ProductReview.builder().productId(7L).userId(3L).orderId(9L).rating(5)
                .content("Great").status(ReviewStatus.PENDING).build();
        review.setId(1L);
        when(reviews.findById(1L)).thenReturn(Optional.of(review));
        when(reviews.summarizeApproved(7L)).thenReturn(new Object[]{4.5d, 2L});

        var result = service.moderate(1L, ReviewStatus.APPROVED);

        assertEquals(ReviewStatus.APPROVED, result.status());
        verify(catalog).updateProductRating(7L, 4.5d, 2);
    }
}
