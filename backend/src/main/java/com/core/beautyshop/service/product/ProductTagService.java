package com.core.beautyshop.service.product;

import com.core.beautyshop.dto.request.TagRequest;
import com.core.beautyshop.dto.response.TagResponse;
import java.util.List;

public interface ProductTagService {
    List<TagResponse> getAllTags();
    TagResponse getTagById(Long id);
    TagResponse createTag(TagRequest request);
    void deleteTag(Long id);
}