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

    @Test
    void deleteTagPerformsSoftDelete() {
        ProductTagRepository repository = mock(ProductTagRepository.class);
        ProductTag tag = ProductTag.builder().name("Dưỡng ẩm").slug("duong-am").build();
        tag.setId(10L);
        when(repository.findByIdAndIsDeletedFalse(10L)).thenReturn(java.util.Optional.of(tag));
        when(repository.save(any(ProductTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductTagServiceImpl service = new ProductTagServiceImpl(repository);
        service.deleteTag(10L);

        assertEquals(true, tag.getIsDeleted());
    }
}
