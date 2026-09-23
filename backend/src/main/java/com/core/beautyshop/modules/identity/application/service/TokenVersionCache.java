package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenVersionCache {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "token_versions", key = "#userId", unless = "#result == null")
    public Optional<Integer> findCurrentVersion(Long userId) {
        return userRepository.findTokenVersionById(userId);
    }

    @CachePut(value = "token_versions", key = "#userId")
    public Integer updateCurrentVersion(Long userId, Integer tokenVersion) {
        return tokenVersion;
    }
}
