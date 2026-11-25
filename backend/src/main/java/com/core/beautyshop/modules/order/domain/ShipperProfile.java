package com.core.beautyshop.modules.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "shipper_profiles")
@Getter
@Setter
@NoArgsConstructor
public class ShipperProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "vehicle_type", length = 40)
    private String vehicleType;

    @Column(name = "vehicle_plate", length = 30)
    private String vehiclePlate;

    @Column(name = "service_area", length = 150)
    private String serviceArea;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "work_shift", length = 60)
    private String workShift;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

