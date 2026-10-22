import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/network/api_response.dart';
import '../data/models/spa_model.dart';
import '../core/storage/storage_service.dart';
import '../core/utils/idempotency_key.dart';
import '../data/models/order_model.dart';

class SpaProvider extends ChangeNotifier {
  final ApiClient _apiClient;
  final Map<int, String> _packageKeys = {};
  int? _accountId;
  bool _authManaged = false;
  int _privateGeneration = 0;
  bool _disposed = false;
  int? lastBookedAppointmentId;

  List<SpaService> _services = [];
  List<Appointment> _appointments = [];
  List<UserServiceTicket> _myTickets = [];
  bool _isLoading = false;
  String? _errorMessage;
  String? _servicesError, _appointmentsError, _ticketsError;
  bool _loadingAppointments = false, _loadingTickets = false;
  String _selectedCategory = 'Tất cả';

  List<SpaService> get services => _services;
  List<Appointment> get appointments => _appointments;
  List<UserServiceTicket> get myTickets => _myTickets;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  String? get servicesError => _servicesError;
  String? get appointmentsError => _appointmentsError;
  String? get ticketsError => _ticketsError;
  bool get loadingAppointments => _loadingAppointments;
  bool get loadingTickets => _loadingTickets;
  String get selectedCategory => _selectedCategory;

  List<String> get categories {
    final set = <String>{'Tất cả'};
    for (final s in _services) {
      if (s.category != null && s.category!.isNotEmpty) {
        set.add(s.category!);
      }
    }
    return set.toList();
  }

  List<SpaService> get filteredServices {
    if (_selectedCategory == 'Tất cả') return _services;
    return _services
        .where(
          (s) =>
              s.category?.toLowerCase() == _selectedCategory.toLowerCase() ||
              s.name.toLowerCase().contains(_selectedCategory.toLowerCase()),
        )
        .toList();
  }

  SpaProvider({ApiClient? apiClient}) : _apiClient = apiClient ?? ApiClient() {
    StorageService.authCleared.addListener(_clearPrivateData);
    fetchServices();
  }

  void bindUser(int? accountId) {
    if (_authManaged && accountId == _accountId) return;
    _authManaged = true;
    _accountId = accountId;
    _clearPrivateData(notify: false);
    final generation = _privateGeneration;
    if (accountId != null)
      Future.microtask(() {
        if (generation != _privateGeneration) return;
        fetchAppointments();
        fetchMyTickets();
      });
  }

  void _clearPrivateData({bool notify = true}) {
    _privateGeneration++;
    _packageKeys.clear();
    _appointments = [];
    _myTickets = [];
    lastBookedAppointmentId = null;
    _appointmentsError = _ticketsError = _errorMessage = null;
    _loadingAppointments = _loadingTickets = false;
    if (notify) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _privateGeneration++;
    StorageService.authCleared.removeListener(_clearPrivateData);
    super.dispose();
  }

  void setCategory(String cat) {
    _selectedCategory = cat;
    notifyListeners();
  }

  Future<void> fetchServices() async {
    _isLoading = true;
    _servicesError = null;
    notifyListeners();

    try {
      final response = await _apiClient.get<List<SpaService>>(
        ApiConstants.spaServices,
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list
              .map((e) => SpaService.fromJson(e as Map<String, dynamic>))
              .toList();
        },
      );

      if (response.isSuccess && response.data != null) {
        _services = response.data!;
      } else {
        _services = [];
        _servicesError = 'Không thể tải dịch vụ Spa.';
      }
    } catch (_) {
      _services = [];
      _servicesError = 'Không thể tải dịch vụ Spa.';
    }

