package com.core.beautyshop.modules.spa.domain;
import jakarta.persistence.*;
import lombok.*;
@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TicketEntitlement {
    @Column(name = "total_sessions", nullable = false)
    private int total;
    @Column(name = "used_sessions", nullable = false)
    private int used;
}
