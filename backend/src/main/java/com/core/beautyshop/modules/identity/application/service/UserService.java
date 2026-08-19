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
}
