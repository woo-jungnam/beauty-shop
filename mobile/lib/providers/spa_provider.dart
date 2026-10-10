import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/network/api_response.dart';
import '../data/models/spa_model.dart';

class SpaProvider extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();

  List<SpaService> _services = [];
  List<Appointment> _appointments = [];
  List<UserServiceTicket> _myTickets = [];
  bool _isLoading = false;
  String? _errorMessage;
  String _selectedCategory = 'Tất cả';

  List<SpaService> get services => _services;
  List<Appointment> get appointments => _appointments;
  List<UserServiceTicket> get myTickets => _myTickets;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
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
    return _services.where((s) =>
        s.category?.toLowerCase() == _selectedCategory.toLowerCase() ||
        s.name.toLowerCase().contains(_selectedCategory.toLowerCase())).toList();
  }

  SpaProvider() {
    fetchServices();
    fetchAppointments();
    fetchMyTickets();
  }

  void setCategory(String cat) {
    _selectedCategory = cat;
    notifyListeners();
  }

  Future<void> fetchServices() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _apiClient.get<List<SpaService>>(
        ApiConstants.spaServices,
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list.map((e) => SpaService.fromJson(e as Map<String, dynamic>)).toList();
        },
      );

      if (response.isSuccess && response.data != null) {
        _services = response.data!;
      } else {
        _services = [];
      }
    } catch (_) {
      _services = [];
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<void> fetchAppointments() async {
    try {
      final response = await _apiClient.get<List<Appointment>>(
        ApiConstants.myAppointments,
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list.map((e) => Appointment.fromJson(e as Map<String, dynamic>)).toList();
        },
      );
      if (response.isSuccess && response.data != null) {
        _appointments = response.data!;
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<void> fetchMyTickets() async {
    try {
      final response = await _apiClient.get<List<UserServiceTicket>>(
        ApiConstants.spaTickets,
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list.map((e) => UserServiceTicket.fromJson(e as Map<String, dynamic>)).toList();
        },
      );
      if (response.isSuccess && response.data != null) {
        _myTickets = response.data!;
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<bool> bookAppointment({
    required int serviceId,
    required String serviceName,
    String? branchName,
    required DateTime date,
    required String timeSlot,
    String? notes,
  }) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    final dateStr = '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    final startTimeStr = timeSlot.contains(':') ? (timeSlot.split(':').length == 2 ? '$timeSlot:00' : timeSlot) : '$timeSlot:00:00';

    try {
      final response = await _apiClient.post(
        ApiConstants.bookAppointment,
        data: {
          'appointmentDate': dateStr,
          'startTime': startTimeStr,
          'notes': notes ?? '',
          'items': [
            {'serviceId': serviceId}
          ],
        },
      );
      if (response.isSuccess) {
        await fetchAppointments();
        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _errorMessage = response.message ?? 'Không thể đặt lịch hẹn';
      }
    } catch (e) {
      if (e is ApiException) {
        _errorMessage = e.message;
      } else {
        _errorMessage = 'Lỗi kết nối khi gửi yêu cầu đặt lịch hẹn';
      }
    } finally {
      _isLoading = false;
      notifyListeners();
    }

    return false;
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
