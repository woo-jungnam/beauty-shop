package com.core.beautyshop.modules.identity.application.facade;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IdentityFacadeImpl implements IdentityFacade {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserSummaryDto createReceptionCustomer(String fullName, String phone) {
        String name = fullName == null ? "" : fullName.trim();
        String normalized = phone == null ? "" : phone.replaceAll("[\\s().-]", "");
        if (normalized.startsWith("+84")) normalized = "0" + normalized.substring(3);
        else if (normalized.startsWith("84") && normalized.length() == 11) normalized = "0" + normalized.substring(2);
        if (name.isBlank() || name.length() > 100) throw new BusinessException("Họ tên khách phải từ 1 đến 100 ký tự.");
        if (!normalized.matches("0[35789]\\d{8}")) throw new BusinessException("Vui lòng nhập số điện thoại di động Việt Nam hợp lệ.");
        var existing = userRepository.findByPhoneIn(java.util.List.of(normalized, "+84" + normalized.substring(1), "84" + normalized.substring(1)));
        if (!existing.isEmpty()) {
            User customer = existing.get(0);
            if (existing.size() != 1 || Boolean.TRUE.equals(customer.getIsDeleted()) || customer.getStatus() != AccountStatus.ACTIVE
                    || customer.getRoles() == null || customer.getRoles().stream().noneMatch(role -> "ROLE_CUSTOMER".equals(role.getName()) && !Boolean.TRUE.equals(role.getIsDeleted()))
                    || customer.getRoles().stream().anyMatch(role -> java.util.Set.of("ROLE_ADMIN", "ROLE_STAFF").contains(role.getName()))) {
                throw new BusinessException("Số điện thoại đã gắn với tài khoản khác hoặc hồ sơ đã khóa. Vui lòng kiểm tra tại CRM.");
            }
            return mapToSummary(customer);
        }
        var role = roleRepository.findByName("ROLE_CUSTOMER").filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .orElseThrow(() -> new BusinessException("Chưa cấu hình vai trò khách hàng. Vui lòng liên hệ quản trị viên."));
        User customer = User.builder().fullName(name).phone(normalized)
                .username("walkin_" + java.util.UUID.randomUUID().toString().replace("-", ""))
                .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .roles(new java.util.ArrayList<>(java.util.List.of(role))).build();
        return mapToSummary(userRepository.saveAndFlush(customer));
    }

    @Override
    public Optional<UserSummaryDto> findUserSummaryById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(this::mapToSummary);
    }

    @Override
    public UserSummaryDto getUserSummaryById(Long userId) {
        if (userId == null) {
            throw new ResourceNotFoundException("ID người dùng không được để trống");
        }
        return userRepository.findById(userId)
                .map(this::mapToSummary)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));
    }

    @Override
    public boolean existsById(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.existsById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, UserSummaryDto> findUserSummaries(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, this::mapToSummary));
    }

    private UserSummaryDto mapToSummary(User user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .membershipTier(user.getMembershipTier() != null ? user.getMembershipTier().name() : null)
                .membershipDiscountPercentage(
                        user.getMembershipTier() != null
                                ? user.getMembershipTier().getDiscountPercentage()
                                : 0
                )
                .loyaltyPoints(user.getLoyaltyPoints())
                .build();
    }
}
