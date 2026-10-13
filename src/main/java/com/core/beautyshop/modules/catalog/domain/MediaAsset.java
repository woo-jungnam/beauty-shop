package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "media_assets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MediaAsset extends Base {
    @Column(name = "original_name", nullable = false, length = 255) private String originalName;
    @Column(name = "stored_name", nullable = false, unique = true, length = 255) private String storedName;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "size_bytes", nullable = false) private Long sizeBytes;
    @Column(name = "storage_path", nullable = false, length = 500) private String storagePath;
    @Column(name = "public_url", nullable = false, length = 500) private String publicUrl;
}
