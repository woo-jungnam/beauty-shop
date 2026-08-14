package com.core.beautyshop.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagRequest {
    @NotBlank(message = "Tên thẻ không được để trống")
    private String name;
}