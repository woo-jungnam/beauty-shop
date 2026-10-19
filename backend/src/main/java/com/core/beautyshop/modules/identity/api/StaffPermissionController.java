package com.core.beautyshop.modules.identity.api;
import com.core.beautyshop.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/users") @PreAuthorize("hasRole('ADMIN')")
public class StaffPermissionController {
    private final StaffPermissionService permissions;
    @GetMapping("/staff-permissions") public ApiResponse<List<String>> known(){return ApiResponse.success(StaffPermissionService.KNOWN);}
    @GetMapping("/{id}/permissions") public ApiResponse<List<String>> get(@PathVariable Long id){return ApiResponse.success(permissions.forUser(id));}
    @PutMapping("/{id}/permissions") public ApiResponse<List<String>> update(@PathVariable Long id,@RequestBody Command c){return ApiResponse.success(permissions.replace(id,c.permissions()));}
    public record Command(Set<String> permissions){}
}
