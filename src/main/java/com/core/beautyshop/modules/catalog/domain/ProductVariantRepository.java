package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @Query("SELECT v.product.id FROM ProductVariant v WHERE v.id = :id AND v.isDeleted = false AND v.product.isDeleted = false")
    Optional<Long> findProductIdByVariantId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id AND v.isDeleted = false AND v.product.isDeleted = false")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT new com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto(" +
            "v.id, p.id, p.name, v.sku, v.variantName, v.price, v.discountPrice, v.isActive, p.thumbnailUrl) " +
            "FROM ProductVariant v JOIN v.product p WHERE v.id IN :variantIds AND v.isDeleted = false AND p.isDeleted = false")
    List<ProductVariantSummaryDto> findVariantSummariesForInventory(@Param("variantIds") Collection<Long> variantIds);

    @Query("SELECT new com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto(" +
            "v.id, p.id, p.name, v.sku, v.variantName, v.price, v.discountPrice, v.isActive, p.thumbnailUrl) " +
            "FROM ProductVariant v JOIN v.product p WHERE v.id = :variantId AND v.isDeleted = false AND p.isDeleted = false")
    Optional<ProductVariantSummaryDto> findVariantSummaryForInventory(@Param("variantId") Long variantId);
    Optional<ProductVariant> findBySku(String sku);
    boolean existsBySku(String sku);
    @Query("SELECT v FROM ProductVariant v JOIN v.product p " +
            "WHERE p.id = :productId AND v.isDeleted = false AND p.isDeleted = false")
    List<ProductVariant> findByProductIdAndIsDeletedFalse(@Param("productId") Long productId);

    @Query("SELECT v FROM ProductVariant v JOIN v.product p " +
            "WHERE v.id = :id AND v.isDeleted = false AND p.isDeleted = false")
    Optional<ProductVariant> findByIdAndIsDeletedFalse(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductVariant variant SET variant.isDefault = false " +
            "WHERE variant.product.id = :productId AND variant.isDefault = true")
    int clearDefaultsForProduct(@Param("productId") Long productId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE ProductVariant variant SET variant.isDefault = false " +
            "WHERE variant.product.id = :productId AND variant.id <> :variantId AND variant.isDefault = true")
    int clearOtherDefaultsForProduct(
            @Param("productId") Long productId,
            @Param("variantId") Long variantId);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse(" +
           "v.id, v.product.id, v.sku, v.variantName, v.price, v.discountPrice, v.volume, v.color, v.barcode, " +
           "v.isDefault, v.isActive, v.createdAt, v.updatedAt) " +
           "FROM ProductVariant v JOIN v.product p " +
           "WHERE p.id = :productId AND v.isDeleted = false AND v.isActive = true AND p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE")
    List<ProductVariantResponse> findVariantResponsesByProductId(@Param("productId") Long productId);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductVariantResponse(" +
           "v.id, v.product.id, v.sku, v.variantName, v.price, v.discountPrice, v.volume, v.color, v.barcode, " +
           "v.isDefault, v.isActive, v.createdAt, v.updatedAt) " +
           "FROM ProductVariant v JOIN v.product p " +
           "WHERE v.id = :id AND v.isDeleted = false AND p.isDeleted = false")
    Optional<ProductVariantResponse> findVariantResponseById(@Param("id") Long id);

    @Query("SELECT new com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto(" +
           "v.id, p.id, p.name, v.sku, v.variantName, v.price, v.discountPrice, v.isActive, p.thumbnailUrl) " +
           "FROM ProductVariant v JOIN v.product p " +
           "WHERE v.id IN :variantIds " +
           "AND v.isDeleted = false AND v.isActive = true " +
           "AND p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE")
    List<ProductVariantSummaryDto> findVariantSummariesByIds(@Param("variantIds") Collection<Long> variantIds);

    @Query("SELECT new com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto(" +
           "v.id, p.id, p.name, v.sku, v.variantName, v.price, v.discountPrice, v.isActive, p.thumbnailUrl) " +
           "FROM ProductVariant v JOIN v.product p " +
           "WHERE v.id = :variantId " +
           "AND v.isDeleted = false AND v.isActive = true " +
           "AND p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE")
    Optional<ProductVariantSummaryDto> findVariantSummaryByIdDto(@Param("variantId") Long variantId);

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p LEFT JOIN FETCH p.brand b " +
           "WHERE v.id IN :variantIds AND v.isDeleted = false AND v.isActive = true " +
           "AND p.isDeleted = false AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE")
    List<ProductVariant> findActiveVariantsWithProductAndBrandByIds(@Param("variantIds") Collection<Long> variantIds);
}
