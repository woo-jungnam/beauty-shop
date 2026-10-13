package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.LoginRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RefreshTokenRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RegisterRequest;
import com.core.beautyshop.modules.identity.application.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    void forceLogoutUser(Long userId);
    void revokeSession(Long userId, Long sessionId);

    int cleanupExpiredSessions(int retentionDays);
}
