package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@org.hibernate.annotations.Immutable
@Entity @Table(name = "appointment_action_history") @Getter @Setter @NoArgsConstructor
public class AppointmentActionHistory extends Base {
    @Column(name = "appointment_id", nullable = false, updatable = false) private Long appointmentId;
    @Column(name = "appointment_item_id", updatable = false) private Long appointmentItemId;
    @Column(name = "actor_user_id", updatable = false) private Long actorUserId;
    @Column(nullable = false, length = 40, updatable = false) private String action;
    @Column(name = "from_state", length = 40, updatable = false) private String fromState;
    @Column(name = "to_state", length = 40, updatable = false) private String toState;
    @Column(length = 2000, updatable = false) private String details;
}
