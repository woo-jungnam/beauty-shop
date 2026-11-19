package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.GoogleLoginRequest;
import com.core.beautyshop.modules.identity.application.dto.request.LoginRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RefreshTokenRequest;
import com.core.beautyshop.modules.identity.application.dto.request.RegisterRequest;
import com.core.beautyshop.modules.identity.application.dto.response.AuthResponse;
import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.modules.identity.domain.RefreshTokenSession;
import com.core.beautyshop.modules.identity.domain.RefreshTokenSessionRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ErrorCode;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.identity.application.factory.UserFactory;
import com.core.beautyshop.modules.identity.application.mapper.AuthMapper;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.shared.security.jwt.JwtUtils;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Set<String> CUSTOMER_ROLES = Set.of("ROLE_USER", "ROLE_CUSTOMER");
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtils jwtUtils;
    private final UserFactory userFactory;
    private final AuthMapper authMapper;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final TokenVersionCache tokenVersionCache;
    private final EntityManager entityManager;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(),
                        request.getPassword()
                )
        );

        UserDetailsImpl authenticated = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findByIdForUpdate(authenticated.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng"));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        requireActiveUser(user);
        return issueLoginSession(user, true);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        var identity = googleIdentityVerifier.verify(request.credential());
        var linkedUser = userRepository.findByGoogleSubject(identity.subject());
        if (linkedUser.isPresent()) {
            User user = lockGoogleCustomer(linkedUser.get().getId());
            if (!identity.subject().equals(user.getGoogleSubject())) {
                throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
            }
            return issueLoginSession(user, true);
        }

        var existingUser = userRepository.findByEmailIgnoreCase(identity.email());
        if (existingUser.isPresent()) {
            User user = lockGoogleCustomer(existingUser.get().getId());
            if (!identity.email().equalsIgnoreCase(user.getEmail())) {
                throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
            }
            // A request that waited for the same link can use the completed association.
            if (identity.subject().equals(user.getGoogleSubject())) {
                return issueLoginSession(user, true);
            }
            if (user.getGoogleSubject() != null) {
                throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Tài khoản BeautyShop đã liên kết với một tài khoản Google khác.");
            }
            if (request.password() == null || request.password().isEmpty()) {
                throw new BusinessException(ErrorCode.GOOGLE_LINK_REQUIRED);
            }
            if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            user.setGoogleSubject(identity.subject());
            userRepository.flush();
            return issueLoginSession(user, true);
        }

        if (request.password() != null) {
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        if (!identity.googleManagesEmail()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Hãy đăng ký BeautyShop bằng email này trước, sau đó dùng mật khẩu BeautyShop để liên kết Google.");
        }
        String name = identity.name();
        if (name == null || name.isBlank()) {
            name = "Khách hàng BeautyShop";
        }
        name = name.strip();
        name = name.substring(0, name.offsetByCodePoints(0, Math.min(100, name.codePointCount(0, name.length()))));
        User user = userRepository.saveAndFlush(User.builder()
                .username("google_" + UUID.randomUUID().toString().replace("-", ""))
                .email(identity.email())
                .googleSubject(identity.subject())
                .fullName(name)
                .roles(List.of(defaultCustomerRole()))
                .build());
        return issueLoginSession(user, true);
    }

    private User lockGoogleCustomer(Long id) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        requireActiveUser(user);
        if (user.getRoles().isEmpty() || user.getRoles().stream().anyMatch(role -> !CUSTOMER_ROLES.contains(role.getRoleName()))
                || !user.getStaffPermissions().isEmpty()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Đăng nhập Google chỉ dành cho tài khoản khách hàng.");
        }
        return user;
    }

    private AuthResponse issueLoginSession(User user, boolean authenticateRequest) {
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        if (authenticateRequest) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        }
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
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên đăng nhập không được chứa ký tự @ hoặc khoảng trắng");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Tên đăng nhập đã được sử dụng");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Email đã được sử dụng");
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()
                && userRepository.existsByPhoneAndNotDeleted(request.getPhone())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "Số điện thoại đã được sử dụng");
        }
        if (userRepository.existsByEmail(request.getUsername()) || userRepository.existsByUsername(request.getEmail())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên đăng nhập và email không được trùng với tài khoản khác");
        }

        List<Role> roles = List.of(defaultCustomerRole());
        User user = userFactory.createNewUser(request, roles);
        userRepository.save(user);
        return issueLoginSession(user, false);
    }

    private Role defaultCustomerRole() {
        return roleRepository.findByName(com.core.beautyshop.modules.identity.domain.enums.Role.ROLE_USER.name())
                .or(() -> roleRepository.findByName(com.core.beautyshop.modules.identity.domain.enums.Role.ROLE_CUSTOMER.name()))
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(com.core.beautyshop.modules.identity.domain.enums.Role.ROLE_USER.name())
                        .description("Default User Role")
                        .build()));
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class, isolation = Isolation.READ_COMMITTED)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (jwtUtils.validateRefreshToken(refreshToken)) {
            Long sessionUserId = refreshTokenSessionRepository.findUserIdByTokenHash(hashToken(refreshToken))
                    .orElseThrow(() -> new BusinessException("Refresh Token không hợp lệ hoặc đã bị thu hồi"));
            User user = userRepository.findByIdForUpdate(sessionUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng"));
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
        userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));
        var session = refreshTokenSessionRepository.findByIdAndUserIdAndIsDeletedFalse(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên làm việc của người dùng này"));
        refreshTokenSessionRepository.revokeFamily(session.getFamilyId(), Instant.now());
    }

    private void requireActiveUser(User user) {
        if (Boolean.TRUE.equals(user.getIsDeleted()) || user.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Tài khoản hiện đang bị khóa hoặc ngưng hoạt động");
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
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = refreshTokenSessionRepository.deleteExpiredOrRevokedBefore(cutoff);
        log.info("Đã dọn dẹp {} refresh token sessions cũ hơn {} ngày", deleted, retentionDays);
        return deleted;
    }
}
