package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.domain.RefreshTokenSessionRepository;
import com.core.beautyshop.shared.security.jwt.AccessTokenRevocationChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccessTokenRevocationService implements AccessTokenRevocationChecker {

    private final TokenVersionCache tokenVersionCache;
    private final RefreshTokenSessionRepository sessions;

    @Override
    public boolean isCurrent(Long userId, Integer tokenVersion) {
        if (userId == null || tokenVersion == null) {
            return false;
        }
        return tokenVersionCache.findCurrentVersion(userId)
                .map(currentVersion -> currentVersion.equals(tokenVersion))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionCurrent(Long userId, String familyId) {
        return userId != null && familyId != null && !familyId.isBlank()
                && sessions.existsByUserIdAndFamilyIdAndRevokedAtIsNullAndExpiresAtAfterAndIsDeletedFalse(userId, familyId, Instant.now());
    }
}
