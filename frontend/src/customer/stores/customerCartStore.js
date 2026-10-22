import { useState, useEffect } from 'react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { isClearanceProduct } from '../utils/productSelection';

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
  cartError: '',
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
  price: Number(ci.price ?? 0),
  originalPrice: Number(ci.price ?? 0),
  image: (ci.imageUrl && !ci.imageUrl.includes('img.com')) ? ci.imageUrl : '',
  quantity: ci.quantity,
  available: ci.available !== false,
});

const isPositiveId = (value) => (typeof value === 'number' || (typeof value === 'string' && /^\d+$/.test(value)))
  && Number.isSafeInteger(Number(value)) && Number(value) > 0;
const isMoney = (value) => value !== null && value !== undefined && value !== ''
  && (typeof value === 'number' || typeof value === 'string')
  && Number.isFinite(Number(value)) && Number(value) >= 0;
const isActiveVariant = (variant) => isPositiveId(variant?.id) && variant.isActive === true
  && !variant.isDeleted && typeof variant.sku === 'string' && variant.sku.trim().length > 0;

const isValidCart = (cart) => isPositiveId(cart?.id) && Array.isArray(cart.items)
  && isMoney(cart.totalPrice)
  && cart.items.every((item) => isPositiveId(item?.id) && isPositiveId(item.variantId)
    && Number.isSafeInteger(item.quantity) && item.quantity > 0
    && typeof item.available === 'boolean'
    && (item.available === false || (isMoney(item.price) && typeof item.sku === 'string' && item.sku.trim().length > 0)))
  && new Set(cart.items.map((item) => Number(item.id))).size === cart.items.length
  && new Set(cart.items.map((item) => Number(item.variantId))).size === cart.items.length;

const isEmptyCartEnvelope = (response) => response?.status === 200 && response.message === 'Success'
  && response.path === '/api/v1/cart' && typeof response.timestamp === 'string' && response.timestamp.length > 0
  && response.success !== false && response.errorCode == null && response.errors == null
  && !Object.hasOwn(response, 'data') && !Object.hasOwn(response, 'items') && !Object.hasOwn(response, 'id');

