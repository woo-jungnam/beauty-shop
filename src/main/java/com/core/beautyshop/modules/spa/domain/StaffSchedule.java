package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.enums.StaffScheduleStatus;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

import java.time.*;

@Entity
@Table(name = "staff_schedules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffSchedule extends Base {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StaffScheduleStatus status = StaffScheduleStatus.SCHEDULED;
    @Column(length = 500)
    private String note;
}
