package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlugAndIsDeletedFalse(String slug);

    Optional<Product> findByIdAndIsDeletedFalse(Long id);

    Page<Product> findByIsDeletedFalse(Pageable pageable);

    Page<Product> findByStatusAndIsDeletedFalse(ProductStatus status, Pageable pageable);

    Page<Product> findByBrandIdAndIsDeletedFalse(Long brandId, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId AND p.isDeleted = false")
    Page<Product> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isDeleted = false AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    boolean existsBySlug(String slug);
}
