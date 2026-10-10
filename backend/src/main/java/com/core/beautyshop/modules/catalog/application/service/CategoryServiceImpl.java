package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.CreateCategoryRequest;
import com.core.beautyshop.modules.catalog.application.dto.request.UpdateCategoryRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.CategoryResponse;
import com.core.beautyshop.modules.catalog.domain.Category;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.catalog.domain.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "'all'")
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllCategoryDtoList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "'roots'")
    public List<CategoryResponse> getRootCategories() {
        List<CategoryResponse> allCategories = categoryRepository.findAllCategoryDtoList();

        Map<Long, List<CategoryResponse>> childrenByParentId = allCategories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(CategoryResponse::getParentId));

        return allCategories.stream()
                .filter(c -> c.getParentId() == null)
                .peek(root -> attachChildren(root, childrenByParentId, new HashSet<>()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "'id:' + #id")
    public CategoryResponse getCategoryById(Long id) {
        CategoryResponse category = categoryRepository.findCategoryDtoById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với id: " + id));
        
        attachChildren(category, childrenByParentId(), new HashSet<>());
        return category;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "'slug:' + #slug")
    public CategoryResponse getCategoryBySlug(String slug) {
        CategoryResponse category = categoryRepository.findCategoryDtoBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với slug: " + slug));
        
        attachChildren(category, childrenByParentId(), new HashSet<>());
        return category;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        categoryRepository.findAllForHierarchyUpdate();
        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new BusinessException("Slug danh mục đã tồn tại: " + request.getSlug());
        }

        Category category = Category.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        if (request.getParentId() != null) {
            Category parent = categoryRepository.findByIdAndIsDeletedFalse(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục cha với id: " + request.getParentId()));
            category.setParentCategory(parent);
        }

        Category saved = categoryRepository.save(category);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        // Tree mutations share the same ordered locks, so concurrent A->B and B->A
        // changes cannot both validate against the old hierarchy.
        categoryRepository.findAllForHierarchyUpdate();
        Category category = categoryRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với id: " + id));

        if (request.getName() != null) category.setName(request.getName());
        if (request.getSlug() != null) {
            if (!category.getSlug().equals(request.getSlug()) && categoryRepository.existsBySlug(request.getSlug())) {
                throw new BusinessException("Slug danh mục đã tồn tại: " + request.getSlug());
            }
            category.setSlug(request.getSlug());
        }
        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getImageUrl() != null) category.setImageUrl(request.getImageUrl());
        if (request.getDisplayOrder() != null) category.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) category.setIsActive(request.getIsActive());

        if (request.isParentIdSpecified() && request.getParentId() == null) {
            category.setParentCategory(null);
        }
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new BusinessException("Danh mục không thể là danh mục cha của chính nó");
            }
            Category parent = categoryRepository.findByIdAndIsDeletedFalse(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục cha với id: " + request.getParentId()));
            validateParentDoesNotCreateCycle(category, parent);
            category.setParentCategory(parent);
        }

        Category saved = categoryRepository.save(category);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "products_page", allEntries = true),
            @CacheEvict(value = "product_detail", allEntries = true)
    })
    public void deleteCategory(Long id) {
        categoryRepository.findAllForHierarchyUpdate();
        Category category = categoryRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với id: " + id));

        if (categoryRepository.existsByParentCategoryIdAndIsDeletedFalse(id)) {
            throw new BusinessException("Không thể xóa danh mục đang có danh mục con hoạt động");
        }

        category.setIsDeleted(true);
        categoryRepository.save(category);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .parentId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
                .parentName(category.getParentCategory() != null ? category.getParentCategory().getName() : null)
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .build();
    }

    private void validateParentDoesNotCreateCycle(Category category, Category proposedParent) {
        Set<Long> visited = new HashSet<>();
        Category current = proposedParent;

        while (current != null) {
            if (java.util.Objects.equals(category.getId(), current.getId())) {
                throw new BusinessException("Không thể chọn danh mục con làm danh mục cha");
            }
            if (current.getId() != null && !visited.add(current.getId())) {
                throw new BusinessException("Cây danh mục hiện có chu trình không hợp lệ");
            }
            current = current.getParentCategory();
        }
    }

    private Map<Long, List<CategoryResponse>> childrenByParentId() {
        return categoryRepository.findAllCategoryDtoList().stream().filter(row -> row.getParentId() != null)
                .collect(Collectors.groupingBy(CategoryResponse::getParentId));
    }

    private void attachChildren(CategoryResponse category, Map<Long, List<CategoryResponse>> children, Set<Long> ancestors) {
        if (!ancestors.add(category.getId())) throw new BusinessException("Cây danh mục hiện có chu trình không hợp lệ");
        List<CategoryResponse> rows = children.getOrDefault(category.getId(), List.of());
        for (CategoryResponse child : rows) attachChildren(child, children, ancestors);
        category.setChildren(new ArrayList<>(rows));
        ancestors.remove(category.getId());
    }
}
