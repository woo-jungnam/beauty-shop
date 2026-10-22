package com.core.beautyshop.modules.spa.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PurchasedServiceDetail {
    @Column(name = "service_name", nullable = false, length = 255)
    private String name;
    @Column(name = "unit_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitValue;
}
