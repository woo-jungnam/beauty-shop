package com.core.beautyshop.service.cart;

import com.core.beautyshop.dto.request.AddToCartRequest;
import com.core.beautyshop.dto.response.CartResponse;
import com.core.beautyshop.entities.cart.Cart;
import com.core.beautyshop.entities.cart.CartItem;
import com.core.beautyshop.entities.product.ProductVariant;
import com.core.beautyshop.entities.user.User;
import com.core.beautyshop.exception.InsufficientStockException;
import com.core.beautyshop.exception.ResourceNotFoundException;
import com.core.beautyshop.repository.CartItemRepository;
import com.core.beautyshop.repository.CartRepository;
import com.core.beautyshop.repository.ProductVariantRepository;
import com.core.beautyshop.repository.UserRepository;
import com.core.beautyshop.service.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId, String sessionId) {
        Cart cart = getCartEntity(userId, sessionId);
        return CartResponse.fromEntity(cart);
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
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm"));

        if (!inventoryService.isStockAvailable(variant.getId(), request.getQuantity())) {
            throw new InsufficientStockException("Không đủ hàng trong kho cho sản phẩm này");
        }

        Cart cart = getCartEntity(userId, request.getSessionId());

        if (cart == null) {
            cart = Cart.builder()
                    .sessionId(request.getSessionId())
                    .items(new ArrayList<>())
                    .build();
            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                cart.setUser(user);
            }
            cart = cartRepository.save(cart);
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndProductVariantId(cart.getId(), variant.getId());

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            int newQty = existingItem.getQuantity() + request.getQuantity();
            if (!inventoryService.isStockAvailable(variant.getId(), newQty)) {
                throw new InsufficientStockException("Không đủ hàng trong kho cho tổng số lượng yêu cầu trong giỏ hàng");
            }
            existingItem.setQuantity(newQty);
            cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productVariant(variant)
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(newItem);
        }

        // Fetch fresh cart to ensure relations are loaded before mapping
        Cart updatedCart = cartRepository.findById(cart.getId()).orElse(cart);
        return CartResponse.fromEntity(updatedCart);
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
            if (!inventoryService.isStockAvailable(item.getProductVariant().getId(), quantity)) {
                throw new InsufficientStockException("Không đủ hàng trong kho");
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        Cart updatedCart = cartRepository.findById(cart.getId()).orElse(cart);
        return CartResponse.fromEntity(updatedCart);
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
        return CartResponse.fromEntity(updatedCart);
    }
}
