import '../core/storage/storage_service.dart';
import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/network/api_response.dart';
import '../data/models/order_model.dart';

class OrderProvider extends ChangeNotifier {
  final ApiClient _apiClient;

  List<OrderResponse> _orders = [];
  bool _isLoading = false;
  String? _errorMessage;
  int _page = 0;
  bool _hasMore = false;
  bool get hasMore => _hasMore;
  Future<void> loadMore() => fetchOrders(append: true);

  List<OrderResponse> get orders => _orders;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;

  OrderProvider({ApiClient? apiClient, bool loadOnCreate = true})
    : _apiClient = apiClient ?? ApiClient() {
    StorageService.authCleared.addListener(_clearSessionOrders);
    if (loadOnCreate) fetchOrders();
  }

  void _clearSessionOrders() {
    _orders = [];
    _page = 0;
    _hasMore = false;
    _errorMessage = null;
    notifyListeners();
  }

  @override
  void dispose() {
    StorageService.authCleared.removeListener(_clearSessionOrders);
    super.dispose();
  }

  Future<void> fetchOrders({bool append = false}) async {
    if (_isLoading) return;
    final requestedPage = append ? _page + 1 : 0;
    final ownerToken = StorageService.getAccessToken();
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      if (!StorageService.isLoggedIn) {
        final ids = StorageService.getGuestOrderIds();
        final selected = ids.skip(requestedPage * 20).take(20);
        final details = <OrderResponse>[];
        for (final id in selected) {
          final result = await _apiClient.get<OrderResponse>(
            '${ApiConstants.orderDetail}$id',
            fromJsonT: (json) =>
                OrderResponse.fromJson(json as Map<String, dynamic>),
          );
          if (result.data != null) details.add(result.data!);
        }
        if (ownerToken != StorageService.getAccessToken()) return;
        _orders = append ? [..._orders, ...details] : details;
        _page = requestedPage;
        _hasMore = ids.length > (requestedPage + 1) * 20;
        return;
      }
      final response = await _apiClient.get<PageResponse<OrderResponse>>(
        ApiConstants.myOrders,
        queryParameters: {'page': requestedPage, 'size': 20, 'sort': 'id,desc'},
        fromJsonT: (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          (item) => OrderResponse.fromJson(item as Map<String, dynamic>),
        ),
      );

      if (ownerToken != StorageService.getAccessToken()) return;
      if (response.isSuccess && response.data != null) {
        _orders = append
            ? [..._orders, ...response.data!.content]
            : response.data!.content;
        _page = requestedPage;
        _hasMore = response.data!.content.length == 20;
      }
    } catch (e) {
      _errorMessage = 'Không thể tải lịch sử đơn hàng';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<OrderResponse?> checkout(CheckoutRequest request) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final response = await _apiClient.post<OrderResponse>(
        ApiConstants.checkout,
        data: request.toJson(),
        headers: {'Idempotency-Key': request.idempotencyKey},
        fromJsonT: (json) =>
            OrderResponse.fromJson(json as Map<String, dynamic>),
      );

      if (response.isSuccess && response.data != null) {
        final createdOrder = response.data!;
        if (!StorageService.isLoggedIn)
          await StorageService.rememberGuestOrder(createdOrder.id);
        _orders.insert(0, createdOrder);
        _isLoading = false;
        notifyListeners();
        return createdOrder;
      } else {
        _errorMessage = response.message ?? 'Tạo đơn hàng không thành công';
      }
    } catch (e) {
      if (e is ApiException) {
        _errorMessage = e.message;
      } else {
        _errorMessage = 'Không thể kết nối đến máy chủ để tạo đơn hàng';
      }
    } finally {
      _isLoading = false;
      notifyListeners();
    }

    return null;
  }

  Future<OrderResponse?> getOrderDetail(int orderId) async {
    try {
      final response = await _apiClient.get<OrderResponse>(
        '${ApiConstants.orderDetail}$orderId',
        fromJsonT: (json) =>
            OrderResponse.fromJson(json as Map<String, dynamic>),
      );
      if (response.isSuccess && response.data != null) {
        final updated = response.data!;
        final idx = _orders.indexWhere((o) => o.id == orderId);
        if (idx >= 0) {
          _orders[idx] = updated;
          notifyListeners();
        }
        return updated;
      }
    } catch (_) {}
    return null;
  }

  Future<bool> cancelOrder(int orderId) async {
    try {
      final response = await _apiClient.delete(
        ApiConstants.orderCancelUrl(orderId),
      );
      if (response.isSuccess) {
        await fetchOrders();
        return true;
      } else {
        _errorMessage = response.message ?? 'Không thể hủy đơn hàng';
      }
    } catch (e) {
      if (e is ApiException) {
        _errorMessage = e.message;
      } else {
        _errorMessage = 'Lỗi kết nối khi hủy đơn hàng';
      }
    }
    return false;
  }

  List<OrderResponse> getOrdersByStatus(String statusFilter) {
    if (statusFilter == 'ALL') return _orders;
    return _orders.where((o) => o.status == statusFilter).toList();
  }
}
