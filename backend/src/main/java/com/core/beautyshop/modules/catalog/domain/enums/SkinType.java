package com.core.beautyshop.modules.catalog.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SkinType {
    ALL_SKIN,
    ALL,
    OILY,
    DRY,
    COMBINATION,
    SENSITIVE,
    NORMAL;

    @JsonCreator
    public static SkinType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("ALL".equals(normalized)) {
            return ALL_SKIN;
        }
        return SkinType.valueOf(normalized);
    }
}
