package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.LoginRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RefreshTokenRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RegisterRequest;
import com.core.beautyshop.modules.identity.application.dto.response.AuthResponse;
import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.modules.identity.domain.RefreshTokenSession;
import com.core.beautyshop.modules.identity.domain.RefreshTokenSessionRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.identity.application.factory.UserFactory;
import com.core.beautyshop.modules.identity.application.mapper.AuthMapper;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.shared.security.jwt.JwtUtils;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtils jwtUtils;
    private final UserFactory userFactory;
    private final AuthMapper authMapper;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final TokenVersionCache tokenVersionCache;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(),
                        request.getPassword()
                )
        );

        UserDetailsImpl authenticated = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findByIdForUpdate(authenticated.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // Authentication loaded this entity before the lock; discard that possibly stale state.
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireActiveUser(user);
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        String familyId = UUID.randomUUID().toString();
        String jwt = jwtUtils.generateAccessToken(userDetails, familyId);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails.getUsername());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        tokenVersionCache.updateCurrentVersion(user.getId(), user.getTokenVersion());
        saveRefreshSession(user, refreshToken, familyId);

        return authMapper.toAuthResponse(user, jwt, refreshToken, roles);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().contains("@") || request.getUsername().chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException("Username must not contain @ or whitespace");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Lỗi: Tên đăng nhập đã được sử dụng!");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Lỗi: Email đã được sử dụng!");
        }
        if (userRepository.existsByEmail(request.getUsername()) || userRepository.existsByUsername(request.getEmail())) {
            throw new BusinessException("Username and email must identify a single account");
        }

        List<Role> roles = new ArrayList<>();
        Role defaultRole = roleRepository.findByName(com.core.beautyshop.modules.identity.domain.enums.Role.ROLE_USER.name())
                .or(() -> roleRepository.findByName(com.core.beautyshop.modules.identity.domain.enums.Role.ROLE_CUSTOMER.name()))
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(com.core.beautyshop.modules.identity.domain.enums.Role.ROLE_USER.name())
                        .description("Default User Role")
                        .build()));
        roles.add(defaultRole);

        User user = userFactory.createNewUser(request, roles);
        userRepository.save(user);
        tokenVersionCache.updateCurrentVersion(user.getId(), user.getTokenVersion());

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        String familyId = UUID.randomUUID().toString();
        String jwt = jwtUtils.generateAccessToken(userDetails, familyId);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails.getUsername());

        saveRefreshSession(user, refreshToken, familyId);

        List<String> roleNames = roles.stream().map(Role::getRoleName).toList();

        return authMapper.toAuthResponse(user, jwt, refreshToken, roleNames);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (jwtUtils.validateRefreshToken(refreshToken)) {
            Long sessionUserId = refreshTokenSessionRepository.findUserIdByTokenHash(hashToken(refreshToken))
                    .orElseThrow(() -> new BusinessException("Refresh Token không hợp lệ hoặc đã bị thu hồi"));
            User user = userRepository.findByIdForUpdate(sessionUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            RefreshTokenSession currentSession = refreshTokenSessionRepository
                    .findByTokenHashForUpdate(hashToken(refreshToken))
                    .orElseThrow(() -> new BusinessException("Refresh Token không hợp lệ hoặc đã bị thu hồi"));

            if (currentSession.getRevokedAt() != null) {
                refreshTokenSessionRepository.revokeFamily(currentSession.getFamilyId(), Instant.now());
                throw new BusinessException("Phát hiện Refresh Token đã được sử dụng lại; phiên đăng nhập đã bị thu hồi");
            }
            if (!currentSession.getExpiresAt().isAfter(Instant.now())) {
                refreshTokenSessionRepository.revokeFamily(currentSession.getFamilyId(), Instant.now());
                throw new BusinessException("Refresh Token đã hết hạn");
            }

            String username = jwtUtils.getUserNameFromJwtToken(refreshToken);
            if (!username.equals(user.getUsername()) || !user.getId().equals(currentSession.getUserId())) {
                throw new BusinessException("Refresh Token không thuộc về người dùng hiện tại");
            }
            requireActiveUser(user);

            UserDetailsImpl userDetails = UserDetailsImpl.build(user);
            tokenVersionCache.updateCurrentVersion(user.getId(), user.getTokenVersion());
            String newAccessToken = jwtUtils.generateAccessToken(userDetails, currentSession.getFamilyId());
            String newRefreshToken = jwtUtils.generateRefreshToken(username);

            currentSession.setRevokedAt(Instant.now());
            refreshTokenSessionRepository.save(currentSession);
            saveRefreshSession(user, newRefreshToken, currentSession.getFamilyId());

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            return authMapper.toAuthResponse(user, newAccessToken, newRefreshToken, roles);
        } else {
            throw new BusinessException("Refresh Token không hợp lệ hoặc đã hết hạn");
        }
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        String hash = hashToken(request.getRefreshToken());
        var sessionUserId = refreshTokenSessionRepository.findUserIdByTokenHash(hash);
        if (sessionUserId.isEmpty() || userRepository.findByIdForUpdate(sessionUserId.get()).isEmpty()) return;
        refreshTokenSessionRepository.findByTokenHashForUpdate(hash).ifPresent(session -> {
            if (session.getRevokedAt() == null && session.getExpiresAt().isAfter(Instant.now())) {
                refreshTokenSessionRepository.revokeFamily(session.getFamilyId(), Instant.now());
            }
        });
    }

    @Override
    @Transactional
    public void revokeSession(Long userId, Long sessionId) {
        userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        var session = refreshTokenSessionRepository.findByIdAndUserIdAndIsDeletedFalse(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found for this user"));
        refreshTokenSessionRepository.revokeFamily(session.getFamilyId(), Instant.now());
    }

    private void requireActiveUser(User user) {
        if (Boolean.TRUE.equals(user.getIsDeleted()) || user.getStatus() != com.core.beautyshop.modules.identity.domain.enums.AccountStatus.ACTIVE) {
            throw new BusinessException("Account is not active");
        }
    }

    @Override
    @Transactional
    public void forceLogoutUser(Long userId) {
        incrementTokenVersion(userId);
        refreshTokenSessionRepository.revokeAllByUserId(userId, Instant.now());
    }

    private void saveRefreshSession(User user, String refreshToken, String familyId) {
        RefreshTokenSession session = RefreshTokenSession.builder()
                .userId(user.getId())
                .tokenHash(hashToken(refreshToken))
                .familyId(familyId)
                .expiresAt(jwtUtils.getExpirationInstant(refreshToken))
                .build();
        refreshTokenSessionRepository.save(session);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void incrementTokenVersion(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng với id: " + userId));
        int currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        if (currentVersion == Integer.MAX_VALUE) {
            throw new BusinessException("Không thể thu hồi token vì phiên bản bảo mật đã vượt giới hạn");
        }
        int newVersion = currentVersion + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);
        tokenVersionCache.updateCurrentVersion(userId, newVersion);
    }

    @Override
    @Transactional
    public int cleanupExpiredSessions(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, java.time.temporal.ChronoUnit.DAYS);
        int deleted = refreshTokenSessionRepository.deleteExpiredOrRevokedBefore(cutoff);
        log.info("Đã dọn dẹp {} refresh token sessions cũ hơn {} ngày", deleted, retentionDays);
        return deleted;
    }
}
