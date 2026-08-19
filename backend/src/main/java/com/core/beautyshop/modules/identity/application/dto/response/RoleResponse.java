package com.core.beautyshop.modules.identity.application.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class RoleResponse {
    private Long id;
    private String roleName;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
