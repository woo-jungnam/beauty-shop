package com.core.beautyshop.factory;

import com.core.beautyshop.dto.request.RegisterRequest;
import com.core.beautyshop.entities.user.Role;
import com.core.beautyshop.entities.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserFactory {

    private final PasswordEncoder passwordEncoder;

    public UserFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public User createNewUser(RegisterRequest request, List<Role> roles) {
        return User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .bio(request.getBio())
                .avatarUrl(request.getAvatarUrl())
                .roles(roles)
                .build();
    }
}
