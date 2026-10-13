package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "facility_blocks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FacilityBlock extends Base {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;
    @Column(nullable = false, length = 500)
    private String reason;
}
