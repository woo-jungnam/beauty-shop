package com.core.beautyshop.modules.inventory.application.dto.request;

import com.core.beautyshop.modules.inventory.domain.enums.WarehouseType;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Tạo kho active; mã code duy nhất, mặc định warehouseType=BRANCH")
public class CreateWarehouseRequest {

    @NotBlank(message = "Tên kho không được để trống")
    @Size(max = 150)
    @Schema(description = "Tên kho", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 150, example = "Kho trung tâm")
    private String name;

    @NotBlank(message = "Mã kho không được để trống")
    @Size(max = 50)
    @Schema(description = "Mã kho duy nhất", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50, example = "WH-CENTRAL")
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

    @Schema(description = "Loại kho CENTRAL/BRANCH/TRANSIT; bỏ trống mặc định BRANCH", defaultValue = "BRANCH")
    private WarehouseType warehouseType;
}
