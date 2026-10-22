package com.core.beautyshop.modules.procurement.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcurementVariantLookupTest {
    @Mock CatalogFacade catalog;
    @InjectMocks ProcurementService service;

    @Test void trimsDeduplicatesAndPreservesCaseAndLeadingZerosWithoutWriting() {
        var variant = ProductVariantSummaryDto.builder().id(1L).sku("000Abc").isActive(false).build();
        when(catalog.getVariantSummariesForInventoryBySkus(Set.of("000Abc", "missing"))).thenReturn(List.of(variant));
        var result = service.lookupVariants(new ProcurementService.VariantLookup(List.of(" 000Abc ", "000Abc", "missing")));
        assertEquals(List.of(variant), result);
        assertFalse(result.getFirst().getIsActive());
        verify(catalog).getVariantSummariesForInventoryBySkus(Set.of("000Abc", "missing"));
        verifyNoMoreInteractions(catalog);
    }

    @Test void validatesInputBeforeQuery() {
        assertThrows(BusinessException.class, () -> service.lookupVariants(null));
        for (List<String> skus : Arrays.asList(null, List.<String>of(), Arrays.asList((String) null), List.of(" "),
                List.of("a".repeat(101)), Collections.nCopies(501, "A"))) {
            assertThrows(BusinessException.class, () -> service.lookupVariants(new ProcurementService.VariantLookup(skus)));
        }
        assertThrows(BusinessException.class, () -> service.lookupVariants(new ProcurementService.VariantLookup(List.of(123))));
        verifyNoInteractions(catalog);
    }
}
