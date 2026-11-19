package com.core.beautyshop.modules.identity.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @NotBlank @Size(max = 8192) String credential,
        @Size(max = 100) String password
) {
    @Override
    public String toString() {
        return "GoogleLoginRequest[redacted]";
    }
}
