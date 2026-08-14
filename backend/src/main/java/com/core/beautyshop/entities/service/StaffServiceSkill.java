package com.core.beautyshop.entities.service;

import com.core.beautyshop.entities.common.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "staff_service_skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffServiceSkill extends Base {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private BeautyService service;

    @Column(name = "is_certified")
    @Builder.Default
    private Boolean isCertified = false;

    // Additional fields like experience level could be added here
}
