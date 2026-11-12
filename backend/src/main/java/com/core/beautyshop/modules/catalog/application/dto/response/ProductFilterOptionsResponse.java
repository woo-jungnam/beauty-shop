package com.core.beautyshop.modules.catalog.application.dto.response;

import java.util.List;

public record ProductFilterOptionsResponse(
        List<SkinConcernOption> skinConcerns,
        List<String> origins
) {
    public record SkinConcernOption(Long id, String code, String name) {}
}
