package com.core.beautyshop.modules.inventory.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiringVariantStockDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long variantId;
    private LocalDate earliestExpirationDate;
    private Integer availableQuantity;
}
