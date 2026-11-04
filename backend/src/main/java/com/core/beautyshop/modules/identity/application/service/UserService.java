package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.api.AdminUserController.SessionView;
import com.core.beautyshop.modules.identity.application.dto.request.UpdateProfileRequest;
import com.core.beautyshop.modules.identity.application.dto.response.ResetPasswordResponse;
import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import com.core.beautyshop.modules.identity.domain.UserStatusHistory;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse getCurrentUserProfile(String username);

    UserProfileResponse updateProfile(UpdateProfileRequest request);

    UserProfileResponse updateProfile(String username, UpdateProfileRequest request);

    Page<UserProfileResponse> getAllUsers(Pageable pageable);
    Page<UserProfileResponse> getAllUsers(String keyword, AccountStatus status, String role, Pageable pageable);
    Page<UserStatusHistory> getStatusHistory(Long userId, Pageable pageable);

    UserProfileResponse getUserById(Long id);

    void reverseLoyaltyPoints(Long userId, Long orderId);

    UserProfileResponse updateStatus(Long userId, AccountStatus status, String reason);
    UserProfileResponse updateRoles(Long userId, List<Long> roleIds);
    ResetPasswordResponse resetPassword(Long userId);
    List<SessionView> getUserSessions(Long userId);
}
