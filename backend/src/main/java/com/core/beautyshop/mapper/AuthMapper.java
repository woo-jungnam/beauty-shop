package com.core.beautyshop.mapper;

import com.core.beautyshop.dto.response.AuthResponse;
import com.core.beautyshop.dto.response.UserProfileResponse;
import com.core.beautyshop.entities.user.Role;
import com.core.beautyshop.entities.user.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthMapper {

    public AuthResponse toAuthResponse(User user, String accessToken, String refreshToken, List<String> roles) {
        if (user == null) {
            return null;
        }
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .build();
    }

    public UserProfileResponse toUserProfileResponse(User user) {
        if (user == null) {
            return null;
        }
        
        List<String> roles = user.getRoles() != null 
                ? user.getRoles().stream().map(Role::getRoleName).toList()
                : List.of();

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .roles(roles)
                .build();
    }
}
