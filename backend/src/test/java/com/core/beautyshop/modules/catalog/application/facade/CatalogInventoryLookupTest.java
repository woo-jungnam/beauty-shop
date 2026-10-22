package com.core.beautyshop.modules.catalog.application.facade;

import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.catalog.domain.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogInventoryLookupTest {
    @Mock ProductVariantRepository variants;
    @InjectMocks CatalogFacadeImpl facade;

    @Test void filtersDatabaseCollationMatchesButAllowsInactiveSku() {
        var exact = ProductVariantSummaryDto.builder().id(1L).sku("Abc").isActive(false).build();
        var otherCase = ProductVariantSummaryDto.builder().id(2L).sku("ABC").isActive(true).build();
        when(variants.findVariantSummariesForInventoryBySkus(Set.of("Abc"))).thenReturn(List.of(otherCase, exact));
        assertEquals(List.of(exact), facade.getVariantSummariesForInventoryBySkus(List.of("Abc")));
        assertTrue(facade.getVariantSummariesForInventoryBySkus(List.of()).isEmpty());
    }
}
