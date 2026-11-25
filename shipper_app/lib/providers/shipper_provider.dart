import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/storage/storage_service.dart';
import '../data/models/shipper_account_model.dart';
import '../data/models/shipper_order_model.dart';

class ShipperProvider extends ChangeNotifier {
  int _unreadCount = 0;
  int _session = 0;

  @override
  void dispose() {
    _session++;
    super.dispose();
  }

  Future<void>? _polling;
  int get unreadCount => _unreadCount;

  void clearSession() {
    _session++;
    _orders = [];
    _unreadCount = 0;
    _polling = null;
    _isLoading = false;
    _errorMessage = null;
    _currentFilter = 'ALL';
    _ordersError = null;
    _nextPage = 0;
    _hasMore = false;
    _userProfile = null;
    _workProfile = null;
    _isProfileLoading = false;
    _profileError = null;
    notifyListeners();
  }

  Future<void> refreshUnreadCount() => _polling ??= _fetchUnreadCount(_session);

  Future<void> _fetchUnreadCount(int session) async {
    try {
      final response = await ApiClient().get<int>(
        ApiConstants.unreadNotifications,
      );
      if (session == _session && response.isSuccess && response.data != null) {
        _unreadCount = response.data!;
        notifyListeners();
      }
    } catch (_) {
      // Keep the previous badge if a poll fails.
    } finally {
      if (session == _session) _polling = null;
    }
  }

  Future<Map<String, dynamic>> fetchNotifications(int page) =>
      _fetchData(ApiConstants.notifications, {'page': page});

  Future<void> markRead(int id) async {
    final session = _session;
    final response = await ApiClient().put<Map<String, dynamic>>(
      ApiConstants.notificationRead(id),
    );
    if (!response.isSuccess) {
      throw response.message ?? 'Không thể cập nhật thông báo';
    }
    await _polling;
    if (session == _session) await refreshUnreadCount();
  }

  Future<Map<String, dynamic>> fetchHistory(
    String from,
    String to,
    int page,
    bool deliveredOnly,
  ) => _fetchData(ApiConstants.shipperHistory, {
    'from': from,
    'to': to,
    'page': page,
    if (deliveredOnly) 'status': 'DELIVERED',
  });

  Future<Map<String, dynamic>> fetchStats(String from, String to) =>
      _fetchData(ApiConstants.shipperStats, {'from': from, 'to': to});

  Future<ShipperOrderModel> fetchOrderDetail(int orderId) async {
    final data = await _fetchData(ApiConstants.shipperOrderDetail(orderId), {});
    return ShipperOrderModel.fromJson(data);
  }

  ShipperUserProfile? _userProfile;
  ShipperWorkProfile? _workProfile;
  bool _isProfileLoading = false;
  String? _profileError;

  ShipperUserProfile? get userProfile => _userProfile;
  ShipperWorkProfile? get workProfile => _workProfile;
  bool get isProfileLoading => _isProfileLoading;
  String? get profileError => _profileError;

  Future<void> fetchAccountProfile() async {
    final session = _session;
    _isProfileLoading = true;
    _profileError = null;
    notifyListeners();

    try {
      final results = await Future.wait([
        _fetchData(ApiConstants.userProfile, {}),
        _fetchData(ApiConstants.shipperProfile, {}),
      ]);
      if (session != _session) return;
      final user = ShipperUserProfile.fromJson(results[0]);
      final work = ShipperWorkProfile.fromJson(results[1]);
      _userProfile = user;
      _workProfile = work;
      if (user.fullName.isNotEmpty) {
        await StorageService.setUserInfo(user.username, user.fullName);
      }
    } catch (e) {
      if (session == _session) {
        _profileError = e.toString();
      }
    } finally {
      if (session == _session) {
        _isProfileLoading = false;
        notifyListeners();
      }
    }
  }

