package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.application.dto.response.CategoryResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlugAndIsDeletedFalse(String slug);

    Optional<Category> findByIdAndIsDeletedFalse(Long id);

    List<Category> findByParentCategoryIsNullAndIsDeletedFalseOrderByDisplayOrderAsc();

    List<Category> findByParentCategoryIdAndIsDeletedFalseOrderByDisplayOrderAsc(Long parentId);

    List<Category> findByIsDeletedFalseOrderByDisplayOrderAsc();

    boolean existsBySlug(String slug);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.CategoryResponse(" +
           "c.id, c.name, c.slug, c.description, c.imageUrl, " +
           "p.id, p.name, c.displayOrder, c.isActive) " +
           "FROM Category c LEFT JOIN c.parentCategory p " +
           "WHERE c.isDeleted = false ORDER BY c.displayOrder ASC")
    List<CategoryResponse> findAllCategoryDtoList();

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.CategoryResponse(" +
           "c.id, c.name, c.slug, c.description, c.imageUrl, " +
           "p.id, p.name, c.displayOrder, c.isActive) " +
           "FROM Category c LEFT JOIN c.parentCategory p " +
           "WHERE c.id = :id AND c.isDeleted = false")
    Optional<CategoryResponse> findCategoryDtoById(@Param("id") Long id);

    @Query("SELECT new com.core.beautyshop.modules.catalog.application.dto.response.CategoryResponse(" +
           "c.id, c.name, c.slug, c.description, c.imageUrl, " +
           "p.id, p.name, c.displayOrder, c.isActive) " +
           "FROM Category c LEFT JOIN c.parentCategory p " +
           "WHERE c.slug = :slug AND c.isDeleted = false")
    Optional<CategoryResponse> findCategoryDtoBySlug(@Param("slug") String slug);
}

