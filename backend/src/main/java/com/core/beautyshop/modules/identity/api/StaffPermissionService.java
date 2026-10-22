package com.core.beautyshop.modules.identity.api;

import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.modules.identity.application.service.AuthService;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service("staffPermissions") @RequiredArgsConstructor
public class StaffPermissionService {
    public static final List<String> KNOWN=List.of("ORDERS","CATALOG","INVENTORY","PROCUREMENT","CRM","SPA_RECEPTION","SPA_EXECUTION","REPORTS");
    private final UserRepository users;
    private final AuthService auth;
    @Transactional(readOnly=true) public boolean has(String permission){
        if(!KNOWN.contains(permission))return false;
        if(SecurityUtils.isAdmin())return true;
        if(!SecurityUtils.isStaffOrAdmin())return false;
        var uid=SecurityUtils.getCurrentUserIdOptional(); if(uid.isEmpty())return false;
        return users.findById(uid.get()).filter(u->!Boolean.TRUE.equals(u.getIsDeleted()) && u.getStatus()==AccountStatus.ACTIVE)
                .map(u->u.getStaffPermissions()!=null && u.getStaffPermissions().contains(permission)).orElse(false);
    }
    @Transactional public List<String> replace(Long uid,Set<String> permissions){
        if(!SecurityUtils.isAdmin())throw new org.springframework.security.access.AccessDeniedException("Chỉ quản trị viên được phân quyền");
        if(permissions==null || !KNOWN.containsAll(permissions))throw new BusinessException("Danh sách quyền nghiệp vụ không hợp lệ");
        var user=users.findByIdForUpdate(uid).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy người dùng"));
        if(user.getRoles().stream().noneMatch(r->"ROLE_STAFF".equals(r.getName())))throw new BusinessException("Chỉ phân quyền nghiệp vụ cho tài khoản STAFF");
        user.setStaffPermissions(new java.util.HashSet<>(permissions));users.save(user);auth.forceLogoutUser(uid);
        return KNOWN.stream().filter(permissions::contains).toList();
    }
    @Transactional(readOnly=true) public List<String> forUser(Long uid){
        var user=users.findById(uid).filter(u->!Boolean.TRUE.equals(u.getIsDeleted())).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy người dùng"));
        return KNOWN.stream().filter(user.getStaffPermissions()::contains).toList();
    }
}
