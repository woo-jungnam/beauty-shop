package com.core.beautyshop.shared.config.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemConfig extends Base {
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;
    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;
    @Column(name = "value_type", nullable = false, length = 20)
    @Builder.Default
    private String valueType = "STRING";
    @Column(length = 500)
    private String description;
    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;
}
