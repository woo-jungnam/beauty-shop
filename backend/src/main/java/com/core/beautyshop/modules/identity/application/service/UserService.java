package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.UpdateProfileRequest;
import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse getCurrentUserProfile(String username);

    UserProfileResponse updateProfile(UpdateProfileRequest request);

    UserProfileResponse updateProfile(String username, UpdateProfileRequest request);

    Page<UserProfileResponse> getAllUsers(Pageable pageable);

    UserProfileResponse getUserById(Long id);

    void reverseLoyaltyPoints(Long userId, Long orderId);

    void addLoyaltyPoints(Long userId, Long orderId, int pointsToAdd);
    UserProfileResponse updateStatus(Long userId, com.core.beautyshop.modules.identity.domain.enums.AccountStatus status, String reason);
}
