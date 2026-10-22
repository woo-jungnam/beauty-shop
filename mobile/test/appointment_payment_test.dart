import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/data/models/auth_models.dart';
import 'package:beautyshop_mobile/data/models/treatment_model.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'package:beautyshop_mobile/providers/treatment_provider.dart';
import 'package:beautyshop_mobile/providers/spa_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/treatments/treatment_appointment_screen.dart';

typedef Handler =
    FutureOr<JsonMap> Function(
      String method,
      String path,
      dynamic data,
      JsonMap? query,
    );

class _ApiStub implements ApiClient {
  _ApiStub(this.handle);
  final Handler handle;
  Future<ApiResponse<T>> _call<T>(
    String method,
    String path,
    dynamic body,
    JsonMap? query,
    T Function(dynamic)? parse,
  ) async =>
      ApiResponse<T>.fromJson(await handle(method, path, body, query), parse);
  @override
  Future<ApiResponse<T>> get<T>(
    String path, {
    JsonMap? queryParameters,
    JsonMap? headers,
    T Function(dynamic)? fromJsonT,
  }) => _call('GET', path, null, queryParameters, fromJsonT);
  @override
  Future<ApiResponse<T>> post<T>(
    String path, {
    dynamic data,
    JsonMap? queryParameters,
    JsonMap? headers,
    T Function(dynamic)? fromJsonT,
  }) => _call('POST', path, data, queryParameters, fromJsonT);
  @override
  Future<ApiResponse<T>> put<T>(
    String path, {
    dynamic data,
    JsonMap? queryParameters,
    JsonMap? headers,
    T Function(dynamic)? fromJsonT,
  }) => _call('PUT', path, data, queryParameters, fromJsonT);
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Auth extends AuthProvider {
  _Auth(this.user);
  User? user;
  @override
  User? get currentUser => user;
  @override
  bool get isAuthenticated => user != null;
  void change(User? value) {
    user = value;
    notifyListeners();
  }
}

JsonMap ok(dynamic value) => {'status': 200, 'data': value};
JsonMap err404() => {'status': 404, 'message': 'Not Found'};
User customer(int id) => User(id: id, username: 'u$id', fullName: 'U $id', roles: ['ROLE_USER']);
User reception(int id) => User(id: id, username: 'r$id', fullName: 'R $id', roles: ['ROLE_STAFF'], permissions: ['SPA_RECEPTION']);
User technician(int id) => User(id: id, username: 't$id', fullName: 'T $id', roles: ['ROLE_STAFF'], permissions: ['SPA_EXECUTION']);

JsonMap financeData({
  num depositRequired = 100000,
  num depositCollected = 0,
  num depositDue = 100000,
  bool closed = false,
  dynamic depositPayment,
  List<String> reviewReasons = const [],
}) => {
  'depositRequired': depositRequired,
  'depositCollected': depositCollected,
  'depositDue': depositDue,
  'appliedToInvoice': 0,
  'assessedFee': 0, 'feePaid': 0, 'feeDue': 0,
  'refundable': 0, 'refunded': 0,
  'closed': closed,
  'entries': [],
  'depositPayment': depositPayment,
  'reviewReasons': reviewReasons,
};

JsonMap invoiceData({
  num amountDue = 50000,
  String paymentStatus = 'PENDING',
  String paymentMethod = 'BANK',
  Map<String, dynamic>? paymentInstruction,
}) => {
  'orderId': 99,
  'orderNumber': 'ORD-99',
  'amountDue': amountDue,
  'paymentStatus': paymentStatus,
  'paymentMethod': paymentMethod,
  'paymentInstruction': paymentInstruction,
};

JsonMap appointmentData({
  int id = 42,
  int userId = 1,
  String status = 'PENDING',
}) => {
  'id': id,
  'userId': userId,
  'status': status,
  'appointmentDate': '2026-12-01',
  'startTime': '10:00',
  'items': [{'id': 1, 'serviceName': 'DV1', 'executionStatus': 'PLANNED'}],
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    FlutterSecureStorage.setMockInitialValues({});
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });
  setUp(() async => StorageService.clearAuth());

  // ==================== Provider unit tests ====================

  group('TreatmentProvider finance/deposit/invoice', () {
    test('finance() calls GET finance endpoint and parses', () async {
      final calls = <String>[];
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          calls.add('$method $path');
          return ok(financeData());
        }),
      );
      provider.bindUser(customer(1), authenticated: true);
      final result = await provider.finance(42);
      expect(calls, ['GET /api/v1/spa/appointments/42/finance']);
      expect(result['depositRequired'], 100000);
      provider.dispose();
    });

    test('createDepositPayment() calls POST deposit-payment', () async {
      final calls = <String>[];
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          calls.add('$method $path');
          return ok(financeData(
            depositPayment: {
              'transferCode': 'SPADEP-abc',
              'canPay': true,
              'paymentInstruction': {
                'bankName': 'ACB',
                'bankAccountNumber': '123456',
                'transferSyntax': 'SPADEP-abc',
                'qrCodeUrl': 'https://qr.example/test',
              },
            },
          ));
        }),
      );
      provider.bindUser(customer(1), authenticated: true);
      final result = await provider.createDepositPayment(42);
      expect(calls, ['POST /api/v1/spa/appointments/42/deposit-payment']);
      expect(result['depositPayment'], isA<Map>());
      provider.dispose();
    });

    test('createDepositPayment() throws on empty response', () async {
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) => ok({})),
      );
      provider.bindUser(customer(1), authenticated: true);
      expect(
        () => provider.createDepositPayment(42),
        throwsA(isA<TreatmentFailure>()),
      );
      provider.dispose();
    });

    test('invoice() returns null on 404', () async {
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('/invoice')) throw ApiException(statusCode: 404, message: 'Not found');
          return ok({});
        }),
      );
      provider.bindUser(customer(1), authenticated: true);
      final result = await provider.invoice(42);
      expect(result, isNull);
      provider.dispose();
    });

    test('invoice() returns data on success', () async {
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          return ok(invoiceData());
        }),
      );
      provider.bindUser(customer(1), authenticated: true);
      final result = await provider.invoice(42);
      expect(result, isNotNull);
      expect(result!['amountDue'], 50000);
      provider.dispose();
    });

    test('generation guard prevents stale finance', () async {
      final delayed = Completer<JsonMap>();
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) => delayed.future),
      );
      provider.bindUser(customer(1), authenticated: true);
      final pending = provider.finance(42);
      provider.bindUser(customer(2), authenticated: true); // generation change
      delayed.complete(ok(financeData()));
      expect(() => pending, throwsA(isA<TreatmentFailure>()));
      provider.dispose();
    });
  });

  group('SpaProvider.bookAppointment returns ID', () {
    test('lastBookedAppointmentId set on success with id in response', () async {
      final provider = SpaProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('book')) return ok({'id': 77});
          if (path.contains('appointments')) return ok([]);
          if (path.contains('services')) return ok([]);
          return ok([]);
        }),
      );
      provider.bindUser(1);
      final success = await provider.bookAppointment(
        serviceId: 1,
        serviceName: 'test',
        date: DateTime(2027, 1, 1),
        timeSlot: '10:00',
      );
      expect(success, isTrue);
      expect(provider.lastBookedAppointmentId, 77);
      provider.dispose();
    });

    test('lastBookedAppointmentId null when response has no id', () async {
      final provider = SpaProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('book')) return ok({});
          if (path.contains('appointments')) return ok([]);
          if (path.contains('services')) return ok([]);
          return ok([]);
        }),
      );
      provider.bindUser(1);
      await provider.bookAppointment(
        serviceId: 1,
        serviceName: 'test',
        date: DateTime(2027, 1, 1),
        timeSlot: '10:00',
      );
      expect(provider.lastBookedAppointmentId, isNull);
      provider.dispose();
    });

    test('lastBookedAppointmentId cleared on logout', () async {
      final provider = SpaProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('book')) return ok({'id': 77});
          return ok([]);
        }),
      );
      provider.bindUser(1);
      await provider.bookAppointment(
        serviceId: 1,
        serviceName: 'test',
        date: DateTime(2027, 1, 1),
        timeSlot: '10:00',
      );
      expect(provider.lastBookedAppointmentId, 77);
      provider.bindUser(null); // logout
      expect(provider.lastBookedAppointmentId, isNull);
      provider.dispose();
    });
  });

  // ==================== Widget tests ====================

  group('TreatmentAppointmentScreen payment section visibility', () {
    Widget buildScreen({
      required _Auth auth,
      required TreatmentProvider provider,
      int id = 42,
      bool operate = false,
    }) =>
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>.value(value: auth),
            ChangeNotifierProxyProvider<AuthProvider, TreatmentProvider>(
              create: (_) => provider,
              update: (_, auth, value) => value!
                ..bindUser(auth.currentUser, authenticated: auth.isAuthenticated),
            ),
          ],
          child: MaterialApp(
            home: TreatmentAppointmentScreen(id: id, operate: operate),
          ),
        );

    testWidgets('owner sees payment section', (tester) async {
      final auth = _Auth(customer(1));
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('/finance')) return ok(financeData());
          if (path == '/api/v1/appointments/42') return ok(appointmentData(userId: 1));
          if (path.contains('/preparation')) return ok([]);
          if (path.contains('/follow-up')) return ok([]);
          return ok({});
        }),
      );
      await tester.pumpWidget(buildScreen(auth: auth, provider: provider));
      await tester.pumpAndSettle();
      expect(find.text('Cọc & phí lịch hẹn'), findsOneWidget);
      expect(find.text('Kiểm tra thanh toán'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    });

    testWidgets('technician does not see payment section', (tester) async {
      final auth = _Auth(technician(2));
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path == '/api/v1/appointments/42') return ok(appointmentData(userId: 1, status: 'IN_PROGRESS'));
          if (path.contains('/preparation')) return ok([]);
          if (path.contains('/follow-up')) return ok([]);
          if (path.contains('/care-summary')) return ok({});
          if (path.contains('/staff')) return ok([{'id': 5, 'userId': 2, 'fullName': 'T 2'}]);
          return ok({});
        }),
      );
      await tester.pumpWidget(buildScreen(auth: auth, provider: provider, operate: true));
      await tester.pumpAndSettle();
      expect(find.text('Cọc & phí lịch hẹn'), findsNothing);
      expect(find.text('Kiểm tra thanh toán'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    });

    testWidgets('deposit paid hides QR button', (tester) async {
      final auth = _Auth(customer(1));
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('/finance')) return ok(financeData(
            depositCollected: 100000,
            depositDue: 0,
          ));
          if (path == '/api/v1/appointments/42') return ok(appointmentData(userId: 1));
          if (path.contains('/preparation')) return ok([]);
          if (path.contains('/follow-up')) return ok([]);
          return ok({});
        }),
      );
      await tester.pumpWidget(buildScreen(auth: auth, provider: provider));
      await tester.pumpAndSettle();
      expect(find.text('Đã đủ cọc theo hệ thống.'), findsOneWidget);
      expect(find.text('Tạo QR trả cọc'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    });

    testWidgets('completed status shows invoice section', (tester) async {
      final auth = _Auth(customer(1));
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('/finance')) return ok(financeData(
            depositCollected: 100000, depositDue: 0, closed: true,
          ));
          if (path.contains('/invoice')) return ok(invoiceData());
          if (path == '/api/v1/appointments/42') return ok(appointmentData(userId: 1, status: 'COMPLETED'));
          if (path.contains('/preparation')) return ok([]);
          if (path.contains('/follow-up')) return ok([]);
          return ok({});
        }),
      );
      await tester.pumpWidget(buildScreen(auth: auth, provider: provider));
      await tester.pumpAndSettle();
      expect(find.text('Hóa đơn buổi dịch vụ'), findsOneWidget);
      expect(find.text('Còn phải trả: 50000 ₫'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    });

    testWidgets('completed without invoice shows waiting message', (tester) async {
      final auth = _Auth(customer(1));
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('/finance')) return ok(financeData(closed: true));
          if (path.contains('/invoice')) throw ApiException(statusCode: 404, message: 'Not Found');
          if (path == '/api/v1/appointments/42') return ok(appointmentData(userId: 1, status: 'COMPLETED'));
          if (path.contains('/preparation')) return ok([]);
          if (path.contains('/follow-up')) return ok([]);
          return ok({});
        }),
      );
      await tester.pumpWidget(buildScreen(auth: auth, provider: provider));
      await tester.pumpAndSettle();
      expect(find.text('Chờ nhân viên lập hóa đơn'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    });

    testWidgets('reception sees payment section for others appointment', (tester) async {
      final auth = _Auth(reception(5));
      final provider = TreatmentProvider(
        apiClient: _ApiStub((method, path, body, query) {
          if (path.contains('/finance')) return ok(financeData());
          if (path == '/api/v1/appointments/42') return ok(appointmentData(userId: 1));
          if (path.contains('/preparation')) return ok([]);
          if (path.contains('/follow-up')) return ok([]);
          return ok({});
        }),
      );
      await tester.pumpWidget(buildScreen(auth: auth, provider: provider, operate: true));
      await tester.pumpAndSettle();
      expect(find.text('Cọc & phí lịch hẹn'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    });
  });
}
