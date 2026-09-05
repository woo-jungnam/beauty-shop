package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"brand"})
    Optional<Product> findBySlugAndIsDeletedFalse(String slug);

    @EntityGraph(attributePaths = {"brand"})
    Optional<Product> findByIdAndIsDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"brand"})
    Page<Product> findByIsDeletedFalse(Pageable pageable);

    // DTO Projection: truy vấn trực tiếp ra ProductListResponse, tránh load full entity với các cột TEXT nặng
    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, p.basePrice, " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name) " +
           "FROM Product p LEFT JOIN p.brand b WHERE p.isDeleted = false")
    Page<ProductListResponse> findAllProductList(Pageable pageable);

    // DTO Projection: tìm sản phẩm theo từ khóa
    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, p.basePrice, " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name) " +
           "FROM Product p LEFT JOIN p.brand b " +
           "WHERE p.isDeleted = false AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ProductListResponse> searchProductList(@Param("keyword") String keyword, Pageable pageable);

    // DTO Projection: lọc sản phẩm theo danh mục
    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, p.basePrice, " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name) " +
           "FROM Product p LEFT JOIN p.brand b JOIN p.categories c " +
           "WHERE c.id = :categoryId AND p.isDeleted = false")
    Page<ProductListResponse> findProductListByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    // DTO Projection: lọc sản phẩm theo thương hiệu
    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse(" +
           "p.id, p.name, p.slug, p.shortDescription, p.thumbnailUrl, p.basePrice, " +
           "p.status, p.isFeatured, p.averageRating, p.totalReviews, p.totalSold, b.name) " +
           "FROM Product p LEFT JOIN p.brand b " +
           "WHERE p.brand.id = :brandId AND p.isDeleted = false")
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

    boolean existsBySlug(String slug);
}
