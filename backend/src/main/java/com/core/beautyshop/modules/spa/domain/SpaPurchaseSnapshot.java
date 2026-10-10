package com.core.beautyshop.modules.spa.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "spa_purchase_snapshots") @Getter @Setter @NoArgsConstructor
public class SpaPurchaseSnapshot {
    @Id @Column(name = "order_id")
    private Long orderId;
    @Column(name = "package_id", nullable = false)
    private Long packageId;
    @Column(name = "validity_days")
    private Integer validityDays;
    @Column(name="expiry_check_mode", nullable=false, length=30)
    private String expiryCheckMode = "BOOKING_TIME";
    @ElementCollection
    @CollectionTable(name = "spa_purchase_entitlements", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "service_id")
    @Column(name = "quantity", nullable = false)
    private java.util.Map<Long, Integer> entitlements = new java.util.HashMap<>();
}
