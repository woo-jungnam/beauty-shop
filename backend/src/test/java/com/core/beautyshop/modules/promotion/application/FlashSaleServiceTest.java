package com.core.beautyshop.modules.promotion.application;

import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.promotion.domain.FlashSaleCampaignRepository;
import com.core.beautyshop.modules.promotion.domain.FlashSaleItemRepository;
import com.core.beautyshop.modules.promotion.domain.FlashSaleOrderAllocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class FlashSaleServiceTest {

    @Mock FlashSaleCampaignRepository campaignRepository;
    @Mock FlashSaleItemRepository itemRepository;
    @Mock FlashSaleOrderAllocationRepository allocationRepository;
    @Mock CatalogFacade catalogFacade;
    @Mock InventoryFacade inventoryFacade;
    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks FlashSaleService service;

    @Test
    void getCandidateItemsExecutesQueryAndReturnsList() {
        doAnswer(invocation -> null).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));

        List<FlashSaleService.FlashSaleCandidateItem> items = service.getCandidateItems();

        assertNotNull(items);
    }
}
