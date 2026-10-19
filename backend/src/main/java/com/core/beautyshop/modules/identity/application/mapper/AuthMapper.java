package com.core.beautyshop.modules.identity.application.mapper;

import com.core.beautyshop.modules.identity.application.dto.response.AuthResponse;
import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.modules.identity.domain.User;
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
                .permissions(permissions(user))
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
                .permissions(permissions(user))
                .membershipTier(user.getMembershipTier())
                .loyaltyPoints(user.getLoyaltyPoints())
                .status(user.getStatus())
                .build();
    }

    private List<String> permissions(User user) {
        if(user.getRoles()!=null && user.getRoles().stream().anyMatch(r->"ROLE_ADMIN".equals(r.getName())))
            return com.core.beautyshop.modules.identity.api.StaffPermissionService.KNOWN;
        return user.getStaffPermissions()==null ? List.of() : user.getStaffPermissions().stream().sorted().toList();
    }
}
