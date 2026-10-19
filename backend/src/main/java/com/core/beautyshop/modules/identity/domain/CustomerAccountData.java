package com.core.beautyshop.modules.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Account-owned records are addressed only after checking the authenticated user. */
public final class CustomerAccountData {
    private CustomerAccountData() {}

    @Entity(name="CustomerSkinProfile") @Table(name="customer_skin_profiles") @Getter @Setter
    public static class SkinProfile {
        @Id private Long userId;
        @Column(length=100) private String skinType;
        @Column(length=100) private String sensitivity;
        @Column(length=1000) private String allergies;
        @Column(length=1000) private String contraindications;
        @Column(length=1000) private String preferredIngredients;
        @Column(length=2000) private String notes;
        @Column(nullable=false) private Instant updatedAt;
    }

    @Entity(name="CustomerAddress") @Table(name="customer_addresses",indexes=@Index(name="idx_customer_address_owner",columnList="user_id")) @Getter @Setter
    public static class Address {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
        @Column(name="user_id",nullable=false) private Long userId;
        @Column(nullable=false,length=100) private String recipientName;
        @Column(nullable=false,length=20) private String phone;
        @Column(nullable=false,length=500) private String address;
        @Column(nullable=false,length=100) private String ward;
        @Column(nullable=false,length=100) private String district;
        @Column(nullable=false,length=100) private String city;
        @Column(name="is_default",nullable=false) private boolean defaultAddress;
        @Column(nullable=false) private Instant updatedAt;
    }

    @Entity(name="PasswordRecovery") @Table(name="password_recoveries",indexes=@Index(name="idx_password_recovery_owner",columnList="user_id,created_at")) @Getter @Setter
    public static class PasswordRecovery {
        @Id @Column(length=64) private String tokenHash;
        @Column(name="user_id",nullable=false) private Long userId;
        @Column(name="created_at",nullable=false) private Instant createdAt;
        @Column(nullable=false) private Instant expiresAt;
        private Instant usedAt;
    }

    @Entity(name="NewsletterSubscription") @Table(name="newsletter_subscriptions") @Getter @Setter
    public static class NewsletterSubscription {
        @Id @Column(length=254) private String email;
        @Column(nullable=false) private Instant consentedAt;
        @Column(nullable=false) private boolean active;
    }
}
