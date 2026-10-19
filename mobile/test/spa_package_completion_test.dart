import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/providers/spa_provider.dart';
import 'package:beautyshop_mobile/data/models/spa_model.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';

class PackageApi implements ApiClient {
  final calls = <Map<String, dynamic>>[];
  bool failPurchase = true;
  @override
  Future<ApiResponse<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async {
    final data = path.endsWith('/my-appointments') ? <dynamic>[] : <dynamic>[];
    return ApiResponse<T>.fromJson({'status': 200, 'data': data}, fromJsonT);
  }

  @override
  Future<ApiResponse<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async {
    calls.add({'path': path, 'data': data, 'headers': headers});
    if (path.endsWith('/purchase') && failPurchase) {
      failPurchase = false;
      throw StateError('Lost response');
    }
    return ApiResponse<T>.fromJson({
      'status': 201,
      'data': path.endsWith('/purchase')
          ? {
              'orderId': 800,
              'servicePackageId': 71,
              'spaBenefitType': 'SERVICE_PACKAGE',
              'totalAmount': 900000,
              'paymentStatus': 'PENDING',
            }
          : {'id': 1},
    }, fromJsonT);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    await StorageService.init();
  });
  test('ticket keeps per-service quota and reservation independently', () {
    final t = UserServiceTicket.fromJson({
      'id': 91,
      'packageName': 'Tên lúc mua',
      'nameSnapshot': true,
      'benefitType': 'SERVICE_PACKAGE',
      'totalSessions': 5,
      'usedSessions': 1,
      'reservedSessions': 2,
      'remainingSessions': 2,
      'status': 'ACTIVE',
      'remainingByService': {'2': 2, '3': 0},
      'services': [
        {
          'serviceId': 2,
          'serviceName': 'Facial',
          'total': 4,
          'used': 0,
          'reserved': 2,
          'available': 2,
        },
      ],
    });
    expect(t.isValid, isTrue);
    expect(t.remainingByService[2], 2);
    expect(t.remainingByService[3], 0);
    expect(t.reservedSessions, 2);
    expect(t.services.single['reserved'], 2);
    expect(t.nameSnapshot, isTrue);
  });
  test('ordinary mobile booking includes the selected ticket', () async {
    final api = PackageApi();
    final provider = SpaProvider(apiClient: api);
    final success = await provider.bookAppointment(
      serviceId: 2,
      serviceName: 'Facial',
      date: DateTime(2026, 11, 1),
      timeSlot: '10:00',
      ticketId: 91,
    );
    expect(success, isTrue);
    expect((api.calls.last['data']['items'] as List).single, {
      'serviceId': 2,
      'ticketId': 91,
    });
    provider.dispose();
  });
  test('cash booking does not silently attach a ticket', () async {
    final api = PackageApi();
    final provider = SpaProvider(apiClient: api);
    await provider.bookAppointment(
      serviceId: 2,
      serviceName: 'Facial',
      date: DateTime(2026, 11, 1),
      timeSlot: '10:00',
    );
    expect((api.calls.last['data']['items'] as List).single, {'serviceId': 2});
    provider.dispose();
  });
  test('purchase retry retains offer and idempotency header', () async {
    final api = PackageApi();
    final provider = SpaProvider(apiClient: api);
    final pack = {'id': 71, 'purchaseToken': 'original-offer'};
    await expectLater(provider.purchasePackage(pack), throwsStateError);
    final order = await provider.purchasePackage(pack);
    expect(order.id, 800);
    expect(order.servicePackageId, 71);
    expect(api.calls[0], api.calls[1]);
    expect(api.calls.first['headers']['Idempotency-Key'], isNotEmpty);
    provider.dispose();
  });
  test('order retains authoritative benefit type for payment navigation', () {
    final order = OrderResponse.fromJson({
      'id': 1,
      'servicePackageId': 71,
      'spaBenefitType': 'TREATMENT_COURSE',
      'paymentStatus': 'PAID',
    });
    expect(order.isPaid, isTrue);
    expect(order.spaBenefitType, 'TREATMENT_COURSE');
  });
}
