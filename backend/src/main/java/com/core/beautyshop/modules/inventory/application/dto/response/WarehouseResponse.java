package com.core.beautyshop.modules.inventory.application.dto.response;

import com.core.beautyshop.modules.inventory.domain.enums.WarehouseType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kho chưa xóa; isActive mô tả hoạt động, độc lập với loại CENTRAL/BRANCH/TRANSIT")
public class WarehouseResponse {
    private Long id;
    private String name;
    private String code;
    private String address;
    private String ward;
    private String district;
    private String city;
    private String phone;
    private String managerName;
    private WarehouseType warehouseType;
    @Schema(description = "Kho có đang hoạt động; false vẫn có thể xuất hiện trong danh sách quản trị")
    private Boolean isActive;
}
