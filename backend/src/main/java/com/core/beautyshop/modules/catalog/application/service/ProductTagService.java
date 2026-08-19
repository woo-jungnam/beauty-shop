package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.TagRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.TagResponse;
import java.util.List;

public interface ProductTagService {
    List<TagResponse> getAllTags();
    TagResponse getTagById(Long id);
    TagResponse createTag(TagRequest request);
    void deleteTag(Long id);
}
