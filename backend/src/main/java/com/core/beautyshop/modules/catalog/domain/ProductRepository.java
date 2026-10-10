package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"brand"})
    Optional<Product> findByIdAndStatusAndIsDeletedFalse(Long id, ProductStatus status);

    @EntityGraph(attributePaths = {"brand"})
    Optional<Product> findBySlugAndStatusAndIsDeletedFalse(String slug, ProductStatus status);

    @EntityGraph(attributePaths = {"brand"})
    Optional<Product> findBySlugAndIsDeletedFalse(String slug);

    @EntityGraph(attributePaths = {"brand"})
    Optional<Product> findByIdAndIsDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT product FROM Product product WHERE product.id = :id AND product.isDeleted = false")
    Optional<Product> findByIdForUpdateAndIsDeletedFalse(@Param("id") Long id);

    @EntityGraph(attributePaths = {"brand"})
    Page<Product> findByIsDeletedFalse(Pageable pageable);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, " +
           "(SELECT MIN(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "(SELECT MAX(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name, b.id, " +
           "(SELECT MIN(c.id) FROM p.categories c)) " +
           "FROM Product p LEFT JOIN p.brand b WHERE p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE")
    Page<ProductListResponse> findAllProductList(Pageable pageable);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, " +
           "(SELECT MIN(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "(SELECT MAX(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name, b.id, " +
           "(SELECT MIN(c.id) FROM p.categories c)) " +
           "FROM Product p LEFT JOIN p.brand b WHERE p.isDeleted = false")
    Page<ProductListResponse> findAllAdminProductList(Pageable pageable);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, " +
           "(SELECT MIN(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "(SELECT MAX(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name, b.id, " +
           "(SELECT MIN(c.id) FROM p.categories c)) " +
           "FROM Product p LEFT JOIN p.brand b WHERE p.isDeleted = false AND p.isFeatured = true " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE")
    Page<ProductListResponse> findFeaturedProductList(Pageable pageable);

    @Query(value = "SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, " +
           "(SELECT MIN(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "(SELECT MAX(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name, b.id, " +
           "(SELECT MIN(c.id) FROM p.categories c)) " +
           "FROM Product p LEFT JOIN p.brand b " +
           "WHERE p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE " +
           "AND EXISTS (SELECT 1 FROM p.categories c WHERE c.id IN :categoryIds AND c.isDeleted = false)",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE " +
           "AND EXISTS (SELECT 1 FROM p.categories c WHERE c.id IN :categoryIds AND c.isDeleted = false)")
    Page<ProductListResponse> findProductListByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds, Pageable pageable);

    default Page<ProductListResponse> findProductListByCategoryId(Long categoryId, Pageable pageable) {
        return findProductListByCategoryIds(List.of(categoryId), pageable);
    }

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, " +
           "(SELECT MIN(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "(SELECT MAX(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name, b.id, " +
           "(SELECT MIN(c.id) FROM p.categories c)) " +
           "FROM Product p LEFT JOIN p.brand b " +
           "WHERE p.brand.id = :brandId AND p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE")
    Page<ProductListResponse> findProductListByBrandId(@Param("brandId") Long brandId, Pageable pageable);

    @EntityGraph(attributePaths = {"brand"})
    Page<Product> findByStatusAndIsDeletedFalse(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"brand"})
    Page<Product> findByBrandIdAndIsDeletedFalse(Long brandId, Pageable pageable);

    @EntityGraph(attributePaths = {"brand"})
    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId AND p.isDeleted = false")
    Page<Product> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"brand"})
    @Query("SELECT p FROM Product p WHERE p.isDeleted = false AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, " +
           "(SELECT MIN(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "(SELECT MAX(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name, b.id, " +
           "(SELECT MIN(c.id) FROM p.categories c)) " +
           "FROM Product p LEFT JOIN p.brand b " +
           "WHERE p.id IN :ids AND p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE")
    List<ProductListResponse> findProductListByIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, " +
           "(SELECT MIN(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "(SELECT MAX(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name, b.id, " +
           "(SELECT MIN(c.id) FROM p.categories c)) " +
           "FROM Product p LEFT JOIN p.brand b " +
           "WHERE p.id != :excludeId AND p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE " +
           "AND (EXISTS (SELECT 1 FROM p.categories c WHERE c.id IN :categoryIds AND c.isDeleted = false) OR p.brand.id = :brandId)")
    List<ProductListResponse> findFallbackSimilarProducts(@Param("excludeId") Long excludeId,
                                                          @Param("categoryIds") Collection<Long> categoryIds,
                                                          @Param("brandId") Long brandId,
                                                          Pageable pageable);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, " +
           "(SELECT MIN(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "(SELECT MAX(COALESCE(v.discountPrice, v.price)) FROM ProductVariant v WHERE v.product = p AND v.isDeleted = false AND v.isActive = true), " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name, b.id, " +
           "(SELECT MIN(c.id) FROM p.categories c)) " +
           "FROM Product p LEFT JOIN p.brand b " +
           "WHERE p.isDeleted = false " +
           "AND p.status = com.core.beautyshop.modules.catalog.domain.enums.ProductStatus.ACTIVE " +
           "ORDER BY p.isFeatured DESC, p.totalSold DESC, p.averageRating DESC")
    List<ProductListResponse> findPopularProducts(Pageable pageable);

    boolean existsBySlug(String slug);
}
