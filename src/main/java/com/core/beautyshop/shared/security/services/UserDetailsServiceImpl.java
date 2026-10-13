package com.core.beautyshop.shared.security.services;

import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = (usernameOrEmail.contains("@") ? userRepository.findByEmail(usernameOrEmail) : userRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng với tên đăng nhập hoặc email: " + usernameOrEmail));

        if (Boolean.TRUE.equals(user.getIsDeleted()) || user.getStatus() != com.core.beautyshop.modules.identity.domain.enums.AccountStatus.ACTIVE) {
            throw new DisabledException("Account is " + user.getStatus());
        }
        return UserDetailsImpl.build(user);
    }
}