  Future<ShipperUserProfile> updateUserProfile({
    required String fullName,
    String? phone,
    String? avatarUrl,
    String? bio,
    String? gender,
    String? dateOfBirth,
  }) async {
    final session = _session;
    final payload = <String, dynamic>{
      'fullName': fullName.trim(),
      'phone': (phone ?? '').trim(),
      'bio': (bio ?? '').trim(),
      if (avatarUrl != null) 'avatarUrl': avatarUrl.trim(),
      if (gender != null && gender.trim().isNotEmpty) 'gender': gender.trim(),
      if (dateOfBirth != null && dateOfBirth.trim().isNotEmpty)
        'dateOfBirth': dateOfBirth.trim(),
    };
    final response = await ApiClient().put<Map<String, dynamic>>(
      ApiConstants.userProfile,
      data: payload,
    );
    if (!response.isSuccess || response.data == null) {
      throw response.message ?? 'Không thể cập nhật thông tin cá nhân';
    }
    final updated = ShipperUserProfile.fromJson(response.data!);
    if (session == _session) {
      _userProfile = updated;
      if (updated.fullName.isNotEmpty) {
        await StorageService.setUserInfo(updated.username, updated.fullName);
      }
      notifyListeners();
    }
    return updated;
  }

  Future<String> uploadAvatar(XFile imageFile) async {
    final MultipartFile filePart;
    if (kIsWeb) {
      final bytes = await imageFile.readAsBytes();
      filePart = MultipartFile.fromBytes(bytes, filename: imageFile.name);
    } else {
      filePart = await MultipartFile.fromFile(
        imageFile.path,
        filename: imageFile.name,
      );
    }
    final formData = FormData.fromMap({'file': filePart});
    final response = await ApiClient().post<Map<String, dynamic>>(
      ApiConstants.shipperAvatar,
      data: formData,
    );
    final url =
        response.data?['url']?.toString() ??
        response.data?['publicUrl']?.toString();
    if (!response.isSuccess || url == null || url.isEmpty) {
      throw response.message ?? 'Không thể tải ảnh đại diện lên máy chủ';
    }
    return url;
  }

