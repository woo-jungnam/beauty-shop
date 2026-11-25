import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/storage/storage_service.dart';
import '../data/models/shipper_account_model.dart';
import '../data/models/shipper_order_model.dart';
import '../data/models/shipper_return_model.dart';

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
    _isBackgroundLoading = false;
    _errorMessage = null;
    _currentFilter = 'ALL';
    _searchKeyword = '';
    _ordersError = null;
    _currentPage = 0;
    _totalPages = 0;
    _totalElements = 0;
    _pageSize = defaultPageSize;
    _hasMore = false;
    _userProfile = null;
    _workProfile = null;
    _isProfileLoading = false;
    _profileError = null;
    _returns = [];
    _isReturnsLoading = false;
    _isReturnsBackgroundLoading = false;
    _returnsHasMore = false;
    _returnPage = 0;
    _returnTotalPages = 0;
    _returnTotalElements = 0;
    _returnPageSize = defaultPageSize;
    _returnsRequest = 0;
    _returnsError = null;
    _returnFilter = 'ALL';
    _returnSearchKeyword = '';
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

  static const int defaultPageSize = 10;
  static const List<int> pageSizeOptions = [10, 20, 50];

  List<ShipperOrderModel> _orders = [];
  bool _isLoading = false;
  bool _isBackgroundLoading = false;
  bool _hasMore = false;
  int _currentPage = 0;
  int _totalPages = 0;
  int _totalElements = 0;
  int _pageSize = defaultPageSize;
  int _ordersRequest = 0;
  String? _ordersError;
  String? _errorMessage;
  String _currentFilter = 'ALL';
  String _searchKeyword = '';

  List<ShipperOrderModel> get orders => _orders;
  bool get isLoading => _isLoading;
  bool get hasMore => _hasMore;
  int get currentPage => _currentPage;
  int get totalPages => _totalPages;
  int get totalElements => _totalElements;
  int get pageSize => _pageSize;
  bool get hasPreviousPage => currentPage > 0;
  bool get hasNextPage =>
      totalPages > 0 ? (currentPage + 1 < totalPages) : hasMore;
  String? get ordersError => _ordersError;
  String? get errorMessage => _errorMessage;
  String get currentFilter => _currentFilter;
  String get searchKeyword => _searchKeyword;

  Future<void> setFilter(String filter) async {
    if (_currentFilter == filter) return;
    _currentFilter = filter;
    _currentPage = 0;
    notifyListeners();
    await fetchOrders(page: 0);
  }

  Future<void> setPageSize(int size) async {
    if (!pageSizeOptions.contains(size) || _pageSize == size) return;
    _pageSize = size;
    _currentPage = 0;
    notifyListeners();
    await fetchOrders(page: 0);
  }

  Future<void> setSearchKeyword(String keyword) async {
    final normalized = keyword.trim();
    if (_searchKeyword == normalized) return;
    _searchKeyword = normalized;
    _currentPage = 0;
    notifyListeners();
    await fetchOrders(page: 0);
  }

  Future<void> goToPage(int page) async {
    if (page < 0) return;
    if (totalPages > 0 && page >= totalPages) return;
    if (page == currentPage && ordersError == null) return;
    await fetchOrders(page: page);
  }

  Future<void> nextPage() async {
    if (!hasNextPage) return;
    await goToPage(currentPage + 1);
  }

  Future<void> previousPage() async {
    if (!hasPreviousPage) return;
    await goToPage(currentPage - 1);
  }

  Future<void> fetchOrders({
    int? page,
    bool background = false,
  }) async {
    if (background && (_isLoading || _isBackgroundLoading)) return;
    final session = _session;
    final request = ++_ordersRequest;
    var targetPage = (page ?? _currentPage) < 0 ? 0 : (page ?? _currentPage);
    if (background) {
      _isBackgroundLoading = true;
    } else {
      _orders = [];
      _currentPage = targetPage;
      _isLoading = true;
      _ordersError = null;
      notifyListeners();
    }

    try {
      final filter = _currentFilter;
      final keyword = _searchKeyword;
      for (var attempt = 0; attempt < 2; attempt++) {
        final data = await _fetchData(ApiConstants.shipperOrders, {
          'page': targetPage,
          'size': _pageSize,
          if (filter != 'ALL') 'status': filter,
          if (keyword.isNotEmpty) 'keyword': keyword,
        });
        if (session != _session || request != _ordersRequest) return;
        final rawList = data['content'] as List? ?? const [];
        final rows = rawList
            .cast<Map<String, dynamic>>()
            .map(ShipperOrderModel.fromJson)
            .toList();
        final parsedPage = (data['page'] as num?)?.toInt() ?? targetPage;
        final parsedTotalElements =
            (data['totalElements'] as num?)?.toInt() ?? rows.length;
        final parsedSize = (data['size'] as num?)?.toInt() ?? _pageSize;
        final parsedTotalPages = (data['totalPages'] as num?)?.toInt() ??
            (parsedTotalElements == 0
                ? 0
                : ((parsedTotalElements + parsedSize - 1) ~/ parsedSize));
        if (rows.isEmpty &&
            parsedTotalPages > 0 &&
            targetPage >= parsedTotalPages &&
            attempt == 0) {
          targetPage = parsedTotalPages - 1;
          continue;
        }
        final isLast = data['last'] is bool
            ? data['last'] as bool
            : (parsedTotalPages == 0 || parsedPage + 1 >= parsedTotalPages);
        _orders = rows;
        _currentPage = parsedPage;
        _totalElements = parsedTotalElements;
        _totalPages = parsedTotalPages;
        _hasMore = !isLast;
        _ordersError = null;
        break;
      }
    } catch (e) {
      if (!background && session == _session && request == _ordersRequest) {
        _ordersError = e.toString();
      }
    } finally {
      if (session == _session) {
        if (background) {
          _isBackgroundLoading = false;
          if (!_isLoading && request == _ordersRequest) notifyListeners();
        } else if (request == _ordersRequest) {
          _isLoading = false;
          notifyListeners();
        }
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

  List<ShipperReturnModel> _returns = [];
  bool _isReturnsLoading = false;
  bool _isReturnsBackgroundLoading = false;
  bool _returnsHasMore = false;
  int _returnPage = 0;
  int _returnTotalPages = 0;
  int _returnTotalElements = 0;
  int _returnPageSize = defaultPageSize;
  int _returnsRequest = 0;
  String? _returnsError;
  String _returnFilter = 'ALL';
  String _returnSearchKeyword = '';

  List<ShipperReturnModel> get returnPickups => _returns;
  bool get isReturnsLoading => _isReturnsLoading;
  bool get hasMoreReturns => _returnsHasMore;
  int get returnCurrentPage => _returnPage;
  int get returnTotalPages => _returnTotalPages;
  int get returnTotalElements => _returnTotalElements;
  int get returnPageSize => _returnPageSize;
  bool get hasPreviousReturnPage => _returnPage > 0;
  bool get hasNextReturnPage =>
      _returnTotalPages > 0 ? (_returnPage + 1 < _returnTotalPages) : _returnsHasMore;
  String? get returnsError => _returnsError;
  String get returnFilter => _returnFilter;
  String get returnSearchKeyword => _returnSearchKeyword;
  int get activeReturnPickupCount =>
      _returns.where((r) => r.isPickupAssigned || r.isPickedUp).length;

  Future<void> setReturnFilter(String filter) async {
    if (_returnFilter == filter) return;
    _returnFilter = filter;
    _returnPage = 0;
    notifyListeners();
    if (filter == 'RETURNING_TO_SENDER') {
      return;
    }
    await fetchReturnPickups(page: 0);
  }

  Future<void> setReturnPageSize(int size) async {
    if (!pageSizeOptions.contains(size) || _returnPageSize == size) return;
    _returnPageSize = size;
    _returnPage = 0;
    notifyListeners();
    await fetchReturnPickups(page: 0);
  }

  Future<void> setReturnSearchKeyword(String keyword) async {
    final normalized = keyword.trim();
    if (_returnSearchKeyword == normalized) return;
    _returnSearchKeyword = normalized;
    _returnPage = 0;
    notifyListeners();
    await fetchReturnPickups(page: 0);
  }

  Future<void> goToReturnPage(int page) async {
    if (page < 0) return;
    if (_returnTotalPages > 0 && page >= _returnTotalPages) return;
    if (page == _returnPage && _returnsError == null) return;
    await fetchReturnPickups(page: page);
  }

  Future<void> nextReturnPage() async {
    if (!hasNextReturnPage) return;
    await goToReturnPage(_returnPage + 1);
  }

  Future<void> previousReturnPage() async {
    if (!hasPreviousReturnPage) return;
    await goToReturnPage(_returnPage - 1);
  }

  Future<ShipperReturnModel> fetchReturnDetail(int returnId) async {
    final data = await _fetchData(ApiConstants.shipperReturnDetail(returnId), {});
    return ShipperReturnModel.fromJson(data);
  }

  Future<void> fetchReturnPickups({
    int? page,
    bool background = false,
  }) async {
    if (background && (_isReturnsLoading || _isReturnsBackgroundLoading)) return;
    final session = _session;
    final request = ++_returnsRequest;
    var targetPage = (page ?? _returnPage) < 0 ? 0 : (page ?? _returnPage);
    if (background) {
      _isReturnsBackgroundLoading = true;
    } else {
      _returns = [];
      _returnPage = targetPage;
      _isReturnsLoading = true;
      _returnsError = null;
      notifyListeners();
    }

    try {
      final filter = _returnFilter;
      final keyword = _returnSearchKeyword;
      final statusParam = (filter == 'ALL' || filter == 'RETURNING_TO_SENDER')
          ? null
          : filter;
      for (var attempt = 0; attempt < 2; attempt++) {
        final data = await _fetchData(ApiConstants.shipperReturns, {
          'page': targetPage,
          'size': _returnPageSize,
          'status': ?statusParam,
          if (keyword.isNotEmpty) 'keyword': keyword,
        });
        if (session != _session || request != _returnsRequest) return;
        final rawList = data['content'] as List? ?? const [];
        final rows = rawList
            .whereType<Map<String, dynamic>>()
            .map(ShipperReturnModel.fromJson)
            .toList();
        final parsedPage = (data['page'] as num?)?.toInt() ?? targetPage;
        final parsedTotalElements =
            (data['totalElements'] as num?)?.toInt() ?? rows.length;
        final parsedSize = (data['size'] as num?)?.toInt() ?? _returnPageSize;
        final parsedTotalPages = (data['totalPages'] as num?)?.toInt() ??
            (parsedTotalElements == 0
                ? 0
                : ((parsedTotalElements + parsedSize - 1) ~/ parsedSize));
        if (rows.isEmpty &&
            parsedTotalPages > 0 &&
            targetPage >= parsedTotalPages &&
            attempt == 0) {
          targetPage = parsedTotalPages - 1;
          continue;
        }
        final isLast = data['last'] is bool
            ? data['last'] as bool
            : (parsedTotalPages == 0 || parsedPage + 1 >= parsedTotalPages);
        _returns = rows;
        _returnPage = parsedPage;
        _returnTotalElements = parsedTotalElements;
        _returnTotalPages = parsedTotalPages;
        _returnsHasMore = !isLast;
        _returnsError = null;
        break;
      }
    } catch (e) {
      if (!background && session == _session && request == _returnsRequest) {
        _returnsError = e.toString();
      }
    } finally {
      if (session == _session) {
        if (background) {
          _isReturnsBackgroundLoading = false;
          if (!_isReturnsLoading && request == _returnsRequest) notifyListeners();
        } else if (request == _returnsRequest) {
          _isReturnsLoading = false;
          notifyListeners();
        }
      }
    }
  }

  Future<void> startReturnPickup(int returnId) async {
    final session = _session;
    final response = await ApiClient().patch<Map<String, dynamic>>(
      ApiConstants.shipperStartReturnPickup(returnId),
    );
    if (!response.isSuccess) {
      throw response.message ?? 'Không thể bắt đầu thu hồi hàng hoàn';
    }
    if (session == _session) await fetchReturnPickups();
  }

  Future<bool> completeReturnPickup({
    required int returnId,
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
        ApiConstants.shipperCompleteReturnPickup(returnId),
        data: formData,
      );

      if (response.isSuccess) {
        if (session == _session) await fetchReturnPickups();
        return true;
      } else {
        _errorMessage = response.message ?? 'Xác nhận thu hồi hàng hoàn thất bại';
        return false;
      }
    } catch (e) {
      _errorMessage = e.toString();
      return false;
    }
  }

  Future<void> reportReturnPickupFailure(int returnId, String reason) async {
    final session = _session;
    final response = await ApiClient().post<Map<String, dynamic>>(
      ApiConstants.shipperFailReturnPickup(returnId),
      data: {'reason': reason},
    );
    if (!response.isSuccess) {
      throw response.message ?? 'Không thể báo thu hồi thất bại';
    }
    if (session == _session) await fetchReturnPickups();
  }

  Future<void> handoverReturnToWarehouse(int returnId, {String? notes}) async {
    final session = _session;
    final response = await ApiClient().post<Map<String, dynamic>>(
      ApiConstants.shipperHandoverReturn(returnId),
      data: {
        if (notes != null && notes.trim().isNotEmpty) 'notes': notes.trim(),
      },
    );
    if (!response.isSuccess) {
      throw response.message ?? 'Không thể báo bàn giao hàng hoàn về kho';
    }
    if (session == _session) await fetchReturnPickups();
  }
}
