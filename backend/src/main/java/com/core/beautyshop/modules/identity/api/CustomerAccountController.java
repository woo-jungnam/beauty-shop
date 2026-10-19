package com.core.beautyshop.modules.identity.api;

import com.core.beautyshop.modules.identity.application.service.CustomerAccountService;
import com.core.beautyshop.modules.identity.application.service.CustomerAccountService.*;
import com.core.beautyshop.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/users") @PreAuthorize("isAuthenticated()")
public class CustomerAccountController {
    private final CustomerAccountService accounts;
    @GetMapping("/skin-profile") public ApiResponse<SkinView> skin(){return ApiResponse.success(accounts.skin());}
    @PutMapping("/skin-profile") public ApiResponse<SkinView> saveSkin(@Valid @RequestBody SkinCommand c){return ApiResponse.success(accounts.updateSkin(c));}
    @GetMapping("/addresses") public ApiResponse<List<AddressView>> addresses(){return ApiResponse.success(accounts.addresses());}
    @PostMapping("/addresses") public ApiResponse<AddressView> add(@Valid @RequestBody AddressCommand c){return ApiResponse.created(accounts.saveAddress(null,c),"Đã lưu địa chỉ");}
    @PutMapping("/addresses/{id}") public ApiResponse<AddressView> update(@PathVariable Long id,@Valid @RequestBody AddressCommand c){return ApiResponse.success(accounts.saveAddress(id,c));}
    @DeleteMapping("/addresses/{id}") public ApiResponse<Void> delete(@PathVariable Long id){accounts.deleteAddress(id);return ApiResponse.success(null);}
    @PutMapping("/password") public ApiResponse<Void> password(@Valid @RequestBody PasswordChange c){accounts.changePassword(c);return ApiResponse.success(null,"Đã đổi mật khẩu. Vui lòng đăng nhập lại trên các thiết bị");}
    @GetMapping("/order-statistics") public ApiResponse<OrderStatistics> statistics(){return ApiResponse.success(accounts.statistics());}
}
