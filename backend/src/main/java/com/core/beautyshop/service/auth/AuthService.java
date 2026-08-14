package com.core.beautyshop.service.auth;

import com.core.beautyshop.dto.request.LoginRequest;
import com.core.beautyshop.dto.request.RefreshTokenRequest;
import com.core.beautyshop.dto.request.RegisterRequest;
import com.core.beautyshop.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);
}
