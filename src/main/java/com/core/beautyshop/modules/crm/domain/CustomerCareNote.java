package com.core.beautyshop.modules.crm.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "customer_care_notes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerCareNote extends Base {
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "note_type", nullable = false, length = 30) private String noteType;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "skin_profile", length = 1000) private String skinProfile;
    @Column(length = 1000) private String allergies;
    @Column(length = 1000) private String contraindications;
    @Column(name = "follow_up_at") private Instant followUpAt;
}
