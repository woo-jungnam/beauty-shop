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
  String? _actionError;
  int _page = 0;
  int _historyRequest = 0;
  bool _loadingHistory = false;
  bool _hasMore = false;
  bool get hasMore => _hasMore;
  Future<void> loadMore() => fetchOrders(append: true);
  Future<void> refreshOrders() => fetchOrders(status: _currentStatus);

  List<OrderResponse> get orders => _orders;
  bool get isLoading => _isLoading || _loadingHistory;
  String? get errorMessage => _errorMessage;
  String? get actionError => _actionError;

  OrderProvider({ApiClient? apiClient, bool loadOnCreate = true})
    : _apiClient = apiClient ?? ApiClient() {
    StorageService.authCleared.addListener(_clearSessionOrders);
    StorageService.authSessionChanged.addListener(_clearSessionOrders);
    if (loadOnCreate) fetchOrders();
  }

  void _clearSessionOrders() {
    _historyRequest++;
    _loadingHistory = false;
    _currentStatus = null;
    _orders = [];
    _page = 0;
    _hasMore = false;
    _errorMessage = null;
    _actionError = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _historyRequest++;
    StorageService.authCleared.removeListener(_clearSessionOrders);
    StorageService.authSessionChanged.removeListener(_clearSessionOrders);
    super.dispose();
  }

  String? _currentStatus;
  String? get currentStatus => _currentStatus;

  Future<void> fetchOrders({String? status, bool append = false}) async {
    if (append && (_loadingHistory || !_hasMore)) return;
    if (!append && _currentStatus != status) {
      _orders = [];
      _page = 0;
      _hasMore = false;
      _currentStatus = status;
    }
    final request = ++_historyRequest;
    final filterStatus = append ? _currentStatus : status;
    final requestedPage = append ? _page + 1 : 0;
    final ownerToken = StorageService.getAccessToken();
    bool isCurrent() =>
        request == _historyRequest &&
        ownerToken == StorageService.getAccessToken();
    _loadingHistory = true;
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
          if (!isCurrent()) return;
          if (result.isSuccess && result.data != null) {
            if (filterStatus == null || result.data!.status == filterStatus) {
              details.add(result.data!);
            }
          }
        }
        if (!isCurrent()) return;
        _orders = append ? [..._orders, ...details] : details;
        _page = requestedPage;
        _hasMore = ids.length > (requestedPage + 1) * 20;
        return;
      }
      final queryParams = <String, dynamic>{
        'page': requestedPage,
        'size': 20,
        'sort': 'id,desc',
      };
      if (filterStatus != null && filterStatus.isNotEmpty) {
        queryParams['status'] = filterStatus;
      }
      final response = await _apiClient.get<PageResponse<OrderResponse>>(
        ApiConstants.myOrders,
        queryParameters: queryParams,
        fromJsonT: (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          (item) => OrderResponse.fromJson(item as Map<String, dynamic>),
        ),
      );

      if (!isCurrent()) return;
      final data = response.data;
      if (!response.isSuccess || data == null) {
        throw StateError('Invalid order history response');
      }
      _orders = append
          ? {
              for (final row in [..._orders, ...data.content]) row.id: row,
            }.values.toList()
          : data.content;
      _page = requestedPage;
      _hasMore = !data.last && requestedPage + 1 < data.totalPages;
    } catch (_) {
      if (isCurrent()) _errorMessage = 'Không thể tải lịch sử đơn hàng';
    } finally {
      if (request == _historyRequest) {
        _loadingHistory = false;
        notifyListeners();
      }
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
    _actionError = null;
    try {
      final response = await _apiClient.delete(
        ApiConstants.orderCancelUrl(orderId),
      );
      if (response.isSuccess) {
        await refreshOrders();
        return true;
      } else {
        _actionError = response.message ?? 'Không thể hủy đơn hàng';
      }
    } catch (e) {
      if (e is ApiException) {
        _actionError = e.message;
      } else {
        _actionError = 'Lỗi kết nối khi hủy đơn hàng';
      }
    }
    notifyListeners();
    return false;
  }

  List<OrderResponse> getOrdersByStatus(String statusFilter) {
    if (statusFilter == 'ALL') return _orders;
    return _orders.where((o) => o.status == statusFilter).toList();
  }
}
