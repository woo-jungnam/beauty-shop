package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "facility_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"appointment_item_id", "facility_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FacilityAllocation extends Base {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "appointment_item_id", nullable = false)
    private AppointmentItem appointmentItem;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;
    @Column(nullable = false)
    private Integer quantity;
}
