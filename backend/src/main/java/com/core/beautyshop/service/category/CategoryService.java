package com.core.beautyshop.service.category;

import com.core.beautyshop.dto.request.CreateCategoryRequest;
import com.core.beautyshop.dto.request.UpdateCategoryRequest;
import com.core.beautyshop.dto.response.CategoryResponse;

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
