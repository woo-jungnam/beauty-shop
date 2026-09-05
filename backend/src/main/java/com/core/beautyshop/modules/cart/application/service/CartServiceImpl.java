package com.core.beautyshop.modules.cart.application.service;

import com.core.beautyshop.modules.cart.application.dto.request.AddToCartRequest;
import com.core.beautyshop.modules.cart.api.dto.CartItemResponse;
import com.core.beautyshop.modules.cart.api.dto.CartResponse;
import com.core.beautyshop.modules.cart.domain.Cart;
import com.core.beautyshop.modules.cart.domain.CartItem;
import com.core.beautyshop.modules.cart.domain.CartItemRepository;
import com.core.beautyshop.modules.cart.domain.CartRepository;
import com.core.beautyshop.modules.catalog.api.CatalogFacade;
import com.core.beautyshop.modules.catalog.api.dto.ProductVariantSummaryDto;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.modules.inventory.api.exception.InsufficientStockException;
import com.core.beautyshop.shared.exception.BusinessException;
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
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessException("Số lượng sản phẩm thêm vào giỏ hàng phải lớn hơn 0");
        }

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
        if (quantity == null) {
            throw new BusinessException("Số lượng không được để trống");
        }

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
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            return CartResponse.of(cart, List.of());
        }

        List<Long> variantIds = cart.getItems().stream()
                .map(CartItem::getProductVariantId)
                .collect(Collectors.toList());

        java.util.Map<Long, ProductVariantSummaryDto> variantMap = catalogFacade.getVariantSummariesByIds(variantIds);

        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> {
                    ProductVariantSummaryDto variant = variantMap.get(item.getProductVariantId());
                    return CartItemResponse.of(item, variant);
                })
                .collect(Collectors.toList());

        return CartResponse.of(cart, itemResponses);
    }

    @Override
    @Transactional
    public CartResponse mergeCart(String sessionId, Long userId) {
        if (sessionId == null || sessionId.trim().isEmpty() || userId == null) {
            return getCart(userId, sessionId);
        }

        Optional<Cart> guestCartOpt = cartRepository.findBySessionId(sessionId);
        if (guestCartOpt.isEmpty() || guestCartOpt.get().getItems() == null || guestCartOpt.get().getItems().isEmpty()) {
            return getCart(userId, null);
        }

        Cart guestCart = guestCartOpt.get();
        if (userId.equals(guestCart.getUserId())) {
            return mapToCartResponse(guestCart);
        }

        Cart userCart = cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart newCart = Cart.builder()
                    .userId(userId)
                    .items(new ArrayList<>())
                    .build();
            return cartRepository.save(newCart);
        });

        for (CartItem guestItem : guestCart.getItems()) {
            Optional<CartItem> existingUserItem = cartItemRepository.findByCartIdAndProductVariantId(
                    userCart.getId(), guestItem.getProductVariantId());

            if (existingUserItem.isPresent()) {
                CartItem item = existingUserItem.get();
                item.setQuantity(item.getQuantity() + guestItem.getQuantity());
                cartItemRepository.save(item);
            } else {
                CartItem newItem = CartItem.builder()
                        .cart(userCart)
                        .productVariantId(guestItem.getProductVariantId())
                        .quantity(guestItem.getQuantity())
                        .build();
                cartItemRepository.save(newItem);
            }
        }

        // Xóa giỏ hàng vãng lai sau khi gộp
        cartItemRepository.deleteAll(guestCart.getItems());
        cartRepository.delete(guestCart);

        Cart updatedUserCart = cartRepository.findById(userCart.getId()).orElse(userCart);
        return mapToCartResponse(updatedUserCart);
    }
}
