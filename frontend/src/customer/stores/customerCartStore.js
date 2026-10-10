import { useState, useEffect } from 'react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';

const STORAGE_KEY = 'beautyshop_customer_cart';
const DEFAULT_FALLBACK_THRESHOLD = 249000;
const DEFAULT_FALLBACK_SHIPPING_FEE = 25000;

export const getGuestSessionId = () => {
  try {
    let id = localStorage.getItem('beautyshop_guest_session_id');
    if (!id) {
      id = 'guest_' + Math.random().toString(36).substring(2, 12) + '_' + Date.now();
      localStorage.setItem('beautyshop_guest_session_id', id);
    }
    return id;
  } catch {
    return 'guest_fallback_' + Date.now();
  }
};

let memoryState = {
  items: [],
  voucher: null,
  isCartOpen: false,
  isLoading: false,
  cartId: null,
  freeShippingThreshold: DEFAULT_FALLBACK_THRESHOLD,
  defaultShippingFee: DEFAULT_FALLBACK_SHIPPING_FEE,
};

let listeners = new Set();

const notifyListeners = () => {
  listeners.forEach((listener) => listener());
};

const mapBackendItem = (ci) => ({
  itemId: String(ci.id),
  backendId: ci.id,
  variantId: ci.variantId,
  name: ci.variantName || ci.sku || 'Sản phẩm',
  variantName: ci.variantName || ci.sku || '',
  sku: ci.sku,
  price: Number(ci.price || 0),
  originalPrice: Number(ci.price || 0),
  image: (ci.imageUrl && !ci.imageUrl.includes('img.com')) ? ci.imageUrl : '',
  quantity: ci.quantity || 1,
  available: ci.available !== false,
});

const loadStoredCart = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      memoryState.items = Array.isArray(parsed.items) ? parsed.items : [];
      memoryState.voucher = parsed.voucher || null;
      memoryState.cartId = parsed.cartId || null;
    }
  } catch (err) {
    console.error('Failed to load cart from localStorage', err);
  }
};

loadStoredCart();

const saveCart = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      items: memoryState.items,
      voucher: memoryState.voucher,
      cartId: memoryState.cartId,
    }));
  } catch (err) {
    console.error('Failed to save cart to localStorage', err);
  }
  notifyListeners();
};

