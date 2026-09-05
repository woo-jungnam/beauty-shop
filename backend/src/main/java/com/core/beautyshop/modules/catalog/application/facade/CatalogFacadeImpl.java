package com.core.beautyshop.modules.catalog.application.facade;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CatalogFacadeImpl implements CatalogFacade {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public Optional<ProductVariantSummaryDto> findVariantSummaryById(Long variantId) {
        if (variantId == null) {
            return Optional.empty();
        }
        return productVariantRepository.findVariantSummaryByIdDto(variantId);
    }

    @Override
    public ProductVariantSummaryDto getVariantSummaryById(Long variantId) {
        if (variantId == null) {
            throw new ResourceNotFoundException("ID biến thể không được để trống");
        }
        return productVariantRepository.findVariantSummaryByIdDto(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm với id: " + variantId));
    }

    @Override
    public Map<Long, ProductVariantSummaryDto> getVariantSummariesByIds(Collection<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productVariantRepository.findVariantSummariesByIds(variantIds).stream()
                .collect(Collectors.toMap(ProductVariantSummaryDto::getId, dto -> dto, (existing, replacing) -> existing));
    }

    @Override
    public boolean variantExistsById(Long variantId) {
        if (variantId == null) {
            return false;
        }
        return productVariantRepository.findByIdAndIsDeletedFalse(variantId).isPresent();
    }
}
