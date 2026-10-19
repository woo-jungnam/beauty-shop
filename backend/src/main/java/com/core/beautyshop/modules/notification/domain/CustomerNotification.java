package com.core.beautyshop.modules.notification.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="customer_notifications",uniqueConstraints=@UniqueConstraint(name="uk_customer_notification_event",columnNames={"user_id","event_key"}),indexes=@Index(name="idx_customer_notification_owner",columnList="user_id,id")) @Getter @Setter
public class CustomerNotification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="event_key",nullable=false,length=160) private String eventKey;
    @Column(nullable=false,length=30) private String type;
    @Column(nullable=false,length=200) private String title;
    @Column(nullable=false,length=2000) private String body;
    @Column(length=300) private String target;
    @Column(nullable=false) private Instant createdAt;
    private Instant readAt;
}
