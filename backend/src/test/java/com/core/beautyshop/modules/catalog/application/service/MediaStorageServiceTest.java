package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.domain.MediaAsset;
import com.core.beautyshop.modules.catalog.domain.MediaAssetRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {

    @Mock
    private MediaAssetRepository repository;

    @TempDir
    Path tempDir;

    private MediaStorageService service;

    @BeforeEach
    void setUp() {
        service = new MediaStorageService(repository);
        ReflectionTestUtils.setField(service, "uploadDirectory", tempDir.toString());
    }

    @Test
    void uploadJpegSuccess() {
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            asset.setId(100L);
            return asset;
        });

        byte[] content = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
        MockMultipartFile file = new MockMultipartFile(
                "file", "my-cosmetic.jpg", "image/jpeg", content);

        MediaStorageService.MediaView result = service.upload(file);

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals("my-cosmetic.jpg", result.originalName());
        assertEquals("image/jpeg", result.contentType());
        assertTrue(result.url().startsWith("/uploads/"));
        assertTrue(result.url().endsWith(".jpg"));
        assertEquals(result.url(), result.publicUrl());
        verify(repository, times(1)).save(any(MediaAsset.class));
    }

    @Test
    void uploadPngSuccess() {
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            asset.setId(101L);
            return asset;
        });

        byte[] content = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        MockMultipartFile file = new MockMultipartFile(
                "file", "serum-laroche.png", "image/png", content);

        MediaStorageService.MediaView result = service.upload(file);

        assertNotNull(result);
        assertTrue(result.url().endsWith(".png"));
    }

    @Test
    void uploadWebpSuccess() {
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            asset.setId(102L);
            return asset;
        });

        byte[] content = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P'};
        MockMultipartFile file = new MockMultipartFile(
                "file", "product.webp", "image/webp", content);

        MediaStorageService.MediaView result = service.upload(file);

        assertNotNull(result);
        assertTrue(result.url().endsWith(".webp"));
    }

    @Test
    void uploadByFileExtensionFallbackSuccess() {
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            asset.setId(103L);
            return asset;
        });

        byte[] content = {0, 0, 0, 16, 'f', 't', 'y', 'p', 'a', 'v', 'i', 'f', 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample-cream.AVIF", "application/octet-stream", content);

        MediaStorageService.MediaView result = service.upload(file);

        assertNotNull(result);
        assertTrue(result.url().toLowerCase().endsWith(".avif"));
        assertEquals("image/avif", result.contentType());
    }

    @Test
    void svgIsRejectedEvenWhenAdvertisedAsPng() {
        MockMultipartFile svg = new MockMultipartFile("file", "image.svg", "image/png",
                "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes());
        assertThrows(BusinessException.class, () -> service.upload(svg));
        verifyNoInteractions(repository);
    }

    @Test
    void activeContentDisguisedAsJpegIsRejected() {
        MockMultipartFile svg = new MockMultipartFile("file", "image.jpg", "image/jpeg",
                "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes());
        assertThrows(BusinessException.class, () -> service.upload(svg));
        verifyNoInteractions(repository);
    }

    @Test
    void storedExtensionAndMimeFollowImageBytesInsteadOfClaimedMetadata() {
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        var result = service.upload(new MockMultipartFile("file", "mislabelled.jpg", "image/jpeg", png));
        assertTrue(result.url().endsWith(".png"));
        assertEquals("image/png", result.contentType());
    }

    @Test
    void uploadRejectsNullOrEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThrows(BusinessException.class, () -> service.upload(emptyFile));
        assertThrows(BusinessException.class, () -> service.upload(null));
        verify(repository, never()).save(any());
    }

    @Test
    void uploadRejectsUnsupportedContentType() {
        MockMultipartFile pdfFile = new MockMultipartFile("file", "manual.pdf", "application/pdf", "pdf-data".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(pdfFile));
        assertTrue(ex.getMessage().contains("Unsupported image type"));
        verify(repository, never()).save(any());
    }

    @Test
    void uploadRejectsFileExceeding10MB() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile bigFile = new MockMultipartFile("file", "huge.jpg", "image/jpeg", oversized);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload(bigFile));
        assertTrue(ex.getMessage().contains("10 MB"));
        verify(repository, never()).save(any());
    }
}
