package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.request.TagRequest;
import com.core.beautyshop.modules.catalog.domain.ProductTag;
import com.core.beautyshop.modules.catalog.domain.ProductTagRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductTagServiceImplTest {

    @Test
    void vietnameseDStrokeIsPreservedInSlug() {
        ProductTagRepository repository = mock(ProductTagRepository.class);
        when(repository.save(any(ProductTag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductTagServiceImpl service = new ProductTagServiceImpl(repository);
        TagRequest request = new TagRequest();
        request.setName("Đặc trị mụn");

        var response = service.createTag(request);

        assertEquals("dac-tri-mun", response.getSlug());
    }
}
