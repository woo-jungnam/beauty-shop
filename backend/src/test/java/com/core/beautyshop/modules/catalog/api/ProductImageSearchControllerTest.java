package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.service.ProductImageSearchService;
import com.core.beautyshop.modules.catalog.infrastructure.ImageSearchException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductImageSearchControllerTest {
    @Test void multipartEndpointWrapsResultsAndMapsServiceFailure() throws Exception {
        var service = mock(ProductImageSearchService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProductImageSearchController(service)).build();
        when(service.search(any())).thenReturn(new ProductImageSearchService.SearchResult("EMPTY", "v1", List.of()));
        mvc.perform(multipart("/api/v1/products/search-by-image").file(new MockMultipartFile("image", new byte[]{1})))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.matchStatus").value("EMPTY"));
        when(service.search(any())).thenThrow(new ImageSearchException(503, "Chưa sẵn sàng"));
        mvc.perform(multipart("/api/v1/products/search-by-image").file(new MockMultipartFile("image", new byte[]{1})))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("IMAGE_SEARCH_503"));
    }
}
