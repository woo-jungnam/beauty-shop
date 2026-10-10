package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class IdentityAdministrationGuard {
    private static final Set<String> SYSTEM_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER", "ROLE_CUSTOMER");
    private final RoleRepository roles;
    private final UserRepository users;

    /** All changes to admin membership/status serialize on the same existing role row. */
    public void lockAdministration() {
        roles.findByNameForUpdate("ROLE_ADMIN")
                .orElseThrow(() -> new BusinessException("The administrator role is unavailable"));
    }

    public boolean isSystemRole(String name) { return SYSTEM_ROLES.contains(name); }

    public void requireAnotherActiveAdmin(User user) {
        boolean activeAdmin = user.getStatus() == AccountStatus.ACTIVE && user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getName()) && !Boolean.TRUE.equals(role.getIsDeleted()));
        if (activeAdmin && users.countActiveAdminsExcluding(user.getId()) == 0) {
            throw new BusinessException("At least one active administrator must remain");
        }
    }
}
