package com.core.beautyshop.shared.security.jwt;

public interface AccessTokenRevocationChecker {

    boolean isCurrent(Long userId, Integer tokenVersion);
    boolean isSessionCurrent(Long userId, String familyId);
}
