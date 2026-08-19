package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.UpdateProfileRequest;
import com.core.beautyshop.modules.identity.application.dto.response.UserProfileResponse;
import com.core.beautyshop.modules.identity.domain.User;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
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

        User user = userRepository.findByUsername(username)
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
}
