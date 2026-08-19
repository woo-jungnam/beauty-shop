package com.core.beautyshop.modules.cart.application.service;

import com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest;
import com.core.beautyshop.modules.cart.application.dto.response.CartItemResponse;
import com.core.beautyshop.modules.cart.application.dto.response.CartResponse;
import com.core.beautyshop.modules.cart.domain.Cart;
import com.core.beautyshop.modules.cart.domain.CartItem;
import com.core.beautyshop.modules.cart.domain.CartItemRepository;
import com.core.beautyshop.modules.cart.domain.CartRepository;
import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.domain.exception.InsufficientStockException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogFacade catalogFacade;
    private final InventoryFacade inventoryFacade;
    private final IdentityFacade identityFacade;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(String sessionId) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        return getCart(userId, sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId, String sessionId) {
        Cart cart = getCartEntity(userId, sessionId);
        return mapToCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        return addToCart(userId, request);
    }

    private Cart getCartEntity(Long userId, String sessionId) {
        if (userId != null) {
            return cartRepository.findByUserId(userId).orElse(null);
        } else if (sessionId != null) {
            return cartRepository.findBySessionId(sessionId).orElse(null);
        }
        return null;
    }

    @Override
    @Transactional
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        ProductVariantSummaryDto variant = catalogFacade.getVariantSummaryById(request.getVariantId());

        if (!inventoryFacade.isStockAvailable(variant.getId(), request.getQuantity())) {
            throw new InsufficientStockException("Không đủ hàng trong kho cho sản phẩm này");
        }

        Cart cart = getCartEntity(userId, request.getSessionId());

        if (cart == null) {
            cart = Cart.builder()
                    .sessionId(request.getSessionId())
                    .userId(userId)
                    .items(new ArrayList<>())
                    .build();
            cart = cartRepository.save(cart);
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndProductVariantId(cart.getId(), variant.getId());

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            int newQty = existingItem.getQuantity() + request.getQuantity();
            if (!inventoryFacade.isStockAvailable(variant.getId(), newQty)) {
                throw new InsufficientStockException("Không đủ hàng trong kho cho tổng số lượng yêu cầu trong giỏ hàng");
            }
            existingItem.setQuantity(newQty);
            cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productVariantId(variant.getId())
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(newItem);
        }

        Cart updatedCart = cartRepository.findById(cart.getId()).orElse(cart);
        return mapToCartResponse(updatedCart);
    }

    @Override
    @Transactional
    public void clearCart() {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (userId != null) {
            cartRepository.findByUserId(userId).ifPresent(cart -> clearCart(cart.getId()));
        }
    }

    @Override
    @Transactional
    public void clearCart(Long cartId) {
        cartRepository.findById(cartId).ifPresent(cart -> {
            cartItemRepository.deleteAll(cart.getItems());
            cart.getItems().clear();
        });
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(Long itemId, Integer quantity) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng"));
        return updateCartItem(item.getCart().getId(), itemId, quantity);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(Long cartId, Long itemId, Integer quantity) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giỏ hàng"));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng"));

        if (!item.getCart().getId().equals(cartId)) {
            throw new ResourceNotFoundException("Sản phẩm không thuộc giỏ hàng này");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            if (!inventoryFacade.isStockAvailable(item.getProductVariantId(), quantity)) {
                throw new InsufficientStockException("Không đủ hàng trong kho");
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        Cart updatedCart = cartRepository.findById(cart.getId()).orElse(cart);
        return mapToCartResponse(updatedCart);
    }

    @Override
    @Transactional
    public CartResponse removeCartItem(Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng"));
        return removeCartItem(item.getCart().getId(), itemId);
    }

    @Override
    @Transactional
    public CartResponse removeCartItem(Long cartId, Long itemId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giỏ hàng"));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng"));

        if (!item.getCart().getId().equals(cartId)) {
            throw new ResourceNotFoundException("Sản phẩm không thuộc giỏ hàng này");
        }

        cartItemRepository.delete(item);

        Cart updatedCart = cartRepository.findById(cart.getId()).orElse(cart);
        return mapToCartResponse(updatedCart);
    }

    private CartResponse mapToCartResponse(Cart cart) {
        if (cart == null) return null;
        List<CartItemResponse> itemResponses = cart.getItems() != null
                ? cart.getItems().stream().map(item -> {
                    ProductVariantSummaryDto variant = catalogFacade.findVariantSummaryById(item.getProductVariantId()).orElse(null);
                    return CartItemResponse.of(item, variant);
                }).collect(Collectors.toList())
                : List.of();
        return CartResponse.of(cart, itemResponses);
    }
}
