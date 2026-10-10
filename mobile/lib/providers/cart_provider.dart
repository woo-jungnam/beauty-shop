import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/network/api_response.dart';
import '../core/storage/storage_service.dart';
import '../data/models/cart_model.dart';

class CartProvider extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();

  CartResponse _cart = CartResponse.empty();
  bool _isLoading = false;
  String? _errorMessage;
  String? _appliedVoucherCode;
  double _voucherDiscount = 0.0;
  double _freeShippingThreshold = 249000.0;
  double _defaultShippingFee = 25000.0;

  CartResponse get cart => _cart;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  String? get appliedVoucherCode => _appliedVoucherCode;
  double get voucherDiscount => _voucherDiscount;
  double get freeShippingThreshold => _freeShippingThreshold;

  int get totalItemsCount {
    return _cart.items.fold(0, (sum, item) => sum + item.quantity);
  }

  double get selectedSubtotal {
    return _cart.items
        .where((i) => i.isSelected)
        .fold(0.0, (sum, i) => sum + i.totalPrice);
  }

  double get shippingFee {
    if (selectedSubtotal == 0) return 0.0;
    return selectedSubtotal >= _freeShippingThreshold ? 0.0 : _defaultShippingFee;
  }

  double get finalTotal {
    final total = selectedSubtotal + shippingFee - _voucherDiscount;
    return total > 0 ? total : 0.0;
  }

  CartProvider() {
    fetchShippingPolicy();
    fetchCart();
  }

  Future<void> fetchShippingPolicy() async {
    try {
      final response = await _apiClient.get<Map<String, dynamic>>(
        ApiConstants.publicConfigs,
        fromJsonT: (json) => json as Map<String, dynamic>,
      );
      if (response.isSuccess && response.data != null) {
        final data = response.data!;
        if (data['shipping.free_threshold'] != null) {
          _freeShippingThreshold = (data['shipping.free_threshold'] as num).toDouble();
        }
        if (data['shipping.default_fee'] != null) {
          _defaultShippingFee = (data['shipping.default_fee'] as num).toDouble();
        }
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<void> fetchCart() async {
    _isLoading = true;
    notifyListeners();

    try {
      final sessionId = StorageService.getGuestSessionId();
      final response = await _apiClient.get<CartResponse>(
        ApiConstants.cart,
        queryParameters: !StorageService.isLoggedIn ? {'sessionId': sessionId} : null,
        fromJsonT: (json) => CartResponse.fromJson(json as Map<String, dynamic>),
      );
      if (response.isSuccess && response.data != null) {
        _cart = response.data!;
      }
    } catch (_) {
      if (_cart.items.isEmpty) {
        _cart = CartResponse.empty();
      }
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<bool> addToCart({
    required int productId,
    required int variantId,
    required String productName,
    required String variantName,
    required double price,
    String? thumbnailUrl,
    int quantity = 1,
  }) async {
    int resolvedVariantId = variantId;
    if (resolvedVariantId <= 0 && productId > 0) {
      try {
        final detailRes = await _apiClient.get(
          '${ApiConstants.productDetail}$productId',
        );
        if (detailRes.isSuccess && detailRes.data is Map) {
          final rawVariants = (detailRes.data as Map)['variants'] as List<dynamic>? ?? [];
          if (rawVariants.isNotEmpty) {
            final activeVariants = rawVariants
                .where((v) => v['isActive'] != false && v['isDeleted'] != true)
                .toList();
            final defaultVar = activeVariants.firstWhere(
              (v) => v['isDefault'] == true,
              orElse: () => activeVariants.isNotEmpty ? activeVariants.first : rawVariants.first,
            );
            resolvedVariantId = (defaultVar['id'] as num?)?.toInt() ?? resolvedVariantId;
          }
        }
      } catch (_) {}
    }

    if (resolvedVariantId <= 0) {
      _errorMessage = 'Sản phẩm hiện chưa có phân loại hàng khả dụng';
      notifyListeners();
      return false;
    }

    try {
      final sessionId = StorageService.getGuestSessionId();
      final response = await _apiClient.post<CartResponse>(
        ApiConstants.addToCart,
        data: {
          'variantId': resolvedVariantId,
          'quantity': quantity,
          if (!StorageService.isLoggedIn) 'sessionId': sessionId,
        },
        fromJsonT: (json) => CartResponse.fromJson(json as Map<String, dynamic>),
      );
      if (response.isSuccess && response.data != null) {
        _cart = response.data!;
        _errorMessage = null;
        notifyListeners();
        return true;
      } else {
        _errorMessage = response.message ?? 'Không thể thêm sản phẩm vào giỏ hàng';
        notifyListeners();
        return false;
      }
    } catch (e) {
      if (e is ApiException) {
        _errorMessage = e.message;
      } else {
        _errorMessage = 'Lỗi kết nối khi thêm vào giỏ hàng';
      }
      notifyListeners();
      return false;
    }
  }

  Future<void> updateQuantity(int itemId, int newQuantity) async {
    if (newQuantity <= 0) {
      await removeItem(itemId);
      return;
    }
    final index = _cart.items.indexWhere((i) => i.id == itemId);
    if (index >= 0) {
      _cart.items[index].quantity = newQuantity;
      notifyListeners();
    }
    try {
      final sessionId = StorageService.getGuestSessionId();
      await _apiClient.put(
        '${ApiConstants.updateCartItem}$itemId',
        queryParameters: {
          'quantity': newQuantity,
          if (!StorageService.isLoggedIn) 'sessionId': sessionId,
        },
      );
    } catch (_) {}
  }

  Future<void> removeItem(int itemId) async {
    _cart.items.removeWhere((i) => i.id == itemId);
    notifyListeners();
    try {
      final sessionId = StorageService.getGuestSessionId();
      await _apiClient.delete(
        '${ApiConstants.removeCartItem}$itemId',
        queryParameters: !StorageService.isLoggedIn ? {'sessionId': sessionId} : null,
      );
    } catch (_) {}
  }

  void toggleItemSelection(int itemId) {
    final index = _cart.items.indexWhere((i) => i.id == itemId);
    if (index >= 0) {
      _cart.items[index].isSelected = !_cart.items[index].isSelected;
      notifyListeners();
    }
  }

  void toggleSelectAll(bool selectAll) {
    for (final item in _cart.items) {
      item.isSelected = selectAll;
    }
    notifyListeners();
  }

  bool applyVoucher(String code) {
    final normalized = code.trim().toUpperCase();
    if (normalized == 'BEAUTY100' || normalized == 'HASAKI100') {
      _appliedVoucherCode = normalized;
      _voucherDiscount = 100000.0;
      notifyListeners();
      return true;
    } else if (normalized == 'FREESHIP') {
      _appliedVoucherCode = normalized;
      _voucherDiscount = 25000.0;
      notifyListeners();
      return true;
    }
    return false;
  }

  void clearVoucher() {
    _appliedVoucherCode = null;
    _voucherDiscount = 0.0;
    notifyListeners();
  }

  Future<void> clearCart() async {
    _cart = CartResponse.empty();
    clearVoucher();
    notifyListeners();
    try {
      final sessionId = StorageService.getGuestSessionId();
      await _apiClient.delete(
        ApiConstants.clearCart,
        queryParameters: !StorageService.isLoggedIn ? {'sessionId': sessionId} : null,
      );
    } catch (_) {}
  }
}
