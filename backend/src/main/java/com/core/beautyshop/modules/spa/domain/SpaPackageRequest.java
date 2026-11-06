package com.core.beautyshop.modules.spa.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="spa_package_requests") @Getter @Setter
public class SpaPackageRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long ticketId;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false, length=20) private String type;
    private Integer extensionDays;
    @Column(nullable=false, length=1000) private String reason;
    @Column(nullable=false, length=30) private String status="PENDING_APPROVAL";
    @Column(length=1000) private String reviewReason;
    private Long reviewedBy;
    private Instant reviewedAt;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}
