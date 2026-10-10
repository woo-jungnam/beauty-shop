package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_facility_requirements", uniqueConstraints = @UniqueConstraint(columnNames = {"service_id", "resource_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceFacilityRequirement extends Base {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "service_id", nullable = false)
    private BeautyService service;
    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;
    @Column(nullable = false)
    private Integer units;
}
