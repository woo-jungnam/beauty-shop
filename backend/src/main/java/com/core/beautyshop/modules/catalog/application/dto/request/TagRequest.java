package com.core.beautyshop.modules.catalog.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagRequest {
    @NotBlank(message = "Tên thẻ không được để trống")
    private String name;
}
