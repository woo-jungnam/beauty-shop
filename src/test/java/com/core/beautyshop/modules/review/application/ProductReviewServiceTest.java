package com.core.beautyshop.modules.review.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.order.api.OrderFacade;
import com.core.beautyshop.modules.review.domain.*;
import com.core.beautyshop.modules.review.domain.enums.ReviewStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTest {
    @Mock ProductReviewRepository reviews;
    @Mock CatalogFacade catalog;
    @Mock OrderFacade orders;
    @InjectMocks ProductReviewService service;

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void commentWithoutPurchaseHasNoRating() {
        asCustomer(3L);
        when(catalog.productExistsById(7L)).thenReturn(true);
        when(reviews.save(any(ProductReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new ProductReviewService.ReviewCommand(7L, null, 5, "Title", "Comment only"));

        assertNull(result.orderId());
        assertNull(result.rating());
        assertFalse(result.isVerifiedPurchase());
        verifyNoInteractions(orders);
    }

    @Test
    void ratingRequiresDeliveredPurchase() {
        asCustomer(3L);
        when(catalog.productExistsById(7L)).thenReturn(true);
        when(orders.isDeliveredProductPurchase(9L, 3L, 7L)).thenReturn(true);
        when(reviews.save(any(ProductReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new ProductReviewService.ReviewCommand(7L, 9L, 5, null, "Rated"));

        assertEquals(9L, result.orderId());
        assertEquals(5, result.rating());
        assertTrue(result.isVerifiedPurchase());
    }

    @Test
    void ratingWithoutPurchaseIsRejected() {
        asCustomer(3L);
        when(catalog.productExistsById(7L)).thenReturn(true);
        when(orders.isDeliveredProductPurchase(9L, 3L, 7L)).thenReturn(false);

        assertThrows(com.core.beautyshop.shared.exception.BusinessException.class,
                () -> service.create(new ProductReviewService.ReviewCommand(7L, 9L, 5, null, "Rated")));
    }

    @Test
    void approvingReviewRefreshesProductAggregate() {
        ProductReview review = ProductReview.builder().productId(7L).userId(3L).orderId(9L).rating(5)
                .content("Great").status(ReviewStatus.PENDING).build();
        review.setId(1L);
        when(reviews.findProductIdForReview(1L)).thenReturn(Optional.of(7L));
        when(reviews.findByIdForUpdate(1L)).thenReturn(Optional.of(review));
        when(reviews.summarizeApproved(7L)).thenReturn(new ApprovedReviewSummary(4.5d, 2L));

        var result = service.moderate(1L, ReviewStatus.APPROVED);

        assertEquals(ReviewStatus.APPROVED, result.status());
        verify(catalog).updateProductRating(7L, 4.5d, 2);
    }

    private void asCustomer(Long userId) {
        var principal = new com.core.beautyshop.shared.security.services.UserDetailsImpl(
                userId, "customer", "customer@example.test", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
