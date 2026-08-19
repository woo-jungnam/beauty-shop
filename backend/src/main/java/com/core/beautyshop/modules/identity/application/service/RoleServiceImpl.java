package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.CreateRoleRequest;
import com.core.beautyshop.modules.identity.application.dto.request.UpdateRoleRequest;
import com.core.beautyshop.modules.identity.application.dto.response.RoleResponse;
import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Override
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với id: " + id));
        return mapToResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.findByRoleName(request.getRoleName()).isPresent()) {
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
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với id: " + id));
        
        if (!role.getRoleName().equals(request.getRoleName()) && 
            roleRepository.findByRoleName(request.getRoleName()).isPresent()) {
            throw new BusinessException("Tên vai trò đã tồn tại");
        }

        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role = roleRepository.save(role);
        return mapToResponse(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy vai trò với id: " + id);
        }
        roleRepository.deleteById(id);
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
