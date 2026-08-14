package com.core.beautyshop.service.user;

import com.core.beautyshop.dto.request.UpdateProfileRequest;
import com.core.beautyshop.dto.response.UserProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserProfileResponse getCurrentUserProfile(String username);

    UserProfileResponse updateProfile(String username, UpdateProfileRequest request);

    Page<UserProfileResponse> getAllUsers(Pageable pageable);

    UserProfileResponse getUserById(Long id);
}
