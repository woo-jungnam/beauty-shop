package com.core.beautyshop.modules.procurement.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "suppliers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Supplier extends Base {
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "contact_name", length = 150) private String contactName;
    @Column(length = 150) private String email;
    @Column(length = 30) private String phone;
    @Column(length = 500) private String address;
    @Column(name = "tax_code", length = 50) private String taxCode;
    @Column(nullable = false) @Builder.Default private Boolean active = true;
}
