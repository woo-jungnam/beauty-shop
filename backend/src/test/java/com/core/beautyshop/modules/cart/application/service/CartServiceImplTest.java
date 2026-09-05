package com.core.beautyshop.modules.cart.application.service;

import com.core.beautyshop.modules.cart.api.dto.CartResponse;
import com.core.beautyshop.modules.cart.domain.Cart;
import com.core.beautyshop.modules.cart.domain.CartItem;
import com.core.beautyshop.modules.cart.domain.CartItemRepository;
import com.core.beautyshop.modules.cart.domain.CartRepository;
import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CatalogFacade catalogFacade;

    @Mock
    private InventoryFacade inventoryFacade;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void testMergeCart_TransfersItemsFromGuestToUserCart() {
        String sessionId = "sess-12345";
        Long userId = 99L;

        Cart guestCart = Cart.builder()
                .sessionId(sessionId)
                .items(new ArrayList<>())
                .build();
        guestCart.setId(1L);

        CartItem guestItem = CartItem.builder()
                .cart(guestCart)
                .productVariantId(10L)
                .quantity(2)
                .build();
        guestItem.setId(101L);
        guestCart.getItems().add(guestItem);

        Cart userCart = Cart.builder()
                .userId(userId)
                .items(new ArrayList<>())
                .build();
        userCart.setId(2L);

        ProductVariantSummaryDto variantDto = ProductVariantSummaryDto.builder()
                .id(10L)
                .sku("SKU-001")
                .productName("Kem Dưỡng Da")
                .price(new BigDecimal("200000"))
                .build();

        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(userCart));
        when(cartItemRepository.findByCartIdAndProductVariantId(2L, 10L)).thenReturn(Optional.empty());
        when(cartRepository.findById(2L)).thenAnswer(inv -> {
            userCart.getItems().add(guestItem);
            return Optional.of(userCart);
        });
        when(catalogFacade.getVariantSummariesByIds(any())).thenReturn(Map.of(10L, variantDto));

        CartResponse merged = cartService.mergeCart(sessionId, userId);

        assertNotNull(merged);
        assertEquals(1, merged.getItems().size());
        verify(cartItemRepository).save(any(CartItem.class));
        verify(cartItemRepository).deleteAll(any());
        verify(cartRepository).delete(guestCart);
    }

    @Test
    void testAddToCart_NegativeOrZeroQuantity_ThrowsException() {
        com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest request = 
                new com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest();
        request.setVariantId(10L);
        request.setQuantity(0);

        assertThrows(com.core.beautyshop.shared.exception.BusinessException.class, 
                () -> cartService.addToCart(1L, request));
    }

    @Test
    void testAddToCart_InsufficientStock_ThrowsException() {
        com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest request = 
                new com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest();
        request.setVariantId(10L);
        request.setQuantity(100);

        ProductVariantSummaryDto variantDto = ProductVariantSummaryDto.builder()
                .id(10L)
                .sku("SKU-001")
                .productName("Kem Dưỡng Da")
                .price(new BigDecimal("200000"))
                .build();

        when(catalogFacade.getVariantSummaryById(10L)).thenReturn(variantDto);
        when(inventoryFacade.isStockAvailable(10L, 100)).thenReturn(false);

        assertThrows(com.core.beautyshop.modules.inventory.api.exception.InsufficientStockException.class, 
                () -> cartService.addToCart(1L, request));
    }

    @Test
    void testUpdateCartItem_ZeroQuantity_DeletesItem() {
        Cart cart = Cart.builder().items(new ArrayList<>()).build();
        cart.setId(1L);

        CartItem item = CartItem.builder()
                .cart(cart)
                .productVariantId(10L)
                .quantity(3)
                .build();
        item.setId(100L);

        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

        CartResponse response = cartService.updateCartItem(1L, 100L, 0);

        assertNotNull(response);
        verify(cartItemRepository).delete(item);
    }
}
