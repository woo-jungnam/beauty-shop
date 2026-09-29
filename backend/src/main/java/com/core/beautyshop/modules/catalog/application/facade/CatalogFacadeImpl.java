package com.core.beautyshop.modules.catalog.application.facade;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CatalogFacadeImpl implements CatalogFacade {

    private final ProductVariantRepository productVariantRepository;
    private final com.core.beautyshop.modules.catalog.domain.ProductRepository productRepository;

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

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void applyDiscountPrice(Long variantId, java.math.BigDecimal discountPrice) {
        if (variantId != null) {
            productVariantRepository.findById(variantId).ifPresent(variant -> {
                if (discountPrice != null
                        && (discountPrice.signum() < 0 || discountPrice.compareTo(variant.getPrice()) > 0)) {
                    throw new BusinessException("Giá khuyến mãi phải nằm trong khoảng từ 0 đến giá gốc");
                }
                variant.setDiscountPrice(discountPrice);
                productVariantRepository.save(variant);
            });
        }
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void deactivateVariant(Long variantId) {
        if (variantId != null) {
            productVariantRepository.findById(variantId).ifPresent(variant -> {
                variant.setIsActive(false);
                productVariantRepository.save(variant);
            });
        }
    }

    @Override
    public boolean productExistsById(Long productId) {
        return productId != null && productRepository.findByIdAndIsDeletedFalse(productId).isPresent();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void updateProductRating(Long productId, double averageRating, int totalReviews) {
        productRepository.findByIdForUpdateAndIsDeletedFalse(productId).ifPresent(product -> {
            product.setAverageRating(averageRating);
            product.setTotalReviews(totalReviews);
        });
    }
}
