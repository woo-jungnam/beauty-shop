package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.api.AdminUserController.SessionView;
import com.core.beautyshop.modules.identity.application.dto.request.UpdateProfileRequest;
import com.core.beautyshop.modules.identity.application.dto.response.ResetPasswordResponse;
import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import com.core.beautyshop.modules.identity.application.mapper.AuthMapper;
import com.core.beautyshop.modules.identity.domain.LoyaltyPointAward;
import com.core.beautyshop.modules.identity.domain.LoyaltyPointAwardRepository;
import com.core.beautyshop.modules.identity.domain.RefreshTokenSessionRepository;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.identity.domain.UserStatusHistory;
import com.core.beautyshop.modules.identity.domain.UserStatusHistoryRepository;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.modules.identity.domain.enums.MembershipTier;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final LoyaltyPointAwardRepository loyaltyPointAwardRepository;
    private final AuthMapper authMapper;
    private final UserStatusHistoryRepository statusHistoryRepository;
    private final RoleRepository roleRepository;
    private final IdentityAdministrationGuard administrationGuard;
    private final AuthService authService;
    private final RefreshTokenSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    private String getAuthenticatedUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            throw new UnauthorizedException("Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn. Vui lòng đăng nhập lại!");
        }
        return auth.getName();
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        return getCurrentUserProfile(getAuthenticatedUsername());
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        return updateProfile(getAuthenticatedUsername(), request);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String username) {
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            throw new UnauthorizedException("Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn. Vui lòng đăng nhập lại!");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Phiên làm việc không hợp lệ (người dùng không tồn tại). Vui lòng đăng nhập lại!"));
        return authMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            throw new UnauthorizedException("Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn. Vui lòng đăng nhập lại!");
        }

        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new UnauthorizedException("Phiên làm việc không hợp lệ (người dùng không tồn tại). Vui lòng đăng nhập lại!"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) {
            if (!request.getPhone().isBlank()
                    && userRepository.existsByPhoneAndNotDeletedExcluding(request.getPhone(), user.getId())) {
                throw new BusinessException("Số điện thoại đã được sử dụng bởi tài khoản khác");
            }
            user.setPhone(request.getPhone());
        }
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getBio() != null) user.setBio(request.getBio());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getDateOfBirth() != null) user.setDateOfBirth(request.getDateOfBirth());

        User saved = userRepository.save(user);
        return authMapper.toUserProfileResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getAllUsers(Pageable pageable) {
        return getAllUsers(null, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getAllUsers(String keyword, AccountStatus status, String role, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("username")), value),
                    cb.like(cb.lower(root.get("email")), value), cb.like(cb.lower(root.get("fullName")), value), cb.like(root.get("phone"), value)));
        }
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (role != null && !role.isBlank()) {
            String name = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            spec = spec.and((root, query, cb) -> { query.distinct(true); return cb.equal(root.join("roles").get("name"), name); });
        }
        return userRepository.findAll(spec, pageable).map(authMapper::toUserProfileResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserStatusHistory> getStatusHistory(Long userId, Pageable pageable) {
        getUserById(userId);
        return statusHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + id));
        return authMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public void reverseLoyaltyPoints(Long userId, Long orderId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        loyaltyPointAwardRepository.findByOrderId(orderId).ifPresent(award -> {
            if (award.isReversed()) return;
            if (!userId.equals(award.getUserId())) throw new BusinessException("Chủ sở hữu phần thưởng khách hàng thân thiết không khớp");
            int points = Math.max(0, user.getLoyaltyPoints() - award.getPoints());
            user.setLoyaltyPoints(points);
            user.setMembershipTier(Arrays.stream(MembershipTier.values())
                    .filter(tier -> points >= tier.getRequiredPoints())
                    .max(Comparator.comparingInt(MembershipTier::getRequiredPoints))
                    .orElse(MembershipTier.MEMBER));
            award.setReversed(true);
            loyaltyPointAwardRepository.save(award);
            userRepository.save(user);
        });
    }

    @Override
    @Transactional
    public UserProfileResponse updateStatus(Long userId, AccountStatus status, String reason) {
        if (status == null) throw new BusinessException("Trạng thái tài khoản là bắt buộc");
        if (reason != null && reason.length() > 500) throw new BusinessException("Lý do trạng thái không được vượt quá 500 ký tự");
        administrationGuard.lockAdministration();
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));
        var previous = user.getStatus() == null ? AccountStatus.ACTIVE : user.getStatus();
        if (previous == status) return authMapper.toUserProfileResponse(user);
        if (status != AccountStatus.ACTIVE) administrationGuard.requireAnotherActiveAdmin(user);
        user.setStatus(status);
        statusHistoryRepository.save(UserStatusHistory.builder()
                .userId(userId).oldStatus(previous).newStatus(status).reason(reason).build());
        if (status != AccountStatus.ACTIVE) authService.forceLogoutUser(userId);
        return authMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) throw new BusinessException("Cần chọn ít nhất một vai trò");
        if (roleIds.stream().anyMatch(Objects::isNull)) throw new BusinessException("Danh sách mã vai trò là bắt buộc");
        administrationGuard.lockAdministration();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));
        var roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.stream().distinct().count()) throw new BusinessException("Một hoặc nhiều vai trò không tồn tại trong hệ thống");
        if (roles.stream().anyMatch(role -> Boolean.TRUE.equals(role.getIsDeleted()))) throw new BusinessException("Không thể gán vai trò đã bị xóa");
        if (roles.stream().noneMatch(role -> "ROLE_ADMIN".equals(role.getName()))) administrationGuard.requireAnotherActiveAdmin(user);
        user.setRoles(new ArrayList<>(roles));
        userRepository.save(user);
        authService.forceLogoutUser(userId);
        return authMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetPassword(Long userId) {
        administrationGuard.lockAdministration();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException("Không thể đặt lại mật khẩu cho tài khoản đã bị xóa");
        }
        String tempPassword = generateSecurePassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);
        authService.forceLogoutUser(userId);
        return ResetPasswordResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .temporaryPassword(tempPassword)
                .message("Mật khẩu tạm thời đã được tạo thành công. Toàn bộ phiên làm việc cũ đã bị thu hồi.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionView> getUserSessions(Long userId) {
        getUserById(userId);
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(session -> new SessionView(
                        session.getId(), session.getFamilyId(), session.getCreatedAt(), session.getExpiresAt(), session.getRevokedAt()))
                .toList();
    }

    private String generateSecurePassword() {
        final String uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lowercase = "abcdefghijkmnopqrstuvwxyz";
        final String digits = "23456789";
        final String special = "@#$%&*";
        final String all = uppercase + lowercase + digits + special;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append(uppercase.charAt(random.nextInt(uppercase.length())));
        sb.append(lowercase.charAt(random.nextInt(lowercase.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(special.charAt(random.nextInt(special.length())));
        for (int i = 4; i < 10; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }
}