  Future<ShipperWorkProfile> updateWorkProfile({
    String? vehicleType,
    String? vehiclePlate,
    String? serviceArea,
    String? emergencyContactName,
    String? emergencyContactPhone,
    String? workShift,
  }) async {
    final session = _session;
    final payload = <String, dynamic>{
      'vehicleType': (vehicleType ?? '').trim(),
      'vehiclePlate': (vehiclePlate ?? '').trim(),
      'serviceArea': (serviceArea ?? '').trim(),
      'emergencyContactName': (emergencyContactName ?? '').trim(),
      'emergencyContactPhone': (emergencyContactPhone ?? '').trim(),
      'workShift': (workShift ?? '').trim(),
    };
    final response = await ApiClient().put<Map<String, dynamic>>(
      ApiConstants.shipperProfile,
      data: payload,
    );
    if (!response.isSuccess || response.data == null) {
      throw response.message ?? 'Không thể cập nhật thông tin tài xế';
    }
    final updated = ShipperWorkProfile.fromJson(response.data!);
    if (session == _session) {
      _workProfile = updated;
      notifyListeners();
    }
    return updated;
  }

  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    final response = await ApiClient().put<dynamic>(
      ApiConstants.changePassword,
      data: {
        'currentPassword': currentPassword,
        'newPassword': newPassword,
      },
    );
    if (!response.isSuccess) {
      throw response.message ?? 'Không thể đổi mật khẩu';
    }
  }

  Future<Map<String, dynamic>> _fetchData(
    String endpoint,
    Map<String, dynamic> query,
  ) async {
    final response = await ApiClient().get<Map<String, dynamic>>(
      endpoint,
      queryParameters: query,
    );
    if (!response.isSuccess || response.data == null) {
      throw response.message ?? 'Không thể tải dữ liệu';
    }
    return response.data!;
  }

  List<ShipperOrderModel> _orders = [];
  bool _isLoading = false;
  bool _hasMore = false;
  int _nextPage = 0;
  int _ordersRequest = 0;
  String? _ordersError;
  String? _errorMessage;
  String _currentFilter = 'ALL';

  List<ShipperOrderModel> get orders => _orders;
  bool get isLoading => _isLoading;
  bool get hasMore => _hasMore;
  String? get ordersError => _ordersError;
  String? get errorMessage => _errorMessage;
  String get currentFilter => _currentFilter;

  Future<void> setFilter(String filter) async {
    if (_currentFilter == filter) return;
    _currentFilter = filter;
    await fetchOrders();
  }

  Future<void> fetchOrders({
    bool loadMore = false,
    bool background = false,
  }) async {
    if (background && _isLoading) return;
    if (loadMore && (_isLoading || !_hasMore)) return;
    final session = _session;
    final request = ++_ordersRequest;
    final page = loadMore ? _nextPage : 0;
    final pages = background && _nextPage > 0 ? _nextPage : 1;
    if (!loadMore && !background) {
      _orders = [];
      _nextPage = 0;
      _hasMore = false;
    }
    _isLoading = true;
    _ordersError = null;
    notifyListeners();

    try {
      final filter = _currentFilter;
      final rows = <ShipperOrderModel>[];
      var hasMore = false;
      var nextPage = page;
      for (var offset = 0; offset < pages; offset++) {
        final data = await _fetchData(ApiConstants.shipperOrders, {
          'page': nextPage,
          'size': 20,
          if (filter != 'ALL') 'status': filter,
        });
        if (session != _session || request != _ordersRequest) return;
        rows.addAll(
          (data['content'] as List).cast<Map<String, dynamic>>().map(
            ShipperOrderModel.fromJson,
          ),
        );
        nextPage++;
        hasMore = data['last'] == false;
        if (!hasMore) break;
      }
      _orders = {
        if (loadMore)
          for (final order in _orders) order.id: order,
        for (final order in rows) order.id: order,
      }.values.toList();
      _nextPage = nextPage;
      _hasMore = hasMore;
    } catch (e) {
      if (!background && session == _session && request == _ordersRequest) {
        _ordersError = e.toString();
      }
    } finally {
      if (session == _session && request == _ordersRequest) {
        _isLoading = false;
        notifyListeners();
      }
    }
  }

  Future<void> startDelivery(int orderId) async {
    final session = _session;
    final response = await ApiClient().patch<Map<String, dynamic>>(
      ApiConstants.shipperStartDelivery(orderId),
    );
    if (!response.isSuccess) throw response.message ?? 'Không thể bắt đầu giao';
    if (session == _session) await fetchOrders();
  }

  Future<void> reportFailure(int orderId, String reason) async {
    final session = _session;
    final response = await ApiClient().post<Map<String, dynamic>>(
      ApiConstants.shipperReportFailure(orderId),
      data: {'reason': reason},
    );
    if (!response.isSuccess) {
      throw response.message ?? 'Không thể báo giao thất bại';
    }
    if (session == _session) await fetchOrders();
  }

  Future<bool> completeDelivery({
    required int orderId,
    required XFile imageFile,
    String? notes,
  }) async {
    final session = _session;
    _errorMessage = null;

    try {
      final map = <String, dynamic>{};
      if (notes != null && notes.trim().isNotEmpty) {
        map['notes'] = notes.trim();
      }
      if (kIsWeb) {
        final bytes = await imageFile.readAsBytes();
        map['proofImage'] = MultipartFile.fromBytes(
          bytes,
          filename: imageFile.name,
        );
      } else {
        map['proofImage'] = await MultipartFile.fromFile(
          imageFile.path,
          filename: imageFile.name,
        );
      }

      final formData = FormData.fromMap(map);
      final response = await ApiClient().post(
        ApiConstants.shipperCompleteDelivery(orderId),
        data: formData,
      );

      if (response.isSuccess) {
        if (session == _session) await fetchOrders();
        return true;
      } else {
        _errorMessage = response.message ?? 'Cập nhật giao hàng thất bại';
        return false;
      }
    } catch (e) {
      _errorMessage = e.toString();
      return false;
    }
  }
}
