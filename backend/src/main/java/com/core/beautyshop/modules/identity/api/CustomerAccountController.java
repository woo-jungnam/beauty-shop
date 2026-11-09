package com.core.beautyshop.modules.identity.api;

import com.core.beautyshop.modules.identity.application.service.CustomerAccountService;
import com.core.beautyshop.modules.identity.application.service.CustomerAccountService.*;
import com.core.beautyshop.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/users") @PreAuthorize("isAuthenticated()")
@Tag(name = "Hồ sơ tài khoản & Sổ địa chỉ", description = "Quản lý hồ sơ da liễu, sổ địa chỉ giao hàng, đổi mật khẩu và thống kê đơn hàng cá nhân")
public class CustomerAccountController {
    private final CustomerAccountService accounts;

    @Operation(summary = "Xem hồ sơ da liễu cá nhân", description = "Lấy thông tin loại da, tình trạng mụn/nám và các vấn đề da liễu người dùng đã lưu")
    @GetMapping("/skin-profile") public ApiResponse<SkinView> skin(){return ApiResponse.success(accounts.skin());}

    @Operation(summary = "Cập nhật hồ sơ da liễu cá nhân", description = "Lưu hoặc chỉnh sửa khảo sát loại da và tình trạng da của khách hàng")
    @PutMapping("/skin-profile") public ApiResponse<SkinView> saveSkin(@Valid @RequestBody SkinCommand c){return ApiResponse.success(accounts.updateSkin(c));}

    @Operation(summary = "Danh sách sổ địa chỉ nhận hàng", description = "Lấy danh sách các địa chỉ giao hàng đã lưu kèm cờ mặc định")
    @GetMapping("/addresses") public ApiResponse<List<AddressView>> addresses(){return ApiResponse.success(accounts.addresses());}

    @Operation(summary = "Thêm địa chỉ giao hàng mới", description = "Tạo mới một địa chỉ nhận hàng vào sổ địa chỉ cá nhân")
    @PostMapping("/addresses") public ApiResponse<AddressView> add(@Valid @RequestBody AddressCommand c){return ApiResponse.created(accounts.saveAddress(null,c),"Đã lưu địa chỉ");}

    @Operation(summary = "Chỉnh sửa địa chỉ giao hàng", description = "Cập nhật thông tin người nhận, số điện thoại hoặc địa chỉ cụ thể")
    @PutMapping("/addresses/{id}") public ApiResponse<AddressView> update(@PathVariable Long id,@Valid @RequestBody AddressCommand c){return ApiResponse.success(accounts.saveAddress(id,c));}

    @Operation(summary = "Xóa địa chỉ giao hàng", description = "Xóa bỏ một địa chỉ khỏi sổ địa chỉ cá nhân")
    @DeleteMapping("/addresses/{id}") public ApiResponse<Void> delete(@PathVariable Long id){accounts.deleteAddress(id);return ApiResponse.success(null);}

    @Operation(summary = "Đổi mật khẩu tài khoản", description = "Người dùng đổi mật khẩu đăng nhập bằng mật khẩu cũ và mật khẩu mới")
    @PutMapping("/password") public ApiResponse<Void> password(@Valid @RequestBody PasswordChange c){accounts.changePassword(c);return ApiResponse.success(null,"Đã đổi mật khẩu. Vui lòng đăng nhập lại trên các thiết bị");}

    @Operation(summary = "Thống kê đơn hàng và chi tiêu cá nhân", description = "Xem tổng số đơn hàng đã đặt, số đơn thành công và tổng tiền đã chi tiêu")
    @GetMapping("/order-statistics") public ApiResponse<OrderStatistics> statistics(){return ApiResponse.success(accounts.statistics());}
}
