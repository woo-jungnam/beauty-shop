package com.core.beautyshop.modules.review.domain.enums;

public enum ReviewSentiment {
    PENDING,
    POSITIVE,
    NEGATIVE,
    FAILED;

    public boolean isLabel() {
        return this == POSITIVE || this == NEGATIVE;
    }
}
