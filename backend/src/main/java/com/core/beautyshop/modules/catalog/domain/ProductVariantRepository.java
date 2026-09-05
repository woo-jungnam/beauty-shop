package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);
    boolean existsBySku(String sku);
    List<ProductVariant> findByProductIdAndIsDeletedFalse(Long productId);
    Optional<ProductVariant> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse(" +
           "v.id, v.product.id, v.sku, v.variantName, v.price, v.discountPrice, v.volume, v.color, v.barcode, " +
           "v.isDefault, v.isActive, v.createdAt, v.updatedAt) " +
           "FROM ProductVariant v WHERE v.product.id = :productId AND v.isDeleted = false")
    List<ProductVariantResponse> findVariantResponsesByProductId(@Param("productId") Long productId);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse(" +
           "v.id, v.product.id, v.sku, v.variantName, v.price, v.discountPrice, v.volume, v.color, v.barcode, " +
           "v.isDefault, v.isActive, v.createdAt, v.updatedAt) " +
           "FROM ProductVariant v WHERE v.id = :id AND v.isDeleted = false")
    Optional<ProductVariantResponse> findVariantResponseById(@Param("id") Long id);

    @Query("SELECT new com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto(" +
           "v.id, p.id, p.name, v.sku, v.variantName, v.price, v.discountPrice, v.isActive) " +
           "FROM ProductVariant v JOIN v.product p " +
           "WHERE v.id IN :variantIds AND v.isDeleted = false")
    List<ProductVariantSummaryDto> findVariantSummariesByIds(@Param("variantIds") Collection<Long> variantIds);

    @Query("SELECT new com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto(" +
           "v.id, p.id, p.name, v.sku, v.variantName, v.price, v.discountPrice, v.isActive) " +
           "FROM ProductVariant v JOIN v.product p " +
           "WHERE v.id = :variantId AND v.isDeleted = false")
    Optional<ProductVariantSummaryDto> findVariantSummaryByIdDto(@Param("variantId") Long variantId);
}
