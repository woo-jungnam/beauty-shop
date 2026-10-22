import 'package:flutter/foundation.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/network/api_response.dart';
import '../core/storage/storage_service.dart';
import '../core/utils/idempotency_key.dart';
import '../data/models/auth_models.dart';
import '../data/models/order_model.dart';
import '../data/models/treatment_model.dart';

class TreatmentProvider extends ChangeNotifier {
  TreatmentProvider({ApiClient? apiClient}) : _api = apiClient ?? ApiClient() {
    StorageService.authCleared.addListener(_clearSession);
  }
  final ApiClient _api;
  String? _identity;
  int _generation = 0;
  bool _disposed = false;
  int? userId;
  bool isStaff = false, isAdmin = false;
  bool canManageReception = false, canExecuteSpa = false;
  final Map<String, String> _purchaseKeys = {};

  void _clearSession() {
    bindUser(null, authenticated: false);
    if (!_disposed) notifyListeners();
  }

  void bindUser(User? user, {required bool authenticated}) {
    final roles = user?.normalizedRoles.toList() ?? <String>[];
    roles.sort();
    final permissions = [...?user?.permissions]..sort();
    final identity = authenticated && user != null
        ? '${user.id}:${roles.join(',')}:${permissions.join(',')}'
        : null;
    if (_identity == identity) return;
    _identity = identity;
    userId = authenticated ? user?.id : null;
    isStaff =
        authenticated && user?.isStaff == true && user?.canOperateSpa == true;
    isAdmin = authenticated && user?.isAdmin == true;
    canManageReception =
        authenticated && user?.hasPermission('SPA_RECEPTION') == true;
    canExecuteSpa =
        authenticated && user?.hasPermission('SPA_EXECUTION') == true;
    _generation++;
    _purchaseKeys.clear();
  }

  int get generation => _generation;
  bool get authenticated => _identity != null;
  bool canUse(TreatmentArea area) =>
      authenticated &&
      switch (area) {
        TreatmentArea.customer => true,
        TreatmentArea.staff => isStaff || isAdmin,
        TreatmentArea.admin => isAdmin,
      };

  void _checkSession(int generation) {
    if (_disposed || generation != _generation) {
      throw const TreatmentFailure(
        'Phiên đăng nhập đã thay đổi. Vui lòng mở lại hồ sơ.',
      );
    }
  }

  Future<T> _read<T>(
    String path,
    T Function(dynamic) parse, {
    Map<String, dynamic>? query,
    bool private = true,
  }) async {
    if (private && !authenticated)
      throw const TreatmentFailure('Vui lòng đăng nhập.');
    final generation = _generation;
    try {
      final response = await _api.get<T>(
        path,
        queryParameters: query,
        fromJsonT: parse,
      );
      _checkSession(generation);
      if (!response.isSuccess || response.data == null) {
        throw TreatmentFailure(response.message ?? 'Không thể tải dữ liệu.');
      }
      return response.data as T;
    } on ApiException catch (error) {
      throw TreatmentFailure(error.message);
    }
  }

  Future<dynamic> write(String path, JsonMap data, {bool put = false}) async {
    if (!authenticated) throw const TreatmentFailure('Vui lòng đăng nhập.');
    final generation = _generation;
    try {
      final response = put
          ? await _api.put<dynamic>(path, data: data)
          : await _api.post<dynamic>(path, data: data);
      _checkSession(generation);
      if (!response.isSuccess) {
        throw TreatmentFailure(
          response.message ?? 'Máy chủ chưa xác nhận thao tác.',
        );
      }
      return response.data;
    } on ApiException catch (error) {
      throw TreatmentFailure(error.message);
    }
  }

  Future<List<TreatmentTemplate>> templates({bool admin = false}) => _read(
    admin
        ? ApiConstants.adminTreatmentTemplates
        : ApiConstants.treatmentTemplates,
    (data) => treatmentMaps(data).map(TreatmentTemplate.fromJson).toList(),
    private: admin,
  );

  Future<TreatmentTemplate> template(int id) => _read(
    '${ApiConstants.treatmentTemplates}/$id',
    (data) => TreatmentTemplate.fromJson(treatmentMap(data)),
    private: false,
  );

  Future<List<TreatmentEnrollment>> enrollments(TreatmentArea area) {
    if (!canUse(area))
      throw const TreatmentFailure('Bạn không có quyền vào khu vực này.');
    return _read(
      area == TreatmentArea.customer ? ApiConstants.myTreatments : area.path,
      (data) => treatmentMaps(data).map(TreatmentEnrollment.fromJson).toList(),
    );
  }