const cartErrorMessage = (error, fallback) => {
  const message = error?.response?.data?.message || error?.message || '';
  if (/[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i.test(message)
      && !/Exception|SQL|stack trace|java\./i.test(message)) return message;
  return fallback;
};

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
    if (memoryState.isLoading) return false;
    try {
      memoryState.isLoading = true;
      notifyListeners();
      const sessionId = getGuestSessionId();
      const res = await apiClient.get(ENDPOINTS.CART.GET(sessionId));
      const cartData = res && Object.hasOwn(res, 'data') ? res.data : res;
      if (res?.success !== false && (cartData === null || isEmptyCartEnvelope(res))) {
        memoryState.items = [];
        memoryState.cartId = null;
        memoryState.cartError = '';
        saveCart();
        return true;
      }
      if (res?.success !== false && isValidCart(cartData)) {
        memoryState.items = cartData.items.map(mapBackendItem);
        memoryState.cartId = cartData.id || null;
        memoryState.cartError = '';
        saveCart();
        return true;
      }
      memoryState.cartError = 'Chưa xác minh được dữ liệu giỏ hàng. Vui lòng kiểm tra lại.';
      return false;
    } catch (err) {
      memoryState.cartError = cartErrorMessage(err, 'Không thể kiểm tra giỏ hàng. Vui lòng thử lại sau.');
      return false;
    } finally {
      memoryState.isLoading = false;
      notifyListeners();
    }
  },

  addItem: async (product, variant, qty = 1) => {
    const fail = (message) => {
      memoryState.cartError = message;
      notifyListeners();
      return false;
    };
    if (memoryState.isLoading) return fail('Giỏ hàng đang được cập nhật. Vui lòng đợi một chút.');
    memoryState.cartError = '';
    if (!Number.isSafeInteger(qty) || qty <= 0) return fail('Số lượng phải là số nguyên dương.');
    const clearance = isClearanceProduct(product);
    const requestedVariantId = variant?.id ?? (clearance ? product.clearanceVariant?.id : undefined);
    const explicitSelection = variant != null || clearance;
    if (explicitSelection && !isPositiveId(requestedVariantId)) return fail('Không xác định được phân loại đã chọn. Vui lòng tải lại sản phẩm.');
    if (variant && (variant.isActive === false || variant.isDeleted)) return fail('Phân loại này hiện không được bán.');
    memoryState.isLoading = true;
    notifyListeners();

    try {
      const pickVariant = (variants) => {
        const active = (Array.isArray(variants) ? variants : []).filter(isActiveVariant);
        return explicitSelection ? active.find((item) => Number(item.id) === Number(requestedVariantId))
          : active.find((item) => item.isDefault) || active[0];
      };
      let selectedVariant = isActiveVariant(variant) ? variant : pickVariant(product?.variants);
      if ((clearance || !selectedVariant) && isPositiveId(product?.id)) {
        let prodRes;
        try {
          prodRes = await apiClient.get(ENDPOINTS.CATALOG.PUBLIC_PRODUCT_DETAIL(product.id));
        } catch (error) {
          return fail(cartErrorMessage(error, 'Không thể tải phân loại sản phẩm. Vui lòng thử lại.'));
        }
        const fullProduct = prodRes?.data ?? prodRes;
        selectedVariant = Number(fullProduct?.id) === Number(product.id) && (!fullProduct.status || fullProduct.status === 'ACTIVE')
          ? pickVariant(fullProduct.variants) : null;
      }
      if (!selectedVariant) return fail(explicitSelection
        ? 'Phân loại đã chọn không còn khả dụng. Vui lòng tải lại sản phẩm.'
        : 'Sản phẩm chưa có phân loại đang bán với mã SKU hợp lệ.');
      if (selectedVariant.stockQuantity != null
          && (!Number.isSafeInteger(selectedVariant.stockQuantity) || selectedVariant.stockQuantity < qty)) {
        return fail('Số lượng yêu cầu vượt quá số hàng hiện có của phân loại này.');
      }
      const variantId = Number(selectedVariant.id);
      const previousQuantity = memoryState.items.find((item) => Number(item.variantId) === variantId)?.quantity ?? 0;
      const res = await apiClient.post(ENDPOINTS.CART.ADD, {
        variantId,
        quantity: qty,
        sessionId: getGuestSessionId(),
      });
      const cartData = res?.data ?? res;
      const confirmedItem = cartData?.items?.find?.((item) => Number(item.variantId) === variantId);
      const minimumQuantity = Number(cartData?.id) === Number(memoryState.cartId) ? previousQuantity + qty : qty;
      if (res?.success === false || !isValidCart(cartData) || confirmedItem?.available !== true
          || confirmedItem.quantity < minimumQuantity) {
        return fail('Máy chủ chưa xác nhận sản phẩm trong giỏ. Vui lòng kiểm tra giỏ trước khi thử lại.');
      }
      memoryState.items = cartData.items.map(mapBackendItem);
      memoryState.cartId = cartData.id || null;
      memoryState.cartError = '';
      memoryState.isCartOpen = true;
      saveCart();
      return true;
    } catch (err) {
      return fail(cartErrorMessage(err, 'Không thể thêm sản phẩm vào giỏ. Vui lòng kiểm tra giỏ trước khi thử lại.'));
    } finally {
      memoryState.isLoading = false;
      notifyListeners();
    }
  },

  clearCartError: () => {
    memoryState.cartError = '';
    notifyListeners();
  },

  updateQuantity: async (itemId, qty) => {
    if (qty <= 0) {
      return cartActions.removeItem(itemId);
    }
    const sessionId = getGuestSessionId();
    const item = memoryState.items.find((i) => i.itemId === String(itemId));
    const backendId = item?.backendId || (isNaN(Number(itemId)) ? null : itemId);

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

  applyVoucher: async (code, currentSubtotal = null) => {
    const upper = (code || '').trim().toUpperCase();
    if (!upper) {
      throw new Error('Vui lòng nhập mã giảm giá');
    }
    const orderTotal = currentSubtotal != null
      ? Number(currentSubtotal)
      : memoryState.items.reduce((sum, item) => sum + (item.price * item.quantity), 0);

    if (orderTotal <= 0) {
      throw new Error('Giỏ hàng trống, không thể áp dụng mã');
    }

    try {
      const res = await apiClient.post(ENDPOINTS.VOUCHERS.VALIDATE, {
        code: upper,
        orderAmount: orderTotal,
      });
      const data = res?.data || res;
      if (!data || !data.code) {
        throw new Error('Mã giảm giá không hợp lệ');
      }
      memoryState.voucher = {
        code: data.code,
        name: data.name,
        voucherId: data.voucherId,
        discountType: data.discountType,
        discountValue: Number(data.discountValue ?? 0),
        discountAmount: Number(data.discountAmount ?? 0),
        minOrderAmount: Number(data.minOrderAmount ?? 0),
        maxDiscount: data.maxDiscountAmount ? Number(data.maxDiscountAmount) : null,
        message: data.message,
      };
      saveCart();
      return { success: true, voucher: memoryState.voucher, message: data.message };
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Mã giảm giá không hợp lệ hoặc đã hết hạn';
      throw new Error(msg);
    }
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
    } catch {}
  },
};

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
    const v = memoryState.voucher;
    if (v.minOrderAmount && subtotal < v.minOrderAmount) {
      discountAmount = 0;
    } else if (v.discountType === 'FIXED_AMOUNT') {
      discountAmount = Math.min(v.discountValue || v.discountAmount || 0, subtotal);
    } else if (v.discountType === 'PERCENTAGE') {
      const percent = v.discountValue || 0;
      const calc = Math.round(subtotal * (percent / 100));
      discountAmount = Math.min(calc, v.maxDiscount || calc, subtotal);
    } else if (v.discountPercent) {
      const calc = Math.round(subtotal * (v.discountPercent / 100));
      discountAmount = Math.min(calc, v.maxDiscount || calc, subtotal);
    } else if (v.discountAmount) {
      discountAmount = Math.min(v.discountAmount, subtotal);
    }
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
    cartError: memoryState.cartError,
    clearCartError: cartActions.clearCartError,
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
