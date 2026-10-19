package com.core.beautyshop.modules.catalog.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="customer_wishlist",uniqueConstraints=@UniqueConstraint(name="uk_customer_wishlist",columnNames={"user_id","product_id"})) @Getter @Setter
public class CustomerWishlistEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="product_id",nullable=false) private Long productId;
    @Column(nullable=false) private Instant createdAt;
}
