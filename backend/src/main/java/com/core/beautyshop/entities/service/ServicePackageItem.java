package com.core.beautyshop.entities.service;

import com.core.beautyshop.entities.common.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_package_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackageItem extends Base {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private ServicePackage servicePackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private BeautyService service;

    @Column(name = "quantity", nullable = false)
    private Integer quantity; // e.g., 10 sessions
}
