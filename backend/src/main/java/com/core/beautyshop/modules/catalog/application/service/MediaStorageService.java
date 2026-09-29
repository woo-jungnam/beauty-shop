package com.core.beautyshop.modules.catalog.application.service;

import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MediaStorageService {
    private final MediaAssetRepository repository;
    @Value("${app.media.upload-dir:uploads}") private String uploadDirectory;

    @Transactional
    public MediaView upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("Image file is required");
        if (file.getSize() > 10L * 1024 * 1024) throw new BusinessException("Image exceeds the 10 MB limit");
        String contentType = Optional.ofNullable(file.getContentType()).orElse("");
        if (!Set.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(contentType)) throw new BusinessException("Unsupported image type");
        String original = Paths.get(Optional.ofNullable(file.getOriginalFilename()).orElse("image")).getFileName().toString();
        String extension = extension(contentType);
        String stored = UUID.randomUUID() + extension;
        Path root = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        Path target = root.resolve(stored).normalize();
        if (!target.startsWith(root)) throw new BusinessException("Invalid file name");
        try {
            Files.createDirectories(root);
            try (var input = file.getInputStream()) { Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException exception) { throw new BusinessException("Unable to store image"); }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try { Files.deleteIfExists(target); } catch (IOException ignored) { }
                    }
                }
            });
        }
        MediaAsset asset = repository.save(MediaAsset.builder().originalName(original).storedName(stored).contentType(contentType)
                .sizeBytes(file.getSize()).storagePath(target.toString()).publicUrl("/uploads/" + stored).build());
        return new MediaView(asset.getId(), asset.getOriginalName(), asset.getContentType(), asset.getSizeBytes(), asset.getPublicUrl());
    }
    private String extension(String contentType) { return switch (contentType) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; case "image/gif" -> ".gif"; default -> ".jpg"; }; }
    public record MediaView(Long id, String originalName, String contentType, Long sizeBytes, String url) { }
}
