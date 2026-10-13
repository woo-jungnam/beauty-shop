package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.application.service.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MediaStorageService mediaStorageService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadEndpointReturnsCreatedWithUrlAndPublicUrl() throws Exception {
        MediaStorageService.MediaView view = new MediaStorageService.MediaView(
                1L, "serum.jpg", "image/jpeg", 1024L, "/uploads/uuid-123.jpg", "/uploads/uuid-123.jpg");

        when(mediaStorageService.upload(any())).thenReturn(view);

        MockMultipartFile file = new MockMultipartFile(
                "file", "serum.jpg", MediaType.IMAGE_JPEG_VALUE, "dummy-image-data".getBytes());

        mockMvc.perform(multipart("/api/v1/admin/media/upload")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.url").value("/uploads/uuid-123.jpg"))
                .andExpect(jsonPath("$.data.publicUrl").value("/uploads/uuid-123.jpg"));
    }
}
