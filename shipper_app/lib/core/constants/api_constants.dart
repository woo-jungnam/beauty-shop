import 'package:flutter/foundation.dart';

import '../storage/storage_service.dart';

class ApiConstants {
  static const String defaultLocalUrl = 'http://localhost:8080';
  static const String defaultProdUrl = 'https://api.dermascan.world';
  static String? _customBaseUrl;

  static String? _validUrl(String? value) {
    final url = Uri.tryParse(value?.trim() ?? '');
    if (url == null ||
        url.host.isEmpty ||
        url.userInfo.isNotEmpty ||
        url.hasQuery ||
        url.hasFragment ||
        url.pathSegments.any((part) => part.isNotEmpty) ||
        !(url.scheme == 'https' || (kDebugMode && url.scheme == 'http'))) {
      return null;
    }
    return url.origin;
  }

  static void setBaseUrl(String url) {
    final valid = _validUrl(url);
    if (valid == null) {
      throw const FormatException('Địa chỉ máy chủ không hợp lệ');
    }
    _customBaseUrl = valid;
    StorageService.setBaseUrl(valid);
  }

  static String get baseUrl =>
      _customBaseUrl ??
      _validUrl(const String.fromEnvironment('BACKEND_URL')) ??
      _validUrl(StorageService.getBaseUrl()) ??
      (kDebugMode ? defaultLocalUrl : defaultProdUrl);

  static const Duration connectTimeout = Duration(seconds: 25);
  static const Duration receiveTimeout = Duration(seconds: 60);

  // Auth
  static const String login = '/api/v1/auth/login';
  static const refresh = '/api/v1/auth/refresh';
  static const String logout = '/api/v1/auth/logout';
  static const String profile = '/api/v1/auth/profile';

  static const notifications = '/api/v1/users/notifications';
  static const unreadNotifications = '$notifications/unread-count';
  static String notificationRead(int id) => '$notifications/$id/read';
  static const shipperHistory = '/api/v1/shipper/history';
  static const shipperStats = '/api/v1/shipper/stats';

  // Shipper Deliveries
  static const String shipperOrders = '/api/v1/shipper/orders';
  static String shipperOrderDetail(int id) => '/api/v1/shipper/orders/$id';
  static String shipperStartDelivery(int id) =>
      '/api/v1/shipper/orders/$id/start';
  static String shipperReportFailure(int id) => '$shipperOrders/$id/failed';
  static String shipperCompleteDelivery(int id) =>
      '/api/v1/shipper/orders/$id/delivered';
}
