package com.core.beautyshop.modules.catalog.application.dto.request;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BannerRequestTest {
    @Test void requiresAnImageAndDestinationAndRejectsExecutableOrAmbiguousLinks() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = BannerRequest.builder().title("Banner name").imageUrl("/uploads/banner.webp").build();
            assertFalse(validator.validate(request).isEmpty());
            for (String target : new String[]{"products?categoryId=3", "#/booking", "/#/treatments", "https://example.com/deals", "#flash-sale"}) {
                request.setTargetUrl(target);
                assertTrue(validator.validate(request).isEmpty(), target);
            }
            for (String target : new String[]{"javascript:alert(1)", "data:text/html,hello", "//example.com", "https://", "products deals", "#"}) {
                request.setTargetUrl(target);
                assertFalse(validator.validate(request).isEmpty(), target);
            }
            request.setTargetUrl("products"); request.setImageUrl("");
            assertFalse(validator.validate(request).isEmpty());
        }
    }
}
