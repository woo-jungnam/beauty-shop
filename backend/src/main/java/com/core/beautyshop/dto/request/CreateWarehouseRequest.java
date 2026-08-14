package com.core.beautyshop.dto.request;

import com.core.beautyshop.entities.warehouse.enums.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWarehouseRequest {

    @NotBlank(message = "Tên kho không được để trống")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "Mã kho không được để trống")
    @Size(max = 50)
    private String code;

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
}
