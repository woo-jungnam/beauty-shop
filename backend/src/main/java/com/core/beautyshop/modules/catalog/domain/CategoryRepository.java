package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
