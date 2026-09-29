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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @Override
    @Transactional
    public CartResponse lockCart(Long userId, String sessionId) {
        Cart cart = userId != null ? cartRepository.findByUserIdForUpdate(userId).orElse(null)
                : cartRepository.findGuestBySessionIdForUpdate(sessionId).orElse(null);
        return mapToCartResponse(cart);
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

        if (userId == null && (request.getSessionId() == null || request.getSessionId().isBlank())) {
            throw new BusinessException("Session ID is required for a guest cart");
        }

        ProductVariantSummaryDto variant = catalogFacade.getVariantSummaryById(request.getVariantId());

        if (!inventoryFacade.isStockAvailable(variant.getId(), request.getQuantity())) {
            throw new InsufficientStockException("Không đủ hàng trong kho cho sản phẩm này");
        }

        Cart cart = userId != null ? cartRepository.findByUserIdForUpdate(userId).orElse(null)
                : cartRepository.findGuestBySessionIdForUpdate(request.getSessionId()).orElse(null);

        if (cart == null) {
            cart = Cart.builder()
                    .sessionId(userId == null ? request.getSessionId() : null)
                    .userId(userId)
                    .items(new ArrayList<>())
                    .build();
            cart = cartRepository.save(cart);
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndProductVariantId(cart.getId(), variant.getId());

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            int newQty = Math.addExact(existingItem.getQuantity(), request.getQuantity());
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
    public void clearCart(String sessionId) {
        Long userId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        if (userId != null) {
            cartRepository.findByUserIdForUpdate(userId).ifPresent(cart -> clearCart(cart.getId()));
        } else if (sessionId != null && !sessionId.isBlank()) {
            cartRepository.findBySessionId(sessionId).ifPresent(cart -> {
                assertCartOwner(cart, sessionId);
                clearCart(cart.getId());
            });
        } else {
            throw new AccessDeniedException("Session ID is required for a guest cart");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void clearCart(Long cartId) {
        cartRepository.findByIdForUpdate(cartId).ifPresent(this::clearCartItems);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void clearCartByUserIdOrSessionId(Long userId, String sessionId) {
        if (userId != null) {
            cartRepository.findByUserIdForUpdate(userId).ifPresent(this::clearCartItems);
        }
        else if (sessionId != null && !sessionId.isBlank()) {
            cartRepository.findGuestBySessionIdForUpdate(sessionId).ifPresent(this::clearCartItems);
        }
    }

    private void clearCartItems(Cart cart) {
        if (cart.getItems() != null) {
            cart.getItems().clear();
        }
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(Long itemId, Integer quantity, String sessionId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng"));
        assertCartOwner(item.getCart(), sessionId);
        return updateCartItem(item.getCart().getId(), itemId, quantity);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(Long cartId, Long itemId, Integer quantity) {
        if (quantity == null) {
            throw new BusinessException("Số lượng không được để trống");
        }

        Cart cart = cartRepository.findByIdForUpdate(cartId)
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
    public CartResponse removeCartItem(Long itemId, String sessionId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng"));
        assertCartOwner(item.getCart(), sessionId);
        return removeCartItem(item.getCart().getId(), itemId);
    }

    @Override
    @Transactional
    public CartResponse removeCartItem(Long cartId, Long itemId) {
        Cart cart = cartRepository.findByIdForUpdate(cartId)
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
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByIdAsc(cart.getId());
        if (cartItems.isEmpty()) {
            return CartResponse.of(cart, List.of());
        }

        List<Long> variantIds = cartItems.stream()
                .map(CartItem::getProductVariantId)
                .collect(Collectors.toList());

        java.util.Map<Long, ProductVariantSummaryDto> variantMap = catalogFacade.getVariantSummariesByIds(variantIds);

        List<CartItemResponse> itemResponses = cartItems.stream()
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

        Optional<Cart> guestCartOpt = cartRepository.findGuestBySessionIdForUpdate(sessionId);
        if (guestCartOpt.isEmpty() || guestCartOpt.get().getItems() == null || guestCartOpt.get().getItems().isEmpty()) {
            return getCart(userId, null);
        }

        Cart guestCart = guestCartOpt.get();
        if (guestCart.getUserId() != null) {
            throw new AccessDeniedException("Cannot merge another user's cart");
        }
        if (userId.equals(guestCart.getUserId())) {
            return mapToCartResponse(guestCart);
        }

        Cart userCart = cartRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            Cart newCart = Cart.builder()
                    .userId(userId)
                    .items(new ArrayList<>())
                    .build();
            return cartRepository.save(newCart);
        });

        List<CartItem> mergedGuestItems = new ArrayList<>();
        for (CartItem guestItem : new ArrayList<>(guestCart.getItems())) {
            Optional<CartItem> existingUserItem = cartItemRepository.findByCartIdAndProductVariantId(
                    userCart.getId(), guestItem.getProductVariantId());

            if (existingUserItem.isPresent()) {
                CartItem item = existingUserItem.get();
                int mergedQuantity = Math.addExact(item.getQuantity(), guestItem.getQuantity());
                if (!inventoryFacade.isStockAvailable(item.getProductVariantId(), mergedQuantity)) {
                    continue;
                }
                item.setQuantity(mergedQuantity);
                cartItemRepository.save(item);
            } else {
                if (!inventoryFacade.isStockAvailable(guestItem.getProductVariantId(), guestItem.getQuantity())) {
                    continue;
                }
                CartItem newItem = CartItem.builder()
                        .cart(userCart)
                        .productVariantId(guestItem.getProductVariantId())
                        .quantity(guestItem.getQuantity())
                        .build();
                cartItemRepository.save(newItem);
            }

            cartItemRepository.delete(guestItem);
            mergedGuestItems.add(guestItem);
        }

        guestCart.getItems().removeAll(mergedGuestItems);
        if (guestCart.getItems().isEmpty()) {
            cartRepository.delete(guestCart);
        } else {
            cartRepository.save(guestCart);
        }

        Cart updatedUserCart = cartRepository.findById(userCart.getId()).orElse(userCart);
        return mapToCartResponse(updatedUserCart);
    }

    private void assertCartOwner(Cart cart, String sessionId) {
        Long currentUserId = SecurityUtils.getCurrentUserIdOptional().orElse(null);
        boolean ownsCart = currentUserId != null
                ? currentUserId.equals(cart.getUserId())
                : cart.getUserId() == null
                    && sessionId != null
                    && !sessionId.isBlank()
                    && sessionId.equals(cart.getSessionId());

        if (!ownsCart) {
            throw new AccessDeniedException("You do not have permission to modify this cart");
        }
    }
}
