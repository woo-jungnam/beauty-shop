import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/network/api_response.dart';
import '../data/models/order_model.dart';

class OrderProvider extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();

  List<OrderResponse> _orders = [];
  bool _isLoading = false;
  String? _errorMessage;

  List<OrderResponse> get orders => _orders;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;

  OrderProvider() {
    fetchOrders();
  }

  Future<void> fetchOrders() async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final response = await _apiClient.get<PageResponse<OrderResponse>>(
        ApiConstants.myOrders,
        queryParameters: {'page': 0, 'size': 50, 'sort': 'id,desc'},
        fromJsonT: (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          (item) => OrderResponse.fromJson(item as Map<String, dynamic>),
        ),
      );

      if (response.isSuccess && response.data != null) {
        _orders = response.data!.content;
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
        fromJsonT: (json) => OrderResponse.fromJson(json as Map<String, dynamic>),
      );

      if (response.isSuccess && response.data != null) {
        final createdOrder = response.data!;
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
        fromJsonT: (json) => OrderResponse.fromJson(json as Map<String, dynamic>),
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