    _isLoading = false;
    if (!_disposed) notifyListeners();
  }

  Future<void> fetchAppointments() async {
    if (_authManaged && (_accountId == null || !StorageService.isLoggedIn))
      return;
    final generation = _privateGeneration;
    _loadingAppointments = true;
    _appointmentsError = null;
    notifyListeners();
    try {
      final response = await _apiClient.get<List<Appointment>>(
        ApiConstants.myAppointments,
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list
              .map((e) => Appointment.fromJson(e as Map<String, dynamic>))
              .toList();
        },
      );
      if (generation != _privateGeneration) return;
      if (response.isSuccess && response.data != null) {
        _appointments = response.data!;
      } else {
        _appointments = [];
        _appointmentsError =
            response.message ??
            'Không thể tải lịch hẹn. Vui lòng kiểm tra đăng nhập.';
      }
    } catch (_) {
      if (generation != _privateGeneration) return;
      _appointments = [];
      _appointmentsError =
          'Không thể tải lịch hẹn. Vui lòng kiểm tra đăng nhập.';
    } finally {
      if (generation == _privateGeneration) {
        _loadingAppointments = false;
        notifyListeners();
      }
    }
  }

  Future<void> fetchMyTickets() async {
    if (_authManaged && (_accountId == null || !StorageService.isLoggedIn))
      return;
    final generation = _privateGeneration;
    _loadingTickets = true;
    _ticketsError = null;
    notifyListeners();
    try {
      final response = await _apiClient.get<List<UserServiceTicket>>(
        ApiConstants.spaTickets,
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list
              .map((e) => UserServiceTicket.fromJson(e as Map<String, dynamic>))
              .toList();
        },
      );
      if (generation != _privateGeneration) return;
      if (response.isSuccess && response.data != null) {
        _myTickets = response.data!;
      } else {
        _myTickets = [];
        _ticketsError =
            response.message ??
            'Không thể tải vé Spa. Vui lòng kiểm tra đăng nhập.';
      }
    } catch (_) {
      if (generation != _privateGeneration) return;
      _myTickets = [];
      _ticketsError = 'Không thể tải vé Spa. Vui lòng kiểm tra đăng nhập.';
    } finally {
      if (generation == _privateGeneration) {
        _loadingTickets = false;
        notifyListeners();
      }
    }
  }

  Future<bool> bookAppointment({
    required int serviceId,
    required String serviceName,
    String? branchName,
    required DateTime date,
    required String timeSlot,
    String? notes,
    bool acceptedFinancialPolicy = false,
    int? financialPolicyVersion,
    int? ticketId,
  }) async {
    final generation = _privateGeneration;
    _isLoading = true;
    _errorMessage = null;
    lastBookedAppointmentId = null;
    notifyListeners();

    final dateStr =
        '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    final startTimeStr = timeSlot.contains(':')
        ? (timeSlot.split(':').length == 2 ? '$timeSlot:00' : timeSlot)
        : '$timeSlot:00:00';

    try {
      final response = await _apiClient.post(
        ApiConstants.bookAppointment,
        data: {
          'appointmentDate': dateStr,
          'startTime': startTimeStr,
          'notes': notes ?? '',
          'acceptedFinancialPolicy': acceptedFinancialPolicy,
          'financialPolicyVersion': financialPolicyVersion,
          'items': [
            {
              'serviceId': serviceId,
              if (ticketId != null) 'ticketId': ticketId,
            },
          ],
        },
      );
      if (_disposed || generation != _privateGeneration) return false;
      if (response.isSuccess) {
        final data = response.data;
        final id = data is Map ? int.tryParse('${data['id']}') : null;
        lastBookedAppointmentId = id != null && id > 0 ? id : null;
        await fetchAppointments();
        return !_disposed && generation == _privateGeneration;
      } else {
        _errorMessage = response.message ?? 'Không thể đặt lịch hẹn';
      }
    } catch (e) {
      if (_disposed || generation != _privateGeneration) return false;
      if (e is ApiException) {
        _errorMessage = e.message;
      } else {
        _errorMessage = 'Lỗi kết nối khi gửi yêu cầu đặt lịch hẹn';
      }
    } finally {
      if (!_disposed && generation == _privateGeneration) {
        _isLoading = false;
        notifyListeners();
      }
    }

    return false;
  }

  Future<List<Map<String, dynamic>>> servicePackages() async {
    final response = await _apiClient.get<List<Map<String, dynamic>>>(
      ApiConstants.spaPackages,
      fromJsonT: (json) => (json as List)
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList(),
    );
    if (!response.isSuccess || response.data == null)
      throw StateError(response.message ?? 'Không tải được gói dịch vụ');
    return response.data!;
  }

  Future<OrderResponse> purchasePackage(Map<String, dynamic> pack) async {
    final generation = _privateGeneration;
    final id = (pack['id'] as num).toInt();
    final key = _packageKeys.putIfAbsent(id, generateIdempotencyKey);
    final response = await _apiClient.post<OrderResponse>(
      '/api/v1/spa/tickets/purchase',
      data: {'packageId': id, 'purchaseToken': pack['purchaseToken']},
      headers: {'Idempotency-Key': key},
      fromJsonT: (json) =>
          OrderResponse.fromJson(Map<String, dynamic>.from(json as Map)),
    );
    if (generation != _privateGeneration)
      throw StateError(
        'Phiên đăng nhập đã thay đổi. Kiểm tra Đơn hàng của tôi.',
      );
    if (!response.isSuccess || response.data == null || response.data!.id <= 0)
      throw StateError(
        response.message ??
            'Chưa xác minh được đơn. Thử lại giữ nguyên yêu cầu.',
      );
    _packageKeys.remove(id);
    return response.data!;
  }

  Future<bool> cancelAppointment(int appointmentId) async {
    try {
      final response = await _apiClient.put(
        ApiConstants.appointmentCancelUrl(appointmentId),
        data: {'reason': 'Khách hàng hủy lịch hẹn'},
      );
      if (response.isSuccess) {
        await fetchAppointments();
        return true;
      } else {
        _errorMessage = response.message ?? 'Không thể hủy lịch hẹn';
      }
    } catch (e) {
      if (e is ApiException) {
        _errorMessage = e.message;
      } else {
        _errorMessage = 'Lỗi kết nối khi hủy lịch hẹn';
      }
    }
    return false;
  }
}
