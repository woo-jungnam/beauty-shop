package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "facilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facility extends Base {

    @Column(name = "name", nullable = false, length = 100)
    private String name; // e.g., "Room 1", "Bed A"

    @Column(name = "description", length = 250)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Optional: add fields like capacity or location if scaling to multiple branches
}
