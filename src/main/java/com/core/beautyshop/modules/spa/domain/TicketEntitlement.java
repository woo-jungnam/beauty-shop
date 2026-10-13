package com.core.beautyshop.modules.spa.domain;
import jakarta.persistence.*;
import lombok.*;
@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TicketEntitlement {
    @Column(name = "total_sessions", nullable = false)
    private int total;
    @Column(name = "used_sessions", nullable = false)
    private int used;
    @Column(name = "reserved_sessions", nullable = false)
    private int reserved;

    public TicketEntitlement(int total, int used) { this.total = total; this.used = used; }
    public int available() { return total - used - reserved; }
}
