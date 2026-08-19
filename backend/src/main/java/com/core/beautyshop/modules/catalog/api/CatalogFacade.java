package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import java.util.Optional;

public interface CatalogFacade {
    Optional<ProductVariantSummaryDto> findVariantSummaryById(Long variantId);
    ProductVariantSummaryDto getVariantSummaryById(Long variantId);
    boolean variantExistsById(Long variantId);
}
