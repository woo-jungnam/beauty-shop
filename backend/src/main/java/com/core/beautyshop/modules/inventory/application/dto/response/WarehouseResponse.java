package com.core.beautyshop.modules.inventory.application.dto.response;

import com.core.beautyshop.modules.inventory.domain.enums.WarehouseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Boolean isActive;
}
