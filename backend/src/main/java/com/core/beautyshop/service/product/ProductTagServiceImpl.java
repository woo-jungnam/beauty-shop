package com.core.beautyshop.service.product;

import com.core.beautyshop.dto.request.TagRequest;
import com.core.beautyshop.dto.response.TagResponse;
import com.core.beautyshop.entities.product.ProductTag;
import com.core.beautyshop.exception.BusinessException;
import com.core.beautyshop.exception.ResourceNotFoundException;
import com.core.beautyshop.repository.ProductTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.text.Normalizer;

@Service
@RequiredArgsConstructor
public class ProductTagServiceImpl implements ProductTagService {

    private final ProductTagRepository tagRepository;

    @Override
    public List<TagResponse> getAllTags() {
        return tagRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TagResponse getTagById(Long id) {
        ProductTag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ với id: " + id));
        return mapToResponse(tag);
    }

    @Override
    @Transactional
    public TagResponse createTag(TagRequest request) {
        String slug = generateSlug(request.getName());
        
        if (tagRepository.existsBySlug(slug)) {
            throw new BusinessException("Slug thẻ đã tồn tại: " + slug);
        }

        ProductTag tag = ProductTag.builder()
                .name(request.getName())
                .slug(slug)
                .build();
        tag = tagRepository.save(tag);
        return mapToResponse(tag);
    }

    @Override
    @Transactional
    public void deleteTag(Long id) {
        if (!tagRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy thẻ với id: " + id);
        }
        tagRepository.deleteById(id);
    }

    private String generateSlug(String input) {
        String slug = Normalizer.normalize(input, Normalizer.Form.NFD);
        slug = slug.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        slug = slug.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
        return slug.replaceAll("^-|-$", "");
    }

    private TagResponse mapToResponse(ProductTag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .slug(tag.getSlug())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }
}