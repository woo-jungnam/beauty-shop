package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.domain.ProductSearchRepository;
import com.core.beautyshop.modules.catalog.domain.ProductImageRepository;
import com.core.beautyshop.modules.catalog.domain.ProductImage;
import com.core.beautyshop.modules.catalog.domain.Product;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchClient;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductImageSearchServiceTest {
    @Test void keepsRankOrderFiltersUnavailableIdsAndNeverBackfills() {
        var client = mock(ImageSearchClient.class);
        var repository = mock(ProductSearchRepository.class);
        var service = new ProductImageSearchService(client, repository, mock(ProductImageRepository.class));
        var one = ProductListResponse.builder().id(1L).thumbnailUrl("/uploads/one.jpg").build();
        var three = ProductListResponse.builder().id(3L).thumbnailUrl("/uploads/three.jpg").build();
        when(repository.findAvailableByIds(anyCollection(), any())).thenReturn(List.of(one, three));
        var result = service.resolve(new ImageSearchClient.Result("success", "CANDIDATES", "v1", List.of(
                new ImageSearchClient.Candidate(3L, -3L, .9),
                new ImageSearchClient.Candidate(2L, -2L, .8),
                new ImageSearchClient.Candidate(1L, -1L, .7))));
        assertEquals(List.of(3L, 1L), result.items().stream().map(i -> i.product().getId()).toList());
        assertTrue(result.items().getFirst().inStock());
        assertEquals("product/3", result.items().getFirst().detailPath());
        verify(repository).findAvailableByIds(eq(List.of(3L, 2L, 1L)), any());
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(client);
    }

    @Test void noAvailableProductsReturnsEmpty() {
        var repository = mock(ProductSearchRepository.class);
        when(repository.findAvailableByIds(anyCollection(), any())).thenReturn(List.of());
        var service = new ProductImageSearchService(mock(ImageSearchClient.class), repository, mock(ProductImageRepository.class));
        assertEquals("EMPTY", service.resolve(new ImageSearchClient.Result("success", "CANDIDATES", "v1",
                List.of(new ImageSearchClient.Candidate(1L, 10L, .5)))).matchStatus());
    }

    @Test void rejectsEmptyUnsupportedAndOversizedFilesBeforeCallingClient() {
        var client = mock(ImageSearchClient.class);
        var service = new ProductImageSearchService(client, mock(ProductSearchRepository.class), mock(ProductImageRepository.class));
        assertEquals(400, assertThrows(ImageSearchException.class, () -> service.search(
                new MockMultipartFile("image", new byte[0]))).status());
        assertEquals(415, assertThrows(ImageSearchException.class, () -> service.search(
                new MockMultipartFile("image", "bad".getBytes()))).status());
        assertEquals(413, assertThrows(ImageSearchException.class, () -> service.search(
                new MockMultipartFile("image", new byte[ProductImageSearchService.MAX_BYTES + 1]))).status());
        verifyNoInteractions(client);
    }

    @Test void usesMatchedUploadInsteadOfExternalThumbnailAndSkipsExternalPhoto() {
        var repository = mock(ProductSearchRepository.class);
        var photos = mock(ProductImageRepository.class);
        var service = new ProductImageSearchService(mock(ImageSearchClient.class), repository, photos);
        var product = ProductListResponse.builder().id(5L).thumbnailUrl("https://images.unsplash.com/old.jpg").build();
        var parent = new Product();
        parent.setId(5L);
        var image = new ProductImage();
        image.setId(568L);
        image.setProduct(parent);
        image.setImageUrl("/uploads/current.png");
        image.setIsDeleted(false);
        when(repository.findAvailableByIds(anyCollection(), any())).thenReturn(List.of(product));
        when(photos.findAllById(any())).thenReturn(List.of(image));
        var candidates = new ImageSearchClient.Result("success", "CANDIDATES", "v1",
                List.of(new ImageSearchClient.Candidate(5L, 568L, .9)));
        assertEquals("/uploads/current.png", service.resolve(candidates).items().getFirst().product().getThumbnailUrl());
        image.setImageUrl("https://images.unsplash.com/replaced.jpg");
        assertEquals("EMPTY", service.resolve(candidates).matchStatus());
    }
}
