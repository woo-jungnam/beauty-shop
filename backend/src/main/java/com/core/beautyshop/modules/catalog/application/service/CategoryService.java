package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateCategoryRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateCategoryRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getAllCategories();

    List<CategoryResponse> getRootCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse getCategoryBySlug(String slug);

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);

    void deleteCategory(Long id);
}
