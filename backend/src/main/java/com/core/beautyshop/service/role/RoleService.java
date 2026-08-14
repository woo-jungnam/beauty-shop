package com.core.beautyshop.service.role;

import com.core.beautyshop.dto.request.CreateRoleRequest;
import com.core.beautyshop.dto.request.UpdateRoleRequest;
import com.core.beautyshop.dto.response.RoleResponse;
import java.util.List;

public interface RoleService {
    List<RoleResponse> getAllRoles();
    RoleResponse getRoleById(Long id);
    RoleResponse createRole(CreateRoleRequest request);
    RoleResponse updateRole(Long id, UpdateRoleRequest request);
    void deleteRole(Long id);
}