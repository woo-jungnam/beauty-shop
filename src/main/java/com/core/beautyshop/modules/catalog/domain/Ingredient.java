package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "ingredients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient extends Base {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "inci_name", nullable = false)
    private String inciName;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "functions", columnDefinition = "json")
    private List<String> functions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", columnDefinition = "json")
    private List<String> benefits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "potential_concerns", columnDefinition = "json")
    private List<String> potentialConcerns;

    @Column(name = "ewg_score")
    @Builder.Default
    private Integer ewgScore = 1;

    @Column(name = "is_active_ingredient")
    @Builder.Default
    private Boolean isActiveIngredient = false;
}
