package com.core.beautyshop.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class TagResponse {
    private Long id;
    private String name;
    private String slug;
    private Instant createdAt;
    private Instant updatedAt;
}
