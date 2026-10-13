package com.core.beautyshop.modules.inventory.application.dto.request;

import com.core.beautyshop.modules.inventory.domain.enums.WarehouseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cập nhật các trường khác null; không đổi code và không xóa giá trị cũ bằng null")
public class UpdateWarehouseRequest {

    @Size(max = 150)
    private String name;

    @Size(max = 500)
    private String address;

    @Size(max = 100)
    private String ward;

    @Size(max = 100)
    private String district;

    @Size(max = 100)
    private String city;

    @Size(max = 20)
    private String phone;

    @Size(max = 100)
    private String managerName;

    private WarehouseType warehouseType;

    @Schema(description = "true hoạt động; false chặn nhập/điều chuyển mới qua service kho")
    private Boolean isActive;
}
