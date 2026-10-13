package com.core.beautyshop.modules.inventory.domain;

import java.util.List;

import com.core.beautyshop.shared.domain.Base;
import com.core.beautyshop.modules.inventory.domain.enums.WarehouseType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse extends Base {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "ward", length = 100)
    private String ward;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "manager_name", length = 100)
    private String managerName;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_type", nullable = false, length = 30)
    private WarehouseType warehouseType = WarehouseType.BRANCH;
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL)
    private List<WarehouseStock> stocks;
}
