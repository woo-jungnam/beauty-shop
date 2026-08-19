package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.application.dto.request.CreateRoleRequest;
import com.core.beautyshop.modules.identity.application.dto.request.UpdateRoleRequest;
import com.core.beautyshop.modules.identity.application.dto.response.RoleResponse;
import java.util.List;

public interface RoleService {
    List<RoleResponse> getAllRoles();
    RoleResponse getRoleById(Long id);
    RoleResponse createRole(CreateRoleRequest request);
    RoleResponse updateRole(Long id, UpdateRoleRequest request);
    void deleteRole(Long id);
}