export const cartActions = {
  fetchCart: async () => {
    try {
      memoryState.isLoading = true;
      const sessionId = getGuestSessionId();
      const res = await apiClient.get(ENDPOINTS.CART.GET(sessionId));
      const cartData = res?.data || res;
      if (cartData && Array.isArray(cartData.items)) {
        memoryState.items = cartData.items.map(mapBackendItem);
        memoryState.cartId = cartData.id || null;
        saveCart();
      }
    } catch (err) {
      console.warn('Failed to fetch cart from backend, using cached state', err);
    } finally {
      memoryState.isLoading = false;
      notifyListeners();
    }
  },

  addItem: async (product, variant, qty = 1) => {
    let variantId = variant?.id;
    if (!variantId && product?.variants && product.variants.length > 0) {
      variantId = product.variants[0].id;
    }

    if (!variantId && product?.id) {
      try {
        const prodRes = await apiClient.get(ENDPOINTS.CATALOG.PUBLIC_PRODUCT_DETAIL(product.id));
        const fullProd = prodRes?.data || prodRes;
        if (fullProd?.variants && fullProd.variants.length > 0) {
          const activeVariants = fullProd.variants.filter((v) => v.isActive !== false && !v.isDeleted);
          const defaultVar = activeVariants.find((v) => v.isDefault) || activeVariants[0] || fullProd.variants[0];
          variantId = defaultVar?.id;
        }
      } catch (e) {
        console.warn('Cannot resolve variant for product', product.id, e);
      }
    }

    if (!variantId) {
      console.error('No variant ID found for product', product);
      return false;
    }

    const sessionId = getGuestSessionId();
    memoryState.isLoading = true;
    notifyListeners();

    try {
      const res = await apiClient.post(ENDPOINTS.CART.ADD, {
        variantId,
        quantity: qty,
        sessionId,
      });
      const cartData = res?.data || res;
      if (cartData && Array.isArray(cartData.items)) {
        memoryState.items = cartData.items.map(mapBackendItem);
        memoryState.cartId = cartData.id || null;
      }
      memoryState.isCartOpen = true;
      saveCart();
      return true;
    } catch (err) {
      console.error('Failed to add item to backend cart', err);
      // Fallback local update if network temporary issue
      const itemId = `v-${variantId}`;
      const price = Number(variant?.price || product.price || 0);
      const existing = memoryState.items.find(i => i.variantId === variantId);
      if (existing) {
        existing.quantity += qty;
      } else {
        memoryState.items.push({
          itemId,
          variantId,
          name: product.name,
          variantName: variant?.variantName || variant?.sku || '',
          sku: variant?.sku || '',
          price,
          originalPrice: price,
          image: product.featuredImage || product.thumbnailUrl || '',
          quantity: qty,
          available: true,
        });
      }
      memoryState.isCartOpen = true;
      saveCart();
      return false;
    } finally {
      memoryState.isLoading = false;
      notifyListeners();
    }
  },

  updateQuantity: async (itemId, qty) => {
    if (qty <= 0) {
      return cartActions.removeItem(itemId);
    }
    const sessionId = getGuestSessionId();
    const item = memoryState.items.find((i) => i.itemId === String(itemId));
    const backendId = item?.backendId || (isNaN(Number(itemId)) ? null : itemId);

    // Optimistic update
    if (item) {
      item.quantity = qty;
      notifyListeners();
    }

    if (backendId && !isNaN(Number(backendId))) {
      try {
        const res = await apiClient.put(
          ENDPOINTS.CART.UPDATE_ITEM(backendId, sessionId),
          null,
          { params: { quantity: qty, sessionId } }
        );
        const cartData = res?.data || res;
        if (cartData && Array.isArray(cartData.items)) {
          memoryState.items = cartData.items.map(mapBackendItem);
          memoryState.cartId = cartData.id || null;
        }
      } catch (err) {
        console.warn('Failed to update cart item in backend', err);
      }
    }
    saveCart();
  },

  removeItem: async (itemId) => {
    const sessionId = getGuestSessionId();
    const item = memoryState.items.find((i) => i.itemId === String(itemId));
    const backendId = item?.backendId || (isNaN(Number(itemId)) ? null : itemId);

    memoryState.items = memoryState.items.filter((i) => i.itemId !== String(itemId));
    saveCart();

    if (backendId && !isNaN(Number(backendId))) {
      try {
        const res = await apiClient.delete(ENDPOINTS.CART.REMOVE_ITEM(backendId, sessionId));
        const cartData = res?.data || res;
        if (cartData && Array.isArray(cartData.items)) {
          memoryState.items = cartData.items.map(mapBackendItem);
          memoryState.cartId = cartData.id || null;
          saveCart();
        }
      } catch (err) {
        console.warn('Failed to delete cart item from backend', err);
      }
    }
  },

  clearCart: async () => {
    const sessionId = getGuestSessionId();
    memoryState.items = [];
    memoryState.voucher = null;
    saveCart();

    try {
      await apiClient.delete(ENDPOINTS.CART.CLEAR(sessionId));
    } catch (err) {
      console.error('Failed to clear cart on backend', err);
    }
  },

  applyVoucher: (code, discountPercent = 10, maxDiscount = 100000) => {
    const upper = code.trim().toUpperCase();
    if (!upper) return false;
    memoryState.voucher = {
      code: upper,
      discountPercent,
      maxDiscount,
    };
    saveCart();
    return true;
  },

  removeVoucher: () => {
    memoryState.voucher = null;
    saveCart();
  },

  setCartOpen: (open) => {
    memoryState.isCartOpen = Boolean(open);
    notifyListeners();
  },

  fetchShippingPolicy: async () => {
    try {
      const res = await apiClient.get('/api/v1/system-configs/public');
      const data = res?.data || res || {};
      if (data['shipping.free_threshold']) {
        memoryState.freeShippingThreshold = Number(data['shipping.free_threshold']);
      }
      if (data['shipping.default_fee']) {
        memoryState.defaultShippingFee = Number(data['shipping.default_fee']);
      }
      notifyListeners();
    } catch (_) {}
  },
};

// Initial sync with backend
cartActions.fetchCart();
cartActions.fetchShippingPolicy();

export const useCustomerCart = () => {
  const [, setTick] = useState(0);

  useEffect(() => {
    const onChange = () => setTick((t) => t + 1);
    listeners.add(onChange);
    return () => listeners.delete(onChange);
  }, []);

  const items = memoryState.items;
  const totalItems = items.reduce((sum, item) => sum + item.quantity, 0);
  const subtotal = items.reduce((sum, item) => sum + (item.price * item.quantity), 0);

  let discountAmount = 0;
  if (memoryState.voucher && subtotal > 0) {
    const calc = subtotal * (memoryState.voucher.discountPercent / 100);
    discountAmount = Math.min(calc, memoryState.voucher.maxDiscount || calc);
  }

  const isFreeShipping = subtotal >= memoryState.freeShippingThreshold;
  const shippingFee = (subtotal === 0 || isFreeShipping) ? 0 : memoryState.defaultShippingFee;
  const remainingForFreeShipping = Math.max(0, memoryState.freeShippingThreshold - subtotal);
  const totalAmount = Math.max(0, subtotal - discountAmount + shippingFee);

  return {
    items,
    totalItems,
    subtotal,
    voucher: memoryState.voucher,
    discountAmount,
    shippingFee,
    isFreeShipping,
    freeShippingThreshold: memoryState.freeShippingThreshold,
    remainingForFreeShipping,
    totalAmount,
    isCartOpen: memoryState.isCartOpen,
    isLoading: memoryState.isLoading,
    addItem: cartActions.addItem,
    updateQuantity: cartActions.updateQuantity,
    removeItem: cartActions.removeItem,
    clearCart: cartActions.clearCart,
    applyVoucher: cartActions.applyVoucher,
    removeVoucher: cartActions.removeVoucher,
    setCartOpen: cartActions.setCartOpen,
    fetchCart: cartActions.fetchCart,
  };
};
