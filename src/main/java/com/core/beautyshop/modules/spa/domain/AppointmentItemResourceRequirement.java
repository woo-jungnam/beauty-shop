package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "appointment_item_resource_requirements", uniqueConstraints = @UniqueConstraint(columnNames = {"appointment_item_id", "resource_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppointmentItemResourceRequirement extends Base {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "appointment_item_id", nullable = false)
    private AppointmentItem appointmentItem;
    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;
    @Column(nullable = false)
    private Integer units;
}
