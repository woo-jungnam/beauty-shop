package com.core.beautyshop.modules.identity.application.facade;

import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.identity.api.dto.UserSummaryDto;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
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
