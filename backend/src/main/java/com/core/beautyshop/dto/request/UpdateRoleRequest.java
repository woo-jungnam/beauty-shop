package com.core.beautyshop.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateRoleRequest {
    @NotBlank(message = "Tên quyền không được để trống")
    private String roleName;

    private String description;
}