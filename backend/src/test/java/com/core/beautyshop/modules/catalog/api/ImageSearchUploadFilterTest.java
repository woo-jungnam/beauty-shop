package com.core.beautyshop.modules.catalog.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageSearchUploadFilterTest {
    @Test void rejectsOversizeBeforeMultipartAndRejectsUnknownLength() throws Exception {
        var filter = new ImageSearchUploadFilter();
        var request = new MockHttpServletRequest("POST", "/api/v1/products/search-by-image");
        request.setServletPath("/api/v1/products/search-by-image");
        var chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();
        request.setContent(new byte[6000000]);
        filter.doFilter(request, response, chain);
        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);

        request = new MockHttpServletRequest("POST", "/api/v1/products/search-by-image");
        request.setServletPath("/api/v1/products/search-by-image");
        request.addHeader("Transfer-Encoding", "chunked");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(411, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test void permitsBoundedBodyAndDoesNotChangeOtherUploads() throws Exception {
        var filter = new ImageSearchUploadFilter();
        var request = new MockHttpServletRequest("POST", "/api/v1/products/search-by-image");
        request.setServletPath("/api/v1/products/search-by-image");
        request.setContent(new byte[100]);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);

        var other = new MockHttpServletRequest("POST", "/api/v1/media");
        var otherChain = mock(FilterChain.class);
        filter.doFilter(other, new MockHttpServletResponse(), otherChain);
        verify(otherChain).doFilter(eq(other), any());
    }
}
