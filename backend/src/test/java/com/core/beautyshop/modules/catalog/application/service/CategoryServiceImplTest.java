package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.domain.Category;
import com.core.beautyshop.modules.catalog.domain.CategoryRepository;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateCategoryRequest;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import com.core.beautyshop.modules.catalog.application.dto.response.CategoryResponse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private CategoryServiceImpl categoryService;

    @Test
    void deleteCategoryRejectsParentWithActiveChildren() {
        Category parent = Category.builder().name("Parent").slug("parent").build();
        parent.setId(1L);

        when(categoryRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(1L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> categoryService.deleteCategory(1L));

        verify(categoryRepository, never()).save(parent);
    }

    @Test
    void updateCategoryRejectsUsingDescendantAsParent() {
        Category category = Category.builder().name("Parent").slug("parent").build();
        category.setId(1L);
        Category child = Category.builder()
                .name("Child")
                .slug("child")
                .parentCategory(category)
                .build();
        child.setId(2L);
        UpdateCategoryRequest request = UpdateCategoryRequest.builder().parentId(2L).build();

        when(categoryRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(child));

        assertThrows(BusinessException.class, () -> categoryService.updateCategory(1L, request));

        verify(categoryRepository, never()).save(category);
    }

    @Test
    void updateCategoryAllowsExplicitNullToDetachParent() {
        Category parent = Category.builder().name("Parent").slug("parent").build();
        parent.setId(1L);
        Category child = Category.builder().name("Child").slug("child").parentCategory(parent).build();
        child.setId(2L);
        UpdateCategoryRequest request = new UpdateCategoryRequest();
        request.setParentId(null);
        when(categoryRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(child));
        when(categoryRepository.save(child)).thenReturn(child);

        categoryService.updateCategory(2L, request);

        assertNull(child.getParentCategory());
        verify(categoryRepository).save(child);
    }

    @Test
    void rootTreeIncludesGrandchildren() {
        CategoryResponse root = CategoryResponse.builder().id(1L).name("Root").build();
        CategoryResponse child = CategoryResponse.builder().id(2L).parentId(1L).name("Child").build();
        CategoryResponse grandchild = CategoryResponse.builder().id(3L).parentId(2L).name("Grandchild").build();
        when(categoryRepository.findAllCategoryDtoList()).thenReturn(List.of(root, child, grandchild));
        var result = categoryService.getRootCategories();
        assertEquals(3L, result.getFirst().getChildren().getFirst().getChildren().getFirst().getId());
    }
}