  Future<TreatmentEnrollment> enrollment(int id, TreatmentArea area) {
    if (!canUse(area))
      throw const TreatmentFailure('Bạn không có quyền xem hồ sơ.');
    return _read(
      '${area.path}/$id',
      (data) => TreatmentEnrollment.fromJson(treatmentMap(data)),
    );
  }

  Future<OrderResponse> purchase(TreatmentTemplate template) async {
    final key = _purchaseKeys.putIfAbsent(
      '${template.id}:${template.versionId}',
      generateIdempotencyKey,
    );
    final data = await write(ApiConstants.treatmentPurchases, {
      'templateId': template.id,
      'versionId': template.versionId,
      'acceptedTerms': true,
      'idempotencyKey': key,
    });
    final order = OrderResponse.fromJson(treatmentMap(data));
    if (order.id <= 0)
      throw const TreatmentFailure(
        'Máy chủ chưa trả về mã đơn hàng. Hãy kiểm tra Đơn hàng trước khi gửi lại.',
      );
    _purchaseKeys.remove('${template.id}:${template.versionId}');
    return order;
  }

  Future<List<String>> slots(
    int id,
    int sessionId,
    String date, {
    int? appointmentId,
  }) => _read(
    '/api/v1/spa/treatments/$id/sessions/$sessionId/available-slots',
    (data) => (data as List).map((value) => value.toString()).toList(),
    query: {
      'date': date,
      if (appointmentId != null) 'appointmentId': appointmentId,
    },
  );

  Future<JsonMap> appointment(int id) =>
      _read('/api/v1/appointments/$id', treatmentMap);
  Future<JsonMap> finance(int id) =>
      _read('/api/v1/spa/appointments/$id/finance', treatmentMap);

  Future<JsonMap> createDepositPayment(int id) async {
    final data = treatmentMap(
      await write('/api/v1/spa/appointments/$id/deposit-payment', {}),
    );
    if (data.isEmpty) {
      throw const TreatmentFailure('Máy chủ chưa trả về thông tin cọc.');
    }
    return data;
  }

  Future<JsonMap?> invoice(int id) async {
    if (!authenticated) throw const TreatmentFailure('Vui lòng đăng nhập.');
    final generation = _generation;
    try {
      final response = await _api.get<JsonMap>(
        '/api/v1/appointments/$id/invoice',
        fromJsonT: treatmentMap,
      );
      _checkSession(generation);
      if (response.status == 404) return null;
      if (!response.isSuccess || response.data == null) {
        throw TreatmentFailure(response.message ?? 'Không thể tải hóa đơn.');
      }
      return response.data;
    } on ApiException catch (error) {
      _checkSession(generation);
      if (error.statusCode == 404) return null;
      throw TreatmentFailure(error.message);
    }
  }

  Future<List<String>> appointmentSlots(int id, String date) => _read(
    '/api/v1/appointments/$id/available-slots',
    (data) => (data as List).map((slot) => '$slot').toList(),
    query: {'date': date},
  );
  Future<List<JsonMap>> preparation(int id) =>
      _read('/api/v1/spa/appointments/$id/preparation', treatmentMaps);
  Future<JsonMap> careSummary(int id) =>
      _read('/api/v1/spa/appointments/$id/care-summary', treatmentMap);
  Future<List<JsonMap>> instructions(int id) => _read(
    '/api/v1/spa/appointments/$id/follow-up-instructions',
    treatmentMaps,
  );
  Future<List<JsonMap>> notifications() =>
      _read(ApiConstants.treatmentNotifications, treatmentMaps);
  Future<List<JsonMap>> staff() =>
      _read('/api/v1/spa/services/staff', treatmentMaps);

  Future<Set<int>> currentStaffIds() async {
    if (!canExecuteSpa || isAdmin) return <int>{};
    final people = await staff();
    return people
        .where((person) => treatmentInt(person['userId']) == userId)
        .map((person) => treatmentInt(person['id']))
        .where((id) => id > 0)
        .toSet();
  }

  Future<List<JsonMap>> services() =>
      _read('/api/v1/spa/services', treatmentMaps, private: false);
  Future<JsonMap> report() =>
      _read('/api/v1/admin/spa/treatments/report', treatmentMap);

  @override
  void dispose() {
    _disposed = true;
    StorageService.authCleared.removeListener(_clearSession);
    super.dispose();
  }
}

class TreatmentFailure implements Exception {
  const TreatmentFailure(this.message);
  final String message;
  @override
  String toString() => message;
}
