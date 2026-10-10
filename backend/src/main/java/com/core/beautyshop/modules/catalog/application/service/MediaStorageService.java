package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStorageService {
    private final MediaAssetRepository repository;
    @Value("${app.media.upload-dir:uploads}") private String uploadDirectory;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/pjpeg",
            "image/png", "image/x-png",
            "image/webp",
            "image/gif",
            "image/avif",
            "image/bmp",
            "image/x-ms-bmp"
    );

    @Transactional
    public MediaView upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("Image file is required");
        if (file.getSize() > 10L * 1024 * 1024) throw new BusinessException("Image exceeds the 10 MB limit");
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").trim().toLowerCase(Locale.ROOT);
        String original = Paths.get(Optional.ofNullable(file.getOriginalFilename()).orElse("image")).getFileName().toString();
        if (contentType.equals("image/svg+xml") || original.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            throw new BusinessException("SVG uploads are not supported; upload a raster image");
        }
        if (!isAllowed(contentType, original)) throw new BusinessException("Unsupported image type. Allowed: JPG, PNG, WEBP, GIF, AVIF, BMP (Max 10MB)");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException exception) { throw new BusinessException("Unable to read image"); }
        ImageFormat format = detectFormat(bytes);
        if (format == null) throw new BusinessException("Unsupported image type or invalid image content");
        contentType = format.contentType();
        String stored = UUID.randomUUID() + format.extension();

        Path root = resolveUploadRoot();
        Path target = root.resolve(stored).normalize();
        if (!target.startsWith(root)) throw new BusinessException("Invalid file name");

        try {
            Files.createDirectories(root);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (Exception exception) {
            log.error("Unable to store image file to {}: {}", target, exception.getMessage(), exception);
            throw new BusinessException("Unable to store image: " + exception.getMessage());
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try { Files.deleteIfExists(target); } catch (IOException ignored) { }
                    }
                }
            });
        }
        MediaAsset asset = repository.save(MediaAsset.builder().originalName(original).storedName(stored).contentType(contentType.isBlank() ? "image/jpeg" : contentType)
                .sizeBytes(file.getSize()).storagePath(target.toString()).publicUrl("/uploads/" + stored).build());
        return new MediaView(asset.getId(), asset.getOriginalName(), asset.getContentType(), asset.getSizeBytes(), asset.getPublicUrl());
    }

    private Path resolveUploadRoot() {
        Path configured = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        try {
            if (!Files.exists(configured)) {
                Files.createDirectories(configured);
            }
            if (Files.isWritable(configured)) {
                return configured;
            }
        } catch (Exception e) {
            log.warn("Configured upload directory {} not writable: {}", configured, e.getMessage());
        }

        // Fallback 1: user.home/.beautyshop/uploads
        try {
            Path homePath = Paths.get(System.getProperty("user.home"), ".beautyshop", "uploads").toAbsolutePath().normalize();
            Files.createDirectories(homePath);
            if (Files.isWritable(homePath)) {
                return homePath;
            }
        } catch (Exception ignored) { }

        // Fallback 2: temp dir
        try {
            Path tmpPath = Paths.get(System.getProperty("java.io.tmpdir"), "beautyshop-uploads").toAbsolutePath().normalize();
            Files.createDirectories(tmpPath);
            return tmpPath;
        } catch (Exception e) {
            log.error("Failed to initialize any writable upload directory: {}", e.getMessage());
            return configured;
        }
    }

    private boolean isAllowed(String contentType, String filename) {
        if (ALLOWED_TYPES.contains(contentType)) return true;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp")
                || lower.endsWith(".gif")
                || lower.endsWith(".avif") || lower.endsWith(".bmp");
    }

    private ImageFormat detectFormat(byte[] bytes) {
        if (matches(bytes, 0, 0xff, 0xd8, 0xff)) return new ImageFormat(".jpg", "image/jpeg");
        if (matches(bytes, 0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return new ImageFormat(".png", "image/png");
        if (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a")) return new ImageFormat(".gif", "image/gif");
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return new ImageFormat(".webp", "image/webp");
        if (bytes.length >= 14 && ascii(bytes, 0, "BM")) return new ImageFormat(".bmp", "image/bmp");
        if (bytes.length >= 16 && ascii(bytes, 4, "ftyp")) {
            long boxLength = ((long) (bytes[0] & 255) << 24) | ((long) (bytes[1] & 255) << 16)
                    | ((long) (bytes[2] & 255) << 8) | (bytes[3] & 255);
            if (boxLength >= 16 && boxLength <= bytes.length) {
                if (ascii(bytes, 8, "avif") || ascii(bytes, 8, "avis")) return new ImageFormat(".avif", "image/avif");
                for (int offset = 16; offset + 4 <= boxLength; offset += 4) {
                    if (ascii(bytes, offset, "avif") || ascii(bytes, offset, "avis")) return new ImageFormat(".avif", "image/avif");
                }
            }
        }
        return null;
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int index = 0; index < expected.length(); index++) if (bytes[offset + index] != expected.charAt(index)) return false;
        return true;
    }

    private boolean matches(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) if ((bytes[offset + index] & 255) != expected[index]) return false;
        return true;
    }

    private record ImageFormat(String extension, String contentType) { }

    @Schema(name = "MediaUploadView", description = "Metadata ảnh đã lưu; upload chưa tự gắn ảnh vào đối tượng nghiệp vụ")
    public record MediaView(@Schema(description = "ID media") Long id,
            @Schema(description = "Tên tệp khách gửi") String originalName,
            @Schema(description = "MIME ảnh sau kiểm tra", example = "image/png") String contentType,
            @Schema(description = "Dung lượng byte", example = "1024") Long sizeBytes,
            @Schema(description = "URL tương đối để gắn vào sản phẩm", example = "/uploads/uuid.png") String url,
            @Schema(description = "Alias tương thích của url, cũng là URL tương đối", example = "/uploads/uuid.png") String publicUrl) {
        public MediaView(Long id, String originalName, String contentType, Long sizeBytes, String url) {
            this(id, originalName, contentType, sizeBytes, url, url);
        }
    }
}
