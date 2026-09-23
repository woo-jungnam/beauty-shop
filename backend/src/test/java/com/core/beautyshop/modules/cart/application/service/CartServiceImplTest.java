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
import org.springframework.security.access.AccessDeniedException;

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

        when(cartRepository.findGuestBySessionIdForUpdate(sessionId)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(userCart));
        when(cartItemRepository.findByCartIdAndProductVariantId(2L, 10L)).thenReturn(Optional.empty());
        when(inventoryFacade.isStockAvailable(10L, 2)).thenReturn(true);
        when(cartRepository.findById(2L)).thenReturn(Optional.of(userCart));
        when(catalogFacade.getVariantSummariesByIds(any())).thenReturn(Map.of(10L, variantDto));

        CartResponse merged = cartService.mergeCart(sessionId, userId);

        assertNotNull(merged);
        assertEquals(1, merged.getItems().size());
        verify(cartItemRepository).save(any(CartItem.class));
        verify(cartItemRepository).delete(guestItem);
        verify(cartRepository).delete(guestCart);
    }

    @Test
    void testMergeCart_SkipsOutOfStockItemAndKeepsItInGuestCart() {
        String sessionId = "sess-partial";
        Long userId = 99L;

        Cart guestCart = Cart.builder().sessionId(sessionId).items(new ArrayList<>()).build();
        guestCart.setId(1L);
        CartItem availableItem = CartItem.builder().cart(guestCart).productVariantId(10L).quantity(1).build();
        CartItem unavailableItem = CartItem.builder().cart(guestCart).productVariantId(20L).quantity(2).build();
        availableItem.setId(101L);
        unavailableItem.setId(102L);
        guestCart.getItems().addAll(java.util.List.of(availableItem, unavailableItem));

        Cart userCart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        userCart.setId(2L);
        ProductVariantSummaryDto availableVariant = ProductVariantSummaryDto.builder()
                .id(10L).sku("SKU-AVAILABLE").price(new BigDecimal("100000")).build();

        when(cartRepository.findGuestBySessionIdForUpdate(sessionId)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(userCart));
        when(cartItemRepository.findByCartIdAndProductVariantId(2L, 10L)).thenReturn(Optional.empty());
        when(cartItemRepository.findByCartIdAndProductVariantId(2L, 20L)).thenReturn(Optional.empty());
        when(inventoryFacade.isStockAvailable(10L, 1)).thenReturn(true);
        when(inventoryFacade.isStockAvailable(20L, 2)).thenReturn(false);
        when(cartRepository.findById(2L)).thenReturn(Optional.of(userCart));
        when(catalogFacade.getVariantSummariesByIds(any())).thenReturn(Map.of(10L, availableVariant));

        CartResponse merged = cartService.mergeCart(sessionId, userId);

        assertEquals(1, merged.getItems().size());
        assertEquals(10L, merged.getItems().get(0).getVariantId());
        assertEquals(java.util.List.of(unavailableItem), guestCart.getItems());
        verify(cartItemRepository).delete(availableItem);
        verify(cartItemRepository, never()).delete(unavailableItem);
        verify(cartRepository).save(guestCart);
        verify(cartRepository, never()).delete(guestCart);
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
    void deletedVariantIsMarkedUnavailableInsteadOfBeingShownAtZeroPrice() {
        Cart cart = Cart.builder().userId(99L).items(new ArrayList<>()).build();
        cart.setId(1L);
        cart.getItems().add(CartItem.builder()
                .cart(cart)
                .productVariantId(404L)
                .quantity(1)
                .build());
        when(cartRepository.findByUserId(99L)).thenReturn(Optional.of(cart));
        when(catalogFacade.getVariantSummariesByIds(java.util.List.of(404L))).thenReturn(Map.of());

        CartResponse response = cartService.getCart(99L, null);

        assertFalse(response.getItems().get(0).getAvailable());
        assertNull(response.getItems().get(0).getPrice());
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
    void addToNewGuestCartReturnsTheNewItemImmediately() {
        com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest request =
                new com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest();
        request.setSessionId("guest-session");
        request.setVariantId(10L);
        request.setQuantity(2);

        ProductVariantSummaryDto variant = ProductVariantSummaryDto.builder()
                .id(10L)
                .sku("SKU-001")
                .productName("Product")
                .price(new BigDecimal("200000"))
                .build();

        when(catalogFacade.getVariantSummaryById(10L)).thenReturn(variant);
        when(inventoryFacade.isStockAvailable(10L, 2)).thenReturn(true);
        when(cartRepository.findGuestBySessionIdForUpdate("guest-session")).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(cartItemRepository.findByCartIdAndProductVariantId(1L, 10L)).thenReturn(Optional.empty());
        when(cartRepository.findById(1L)).thenReturn(Optional.empty());
        when(catalogFacade.getVariantSummariesByIds(java.util.List.of(10L)))
                .thenReturn(Map.of(10L, variant));

        CartResponse response = cartService.addToCart(null, request);

        assertEquals(1, response.getItems().size());
        assertEquals(10L, response.getItems().get(0).getVariantId());
        assertEquals(2, response.getItems().get(0).getQuantity());
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

        when(cartRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

        CartResponse response = cartService.updateCartItem(1L, 100L, 0);

        assertNotNull(response);
        verify(cartItemRepository).delete(item);
    }

    @Test
    void guestCannotUpdateAnotherSessionCart() {
        Cart cart = Cart.builder()
                .sessionId("owner-session")
                .items(new ArrayList<>())
                .build();
        cart.setId(1L);

        CartItem item = CartItem.builder()
                .cart(cart)
                .productVariantId(10L)
                .quantity(1)
                .build();
        item.setId(100L);

        when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));

        assertThrows(AccessDeniedException.class,
                () -> cartService.updateCartItem(100L, 2, "attacker-session"));
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void testClearCart_ClearsItemsAndSavesCart_WithoutCallingDeleteAll() {
        Cart cart = Cart.builder().items(new ArrayList<>()).build();
        cart.setId(5L);

        CartItem item = CartItem.builder().cart(cart).productVariantId(10L).quantity(2).build();
        cart.getItems().add(item);

        when(cartRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(cart));

        cartService.clearCart(5L);

        assertTrue(cart.getItems().isEmpty());
        verify(cartRepository).save(cart);
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void authenticatedCheckoutDoesNotClearAnUnrelatedGuestCart() {
        Cart userCart = Cart.builder().userId(10L).items(new ArrayList<>()).build();
        userCart.setId(5L);
        userCart.getItems().add(CartItem.builder().cart(userCart).productVariantId(1L).quantity(1).build());

        Cart guestCart = Cart.builder().sessionId("guest-session").items(new ArrayList<>()).build();
        guestCart.setId(6L);
        guestCart.getItems().add(CartItem.builder().cart(guestCart).productVariantId(2L).quantity(1).build());

        when(cartRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(userCart));

        cartService.clearCartByUserIdOrSessionId(10L, "guest-session");

        assertTrue(userCart.getItems().isEmpty());
        assertFalse(guestCart.getItems().isEmpty());
        verify(cartRepository).save(userCart);
        verify(cartRepository, never()).save(guestCart);
    }
}
