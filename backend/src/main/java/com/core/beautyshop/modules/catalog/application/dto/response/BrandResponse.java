package com.core.beautyshop.modules.catalog.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandResponse {
    private Long id;
    private String name;
    private String slug;
    private String logoUrl;
    private String description;
    private String originCountry;
    private Boolean isOfficial;
}
