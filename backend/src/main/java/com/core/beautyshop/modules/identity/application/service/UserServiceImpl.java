package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.UpdateProfileRequest;
import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.LoyaltyPointAward;
import com.core.beautyshop.modules.identity.domain.LoyaltyPointAwardRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.exception.UnauthorizedException;
import com.core.beautyshop.modules.identity.application.mapper.AuthMapper;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final LoyaltyPointAwardRepository loyaltyPointAwardRepository;
    private final AuthMapper authMapper;

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
        if (request.getPhone() != null) user.setPhone(request.getPhone());
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
        return userRepository.findAll(pageable).map(authMapper::toUserProfileResponse);
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        loyaltyPointAwardRepository.findByOrderId(orderId).ifPresent(award -> {
            if (award.isReversed()) return;
            if (!userId.equals(award.getUserId())) throw new BusinessException("Loyalty award owner mismatch");
            int points = Math.max(0, user.getLoyaltyPoints() - award.getPoints());
            user.setLoyaltyPoints(points);
            user.setMembershipTier(java.util.Arrays.stream(com.core.beautyshop.modules.identity.domain.enums.MembershipTier.values())
                    .filter(tier -> points >= tier.getRequiredPoints())
                    .max(java.util.Comparator.comparingInt(com.core.beautyshop.modules.identity.domain.enums.MembershipTier::getRequiredPoints))
                    .orElse(com.core.beautyshop.modules.identity.domain.enums.MembershipTier.MEMBER));
            award.setReversed(true);
            loyaltyPointAwardRepository.save(award);
            userRepository.save(user);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void addLoyaltyPoints(Long userId, Long orderId, int pointsToAdd) {
        if (userId == null || orderId == null || pointsToAdd <= 0) {
            return;
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        if (loyaltyPointAwardRepository.existsByOrderId(orderId)) {
            return;
        }

        int currentPoints = user.getLoyaltyPoints() != null ? user.getLoyaltyPoints() : 0;
        long calculatedTotal = (long) currentPoints + pointsToAdd;
        if (calculatedTotal > Integer.MAX_VALUE) {
            throw new BusinessException("Điểm tích lũy vượt quá giới hạn hệ thống");
        }

        int newTotalPoints = (int) calculatedTotal;
        user.setLoyaltyPoints(newTotalPoints);

        com.core.beautyshop.modules.identity.domain.enums.MembershipTier newTier;
        if (newTotalPoints >= com.core.beautyshop.modules.identity.domain.enums.MembershipTier.PLATINUM.getRequiredPoints()) {
            newTier = com.core.beautyshop.modules.identity.domain.enums.MembershipTier.PLATINUM;
        } else if (newTotalPoints >= com.core.beautyshop.modules.identity.domain.enums.MembershipTier.GOLD.getRequiredPoints()) {
            newTier = com.core.beautyshop.modules.identity.domain.enums.MembershipTier.GOLD;
        } else if (newTotalPoints >= com.core.beautyshop.modules.identity.domain.enums.MembershipTier.SILVER.getRequiredPoints()) {
            newTier = com.core.beautyshop.modules.identity.domain.enums.MembershipTier.SILVER;
        } else {
            newTier = com.core.beautyshop.modules.identity.domain.enums.MembershipTier.MEMBER;
        }

        user.setMembershipTier(newTier);
        userRepository.save(user);
        loyaltyPointAwardRepository.save(LoyaltyPointAward.builder()
                .orderId(orderId)
                .userId(userId)
                .points(pointsToAdd)
                .build());
    }
}
