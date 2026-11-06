package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.CreateRoleRequest;
import com.core.beautyshop.modules.identity.application.dto.request.UpdateRoleRequest;
import com.core.beautyshop.modules.identity.application.dto.response.RoleResponse;
import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final IdentityAdministrationGuard administrationGuard;
    private final AuthService authService;

    @Override
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id).filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với id: " + id));
        return mapToResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        java.util.Optional<Role> existing = roleRepository.findByRoleName(request.getRoleName());
        if (existing.isPresent()) {
            Role r = existing.get();
            if (Boolean.TRUE.equals(r.getIsDeleted())) {
                r.setIsDeleted(false);
                r.setDescription(request.getDescription());
                return mapToResponse(roleRepository.save(r));
            }
            throw new BusinessException("Tên vai trò đã tồn tại");
        }
        Role role = Role.builder()
                .roleName(request.getRoleName())
                .description(request.getDescription())
                .build();
        role = roleRepository.save(role);
        return mapToResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, UpdateRoleRequest request) {
        administrationGuard.lockAdministration();
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với id: " + id));
        
        boolean renamed = !role.getRoleName().equals(request.getRoleName());
        if (renamed && administrationGuard.isSystemRole(role.getName())) {
            throw new BusinessException("Không được phép sửa mã vai trò hệ thống mặc định");
        }
        if (renamed &&
            roleRepository.findByRoleName(request.getRoleName()).isPresent()) {
            throw new BusinessException("Tên vai trò đã tồn tại");
        }

        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role = roleRepository.save(role);
        if (renamed) userRepository.findByRoleIdForUpdate(id).forEach(user -> authService.forceLogoutUser(user.getId()));
        return mapToResponse(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        administrationGuard.lockAdministration();
        Role role = roleRepository.findById(id).filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với id: " + id));
        if (administrationGuard.isSystemRole(role.getName())) throw new BusinessException("Không được phép xóa vai trò hệ thống mặc định");
        for (var user : userRepository.findByRoleIdForUpdate(id)) {
            user.getRoles().removeIf(assigned -> id.equals(assigned.getId()));
            userRepository.save(user);
            authService.forceLogoutUser(user.getId());
        }
        userRepository.flush();
        role.setIsDeleted(true);
        roleRepository.save(role);
    }

    private RoleResponse mapToResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
