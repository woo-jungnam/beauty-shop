package com.core.beautyshop.modules.catalog.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ImageSearchClientTest {
    @Test void validatesResponseAndRejectsDuplicateIdsAndNonFiniteScores() {
        var candidate = new ImageSearchClient.Candidate(1L, -1L, .8);
        assertEquals(1, ImageSearchClient.validate(new ImageSearchClient.Result("success", "CANDIDATES", "v1", List.of(candidate))).items().size());
        assertThrows(ImageSearchException.class, () -> ImageSearchClient.validate(
                new ImageSearchClient.Result("success", "CANDIDATES", "v1", List.of(candidate, candidate))));
        assertThrows(ImageSearchException.class, () -> ImageSearchClient.validate(
                new ImageSearchClient.Result("success", "CANDIDATES", "v1", List.of(new ImageSearchClient.Candidate(1L, 1L, Double.NaN)))));
    }

    @Test void sendsMultipartAndInternalTokenAndParsesIds() {
        var builder = RestClient.builder().baseUrl("http://image-service");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new ImageSearchClient("http://image-service", "test-only", true, 2);
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("http://image-service/image-search/search?limit=20"))
                .andExpect(header("X-Image-Search-Token", "test-only"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"image\"")))
                .andRespond(withSuccess("""
                    {"status":"success","match_status":"CANDIDATES","index_version":"v1",
                     "items":[{"product_id":7,"image_id":9,"similarity":0.9}]}
                    """, MediaType.APPLICATION_JSON));
        assertEquals(7L, client.search(new byte[]{1, 2}).items().getFirst().productId());
        server.verify();
    }

    @Test void upstreamFailureIsServiceErrorAndNotEmptyResult() {
        var builder = RestClient.builder().baseUrl("http://image-service");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new ImageSearchClient("http://image-service", "test-only", true, 2);
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(anything()).andRespond(withServerError());
        assertEquals(503, assertThrows(ImageSearchException.class, () -> client.search(new byte[]{1})).status());
        server.verify();
    }
}
