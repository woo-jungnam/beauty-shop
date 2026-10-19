package com.core.beautyshop.modules.crm.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="crm_work_items",indexes={@Index(name="idx_crm_work_due",columnList="status,due_at"),@Index(name="idx_crm_work_customer",columnList="customer_id")}) @Getter @Setter
public class CrmWorkItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="customer_id",nullable=false) private Long customerId;
    @Column(unique=true) private Long sourceNoteId;
    @Column(nullable=false,length=30) private String kind;
    @Column(nullable=false,length=200) private String title;
    @Column(length=10000) private String description;
    private Long assigneeId;
    @Column(name="due_at") private Instant dueAt;
    @Column(nullable=false,length=30) private String status;
    @Column(length=2000) private String outcome;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    private Instant completedAt;
    @Version private long version;
}
