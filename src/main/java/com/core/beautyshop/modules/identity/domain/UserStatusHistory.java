package com.core.beautyshop.modules.identity.domain;

import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserStatusHistory extends Base {
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "old_status", nullable = false, length = 20) private AccountStatus oldStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 20) private AccountStatus newStatus;
    @Column(length = 500) private String reason;
}
