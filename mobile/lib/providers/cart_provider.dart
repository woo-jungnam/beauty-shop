import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/network/api_response.dart';
import '../core/storage/storage_service.dart';
import '../data/models/cart_model.dart';

class CartProvider extends ChangeNotifier {
  final ApiClient _apiClient;

  CartResponse _cart = CartResponse.empty();
  bool _isLoading = false;
  bool _disposed = false;
  String? _errorMessage;
  String? _appliedVoucherCode;
  String? _voucherToken;
  double _voucherDiscount = 0.0;
  double? _freeShippingThreshold;
  double? _defaultShippingFee;
  bool _isLoadingShipping = false;
  String? _shippingError;
  bool _isApplyingVoucher = false;
  String? _voucherError;
  int _voucherRevision = 0;

  CartResponse get cart => _cart;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  bool get _hasCurrentVoucher =>
      _voucherToken != null && _voucherToken == StorageService.getAccessToken();
  String? get appliedVoucherCode =>
      _hasCurrentVoucher ? _appliedVoucherCode : null;
  double get voucherDiscount => _hasCurrentVoucher ? _voucherDiscount : 0.0;
  double? get freeShippingThreshold => _freeShippingThreshold;
  bool get hasShippingPolicy =>
      _freeShippingThreshold != null && _defaultShippingFee != null;
  bool get isLoadingShipping => _isLoadingShipping;
  String? get shippingError => _shippingError;
  bool get isApplyingVoucher => _isApplyingVoucher;
  String? get voucherError => _voucherError;
  bool get allItemsSelected =>
      _cart.items.isNotEmpty && _cart.items.every((item) => item.isSelected);

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
    if (!hasShippingPolicy) return 0.0;
    return selectedSubtotal >= _freeShippingThreshold!
        ? 0.0
        : _defaultShippingFee!;
  }

  double get membershipDiscount {
    final sub = selectedSubtotal;
    if (sub <= 0) return 0.0;
    if (allItemsSelected) return _cart.estimatedMembershipDiscount;
    final pct = _cart.membershipDiscountPercentage ?? 0;
    if (pct <= 0) return 0.0;
    return (sub * pct / 100).roundToDouble();
  }

  double get finalTotal {
    final total = selectedSubtotal + shippingFee - voucherDiscount - membershipDiscount;
    return total > 0 ? total : 0.0;
  }

  CartProvider({ApiClient? apiClient, bool loadOnCreate = true})
    : _apiClient = apiClient ?? ApiClient() {
    StorageService.authCleared.addListener(_onAuthCleared);
    StorageService.authSessionChanged.addListener(_onAuthSessionChanged);
    if (loadOnCreate) {
      fetchShippingPolicy();
      fetchCart();
    }
  }

  void _onAuthCleared() {
    _cart = CartResponse.empty();
    _invalidateVoucher();
    _errorMessage = null;
    if (!_disposed) notifyListeners();
  }

  void _onAuthSessionChanged() {
    if (!_disposed) fetchCart();
  }

  @override
  void dispose() {
    _disposed = true;
    StorageService.authCleared.removeListener(_onAuthCleared);
    StorageService.authSessionChanged.removeListener(_onAuthSessionChanged);
    super.dispose();
  }

  Future<void> fetchShippingPolicy() async {
    _isLoadingShipping = true;
    _shippingError = null;
    notifyListeners();
    try {
      final response = await _apiClient.get<Map<String, dynamic>>(
        ApiConstants.publicConfigs,
        fromJsonT: (json) {
          if (json is Map) {
            return Map<String, dynamic>.from(json);
          }
          return <String, dynamic>{};
        },
      );
      if (!response.isSuccess || response.data == null) {
        throw StateError('Missing shipping policy');
      }
      final data = response.data!;
      final threshold = double.tryParse('${data['shipping.free_threshold']}');
      final fee = double.tryParse('${data['shipping.default_fee']}');
      if (threshold == null ||
          fee == null ||
          !threshold.isFinite ||
          !fee.isFinite ||
          threshold < 0 ||
          fee < 0) {
        throw StateError('Invalid shipping policy');
      }
      _freeShippingThreshold = threshold;
      _defaultShippingFee = fee;
      _shippingError = null;
    } catch (_) {
      if (_freeShippingThreshold == null || _defaultShippingFee == null) {
        _shippingError = 'Không thể tải phí vận chuyển. Vui lòng thử lại.';
      }
    } finally {
      _isLoadingShipping = false;
      notifyListeners();
    }
  }

  Future<void> fetchCart() async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final sessionId = StorageService.getGuestSessionId();
      final response = await _apiClient.get<CartResponse>(
        ApiConstants.cart,
        queryParameters: !StorageService.isLoggedIn
            ? {'sessionId': sessionId}
            : null,
        fromJsonT: (json) =>
            CartResponse.fromJson(json as Map<String, dynamic>),
      );
      if (response.isSuccess) {
        _cart = response.data ?? CartResponse.empty();
        if (_freeShippingThreshold == null && _cart.freeShippingThreshold != null) {
          _freeShippingThreshold = _cart.freeShippingThreshold;
        }
        if (_defaultShippingFee == null && _cart.defaultShippingFee != null) {
          _defaultShippingFee = _cart.defaultShippingFee;
        }
        _invalidateVoucher();
      } else {
        _errorMessage = response.message ?? 'Không thể tải giỏ hàng';
      }
    } catch (_) {
      _errorMessage = 'Không thể tải giỏ hàng. Vui lòng thử lại.';
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
          final rawVariants =
              (detailRes.data as Map)['variants'] as List<dynamic>? ?? [];
          if (rawVariants.isNotEmpty) {
            final activeVariants = rawVariants
                .where((v) => v['isActive'] != false && v['isDeleted'] != true)
                .toList();
            final inStockVariants = activeVariants
                .where((v) => ((v['stockQuantity'] as num?)?.toInt() ?? 0) > 0)
                .toList();
            final pool = inStockVariants.isNotEmpty
                ? inStockVariants
                : activeVariants;
            if (pool.isNotEmpty) {
              final defaultVar = pool.firstWhere(
                (v) => v['isDefault'] == true,
                orElse: () => pool.first,
              );
              resolvedVariantId =
                  (defaultVar['id'] as num?)?.toInt() ?? resolvedVariantId;
            }
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
        fromJsonT: (json) =>
            CartResponse.fromJson(json as Map<String, dynamic>),
      );
      if (response.isSuccess && response.data != null) {
        _cart = response.data!;
        _invalidateVoucher();
        _errorMessage = null;
        notifyListeners();
        return true;
      } else {
        _errorMessage =
            response.message ?? 'Không thể thêm sản phẩm vào giỏ hàng';
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

  CartResponse _snapshotCart() {
    return CartResponse(
      items: _cart.items
          .map(
            (i) => CartItem(
              id: i.id,
              productId: i.productId,
              variantId: i.variantId,
              productName: i.productName,
              variantName: i.variantName,
              thumbnailUrl: i.thumbnailUrl,
              price: i.price,
              originalPrice: i.originalPrice,
              quantity: i.quantity,
              isSelected: i.isSelected,
            ),
          )
          .toList(),
      subtotal: _cart.subtotal,
      shippingFee: _cart.shippingFee,
      discountAmount: _cart.discountAmount,
      totalAmount: _cart.totalAmount,
      membershipTier: _cart.membershipTier,
      membershipDiscountPercentage: _cart.membershipDiscountPercentage,
      estimatedMembershipDiscount: _cart.estimatedMembershipDiscount,
      freeShippingThreshold: _cart.freeShippingThreshold,
      defaultShippingFee: _cart.defaultShippingFee,
    );
  }

  void _applyCartPreservingSelection(
    CartResponse updated,
    Map<int, bool> selectedById,
  ) {
    for (final item in updated.items) {
      if (selectedById.containsKey(item.id)) {
        item.isSelected = selectedById[item.id]!;
      }
    }
    _cart = updated;
    if (_freeShippingThreshold == null && _cart.freeShippingThreshold != null) {
      _freeShippingThreshold = _cart.freeShippingThreshold;
    }
    if (_defaultShippingFee == null && _cart.defaultShippingFee != null) {
      _defaultShippingFee = _cart.defaultShippingFee;
    }
  }

  Future<bool> updateQuantity(int itemId, int newQuantity) async {
    if (newQuantity <= 0) {
      return removeItem(itemId);
    }
    final index = _cart.items.indexWhere((i) => i.id == itemId);
    if (index < 0) return false;
    final previousCart = _snapshotCart();
    final selectedById = {for (final i in _cart.items) i.id: i.isSelected};
    _cart.items[index].quantity = newQuantity;
    _errorMessage = null;
    _invalidateVoucher();
    notifyListeners();
    try {
      final sessionId = StorageService.getGuestSessionId();
      final response = await _apiClient.put<CartResponse?>(
        '${ApiConstants.updateCartItem}$itemId',
        queryParameters: {
          'quantity': newQuantity,
          if (!StorageService.isLoggedIn) 'sessionId': sessionId,
        },
        fromJsonT: (json) => json is Map
            ? CartResponse.fromJson(Map<String, dynamic>.from(json))
            : null,
      );
      if (response.isSuccess) {
        if (response.data != null) {
          _applyCartPreservingSelection(response.data!, selectedById);
        }
        _errorMessage = null;
        if (!_disposed) notifyListeners();
        return true;
      }
      _cart = previousCart;
      _errorMessage = response.message ?? 'Không thể cập nhật số lượng sản phẩm';
    } catch (e) {
      _cart = previousCart;
      _errorMessage = e is ApiException
          ? e.message
          : 'Không thể cập nhật số lượng. Vui lòng thử lại.';
    }
    if (!_disposed) notifyListeners();
    return false;
  }

  Future<bool> removeItem(int itemId) async {
    final previousCart = _snapshotCart();
    final selectedById = {for (final i in _cart.items) i.id: i.isSelected};
    _cart.items.removeWhere((i) => i.id == itemId);
    _errorMessage = null;
    _invalidateVoucher();
    notifyListeners();
    try {
      final sessionId = StorageService.getGuestSessionId();
      final response = await _apiClient.delete<CartResponse?>(
        '${ApiConstants.removeCartItem}$itemId',
        queryParameters: !StorageService.isLoggedIn
            ? {'sessionId': sessionId}
            : null,
        fromJsonT: (json) => json is Map
            ? CartResponse.fromJson(Map<String, dynamic>.from(json))
            : null,
      );
      if (response.isSuccess) {
        if (response.data != null) {
          _applyCartPreservingSelection(response.data!, selectedById);
        }
        _errorMessage = null;
        if (!_disposed) notifyListeners();
        return true;
      }
      _cart = previousCart;
      _errorMessage = response.message ?? 'Không thể xóa sản phẩm khỏi giỏ hàng';
    } catch (e) {
      _cart = previousCart;
      _errorMessage = e is ApiException
          ? e.message
          : 'Không thể xóa sản phẩm. Vui lòng thử lại.';
    }
    if (!_disposed) notifyListeners();
    return false;
  }

  void toggleItemSelection(int itemId) {
    final index = _cart.items.indexWhere((i) => i.id == itemId);
    if (index >= 0) {
      _cart.items[index].isSelected = !_cart.items[index].isSelected;
      _invalidateVoucher();
      notifyListeners();
    }
  }

  void toggleSelectAll(bool selectAll) {
    _invalidateVoucher();
    for (final item in _cart.items) {
      item.isSelected = selectAll;
    }
    notifyListeners();
  }

  Future<bool> applyVoucher(String code) async {
    if (_isApplyingVoucher) return false;
    _invalidateVoucher();
    final normalized = code.trim().toUpperCase();
    if (normalized.isEmpty ||
        !StorageService.isLoggedIn ||
        selectedSubtotal <= 0) {
      _voucherError = normalized.isEmpty
          ? 'Vui lòng nhập mã giảm giá'
          : !StorageService.isLoggedIn
          ? 'Vui lòng đăng nhập để sử dụng mã giảm giá'
          : 'Vui lòng chọn sản phẩm trước khi áp dụng mã';
      notifyListeners();
      return false;
    }
    final revision = _voucherRevision;
    final subtotal = selectedSubtotal;
    final token = StorageService.getAccessToken();
    _isApplyingVoucher = true;
    notifyListeners();
    try {
      final response = await _apiClient.post<Map<String, dynamic>>(
        ApiConstants.validateVoucher,
        data: {'code': normalized, 'orderAmount': subtotal},
        fromJsonT: (json) => json as Map<String, dynamic>,
      );
      if (revision != _voucherRevision ||
          token != StorageService.getAccessToken())
        return false;
      if (!response.isSuccess || response.data == null) {
        _voucherError = response.message ?? 'Không thể xác thực mã giảm giá';
        return false;
      }
      final amount = double.tryParse('${response.data!['discountAmount']}');
      final validatedCode = response.data!['code']?.toString();
      if (amount == null ||
          !amount.isFinite ||
          amount < 0 ||
          amount > subtotal ||
          validatedCode == null ||
          validatedCode.trim().isEmpty) {
        _voucherError = 'Không thể xác thực mức giảm giá. Vui lòng thử lại.';
        return false;
      }
      _appliedVoucherCode = validatedCode;
      _voucherToken = token;
      _voucherDiscount = amount;
      return true;
    } catch (error) {
      if (revision == _voucherRevision) {
        _voucherError = error is ApiException
            ? error.message
            : 'Không thể xác thực mã giảm giá. Vui lòng thử lại.';
      }
      return false;
    } finally {
      if (revision == _voucherRevision) _isApplyingVoucher = false;
      notifyListeners();
    }
  }

  Future<bool> revalidateVoucher() async {
    final code = _appliedVoucherCode;
    return code == null || await applyVoucher(code);
  }

  void _invalidateVoucher() {
    _voucherRevision++;
    _isApplyingVoucher = false;
    _appliedVoucherCode = null;
    _voucherToken = null;
    _voucherDiscount = 0.0;
    _voucherError = null;
  }

  void clearVoucher() {
    _invalidateVoucher();
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
        queryParameters: !StorageService.isLoggedIn
            ? {'sessionId': sessionId}
            : null,
      );
    } catch (_) {}
  }
}
