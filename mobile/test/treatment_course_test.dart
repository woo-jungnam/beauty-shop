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
import 'package:beautyshop_mobile/data/models/spa_model.dart';
import 'package:beautyshop_mobile/data/models/treatment_model.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'package:beautyshop_mobile/providers/treatment_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/treatments/treatment_hub_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/treatments/treatment_enrollment_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/treatments/treatment_appointment_screen.dart';

typedef Handler =
    FutureOr<JsonMap> Function(
      String method,
      String path,
      dynamic data,
      JsonMap? query,
    );

class TreatmentApiStub implements ApiClient {
  TreatmentApiStub(this.handle);
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

class TestTreatmentAuth extends AuthProvider {
  TestTreatmentAuth(this.user);
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

JsonMap success(dynamic value) => {'status': 200, 'data': value};
User account(
  int id,
  List<String> roles, {
  List<String> permissions = const [],
}) => User(
  id: id,
  username: 'user$id',
  fullName: 'User $id',
  roles: roles,
  permissions: permissions,
);
JsonMap plan() => {
  'name': 'Chăm sóc theo lộ trình',
  'description': 'Kế hoạch 2 buổi',
  'serviceId': 10,
  'price': 900000,
  'validityDays': 90,
  'terms': 'Đổi lịch trước 24 giờ.',
  'sessions': [
    {
      'sequence': 1,
      'title': 'Tiếp nhận',
      'minGapDays': 0,
      'required': true,
      'items': [
        {'serviceId': 11, 'serviceName': 'Chăm sóc da', 'required': true},
      ],
    },
    {
      'sequence': 2,
      'title': 'Đánh giá',
      'minGapDays': 7,
      'required': true,
      'items': [
        {'serviceId': 11, 'serviceName': 'Chăm sóc da', 'required': true},
      ],
    },
  ],
};
JsonMap template() => {
  'id': 1,
  'versionId': 12,
  'versionNumber': 2,
  'status': 'PUBLISHED',
  ...plan(),
  'allowedActions': ['PURCHASE'],
};
JsonMap course({List<String> actions = const []}) => {
  'id': 3,
  'userId': 1,
  'templateId': 1,
  'versionId': 12,
  'version': 1,
  'name': 'Lộ trình cá nhân',
  'status': 'PENDING_INTAKE',
  'orderId': 40,
  'totalSessions': 2,
  'completedSessions': 0,
  'progressPercent': 0,
  'expiryDate': '2027-01-01T00:00:00Z',
  'currentPlan': plan(),
  'sessions': [],
  'changes': [],
  'requests': [],
  'audit': [],
  'allowedActions': actions,
};

Widget app(Widget screen, TreatmentProvider provider, TestTreatmentAuth auth) =>
    MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthProvider>.value(value: auth),
        ChangeNotifierProxyProvider<AuthProvider, TreatmentProvider>(
          create: (_) => provider,
          update: (_, auth, value) => value!
            ..bindUser(auth.currentUser, authenticated: auth.isAuthenticated),
        ),
      ],
      child: MaterialApp(home: screen),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    FlutterSecureStorage.setMockInitialValues({});
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });
  setUp(() async => StorageService.clearAuth());

  test(
    'All roles including object authorities survive saved profile round-trip',
    () {
      final user = User.fromJson({
        'id': 1,
        'roles': [
          'ROLE_USER',
          {'name': 'ROLE_STAFF'},
        ],
        'authorities': [
          {'authority': 'ROLE_ADMIN'},
        ],
      });
      final restored = User.fromJson(user.toJson());
      expect(restored.normalizedRoles, {'USER', 'STAFF', 'ADMIN'});
      expect(restored.isAdmin, isTrue);
      expect(restored.isStaff, isTrue);
      expect(
        User.fromJson({
          'roles': ['ROLE_CUSTOMER'],
        }).normalizedRoles,
        {'USER'},
      );
    },
  );

  test(
    'Spa operation requires an explicit grant while the staff role remains available to other modules',
    () {
      final catalog = User.fromJson({
        'id': 1,
        'roles': ['ROLE_STAFF'],
        'permissions': ['CATALOG'],
      });
      expect(catalog.isStaff, isTrue);
      expect(catalog.canOperateSpa, isFalse);
      expect(User.fromJson(catalog.toJson()).permissions, ['CATALOG']);
      for (final permission in ['SPA_RECEPTION', 'SPA_EXECUTION']) {
        final spa = User.fromJson({
          'id': 1,
          'roles': ['ROLE_STAFF'],
          'permissions': [permission],
        });
        expect(spa.canOperateSpa, isTrue);
        expect(User.fromJson(spa.toJson()).canOperateSpa, isTrue);
      }
      expect(
        User.fromJson({
          'roles': ['ROLE_STAFF'],
        }).canOperateSpa,
        isFalse,
      );
      expect(
        User.fromJson({
          'roles': ['ROLE_CUSTOMER'],
          'permissions': ['SPA_EXECUTION'],
        }).canOperateSpa,
        isFalse,
      );
      expect(
        User.fromJson({
          'roles': ['ROLE_ADMIN'],
        }).canOperateSpa,
        isTrue,
      );
    },
  );

  test(
    'A grant-only change invalidates pending treatment data and closes the staff scope',
    () async {
      final delayed = Completer<JsonMap>();
      var calls = 0;
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          calls++;
          return delayed.future;
        }),
      );
      provider.bindUser(
        account(7, ['ROLE_STAFF'], permissions: ['CATALOG']),
        authenticated: true,
      );
      expect(provider.isStaff, isFalse);
      expect(
        () => provider.enrollments(TreatmentArea.staff),
        throwsA(isA<TreatmentFailure>()),
      );
      expect(calls, 0);
      provider.bindUser(
        account(7, ['ROLE_STAFF'], permissions: ['SPA_EXECUTION']),
        authenticated: true,
      );
      expect(provider.isStaff, isTrue);
      final generation = provider.generation;
      final pending = provider.enrollments(TreatmentArea.staff);
      provider.bindUser(
        account(7, ['ROLE_STAFF'], permissions: ['CATALOG']),
        authenticated: true,
      );
      expect(provider.generation, greaterThan(generation));
      expect(provider.canUse(TreatmentArea.staff), isFalse);
      final rejected = expectLater(pending, throwsA(isA<TreatmentFailure>()));
      delayed.complete(success([]));
      await rejected;
      provider.dispose();
    },
  );

  testWidgets(
    'Catalog-only staff sees no Spa operator switch until an explicit Spa grant is received',
    (tester) async {
      final auth = TestTreatmentAuth(
        account(7, ['ROLE_STAFF'], permissions: ['CATALOG']),
      );
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) => success([])),
      );
      await tester.pumpWidget(app(const TreatmentHubScreen(), provider, auth));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Chuyển khu vực'));
      await tester.pumpAndSettle();
      expect(find.text('Nhân viên'), findsNothing);
      await tester.tap(find.text('Khách hàng'));
      await tester.pumpAndSettle();
      auth.change(account(7, ['ROLE_STAFF'], permissions: ['SPA_RECEPTION']));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Chuyển khu vực'));
      await tester.pumpAndSettle();
      expect(find.text('Nhân viên'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );

  test(
    'Single services remain default and appointments preserve every linked item',
    () {
      expect(SpaService.fromJson({'id': 1}).isTreatmentCourse, isFalse);
      expect(
        SpaService.fromJson({
          'id': 2,
          'serviceType': 'TREATMENT_COURSE',
        }).isTreatmentCourse,
        isTrue,
      );
      final appointment = Appointment.fromJson({
        'id': 50,
        'items': [
          {
            'id': 101,
            'serviceId': 11,
            'serviceName': 'A',
            'treatmentSessionItemId': 501,
            'executionStatus': 'PERFORMED',
          },
          {
            'id': 102,
            'serviceId': 12,
            'serviceName': 'B',
            'treatmentSessionItemId': 502,
            'executionStatus': 'PLANNED',
          },
        ],
      });
      expect(appointment.items, hasLength(2));
      expect(appointment.serviceName, 'A, B');
      expect(appointment.items.last.treatmentSessionItemId, 502);
    },
  );

  test(
    'No-show does not infer clinical completion or conceal server booking rules',
    () {
      final session = TreatmentSession.fromJson({
        'id': 1,
        'status': 'PLANNED',
        'allowedActions': ['BOOK'],
        'items': [
          {
            'id': 10,
            'status': 'COMPLETED',
            'serviceId': 11,
            'appointmentId': 99,
            'appointmentItemId': 1,
          },
          {'id': 11, 'status': 'NO_SHOW', 'serviceId': 12},
        ],
      });
      expect(session.status, 'PLANNED');
      expect(session.outstandingItems.map((item) => item.id), [11]);
      expect(session.appointmentIds, [99]);
      expect(session.allows('BOOK'), isTrue);
    },
  );

  test(
    'Delayed response from previous account is rejected after switching account',
    () async {
      final pending = Completer<JsonMap>();
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub(
          (method, path, body, query) => pending.future,
        ),
      );
      provider.bindUser(account(1, ['ROLE_USER']), authenticated: true);
      final request = provider.enrollment(3, TreatmentArea.customer);
      provider.bindUser(account(2, ['ROLE_USER']), authenticated: true);
      pending.complete(success(course()));
      await expectLater(request, throwsA(isA<TreatmentFailure>()));
      expect(provider.userId, 2);
      provider.dispose();
    },
  );

  test(
    'Customer cannot call operational list even when primary role differs from roles',
    () async {
      var calls = 0;
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          calls++;
          return success([]);
        }),
      );
      provider.bindUser(account(1, ['ROLE_USER']), authenticated: true);
      expect(
        () => provider.enrollments(TreatmentArea.admin),
        throwsA(isA<TreatmentFailure>()),
      );
      expect(calls, 0);
      provider.bindUser(
        account(1, ['ROLE_USER', 'ROLE_STAFF'], permissions: ['SPA_EXECUTION']),
        authenticated: true,
      );
      await provider.enrollments(TreatmentArea.staff);
      expect(calls, 1);
      provider.dispose();
    },
  );

  test(
    'Token refresh expiry clears treatment scope even outside the auth screen',
    () async {
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) => success([])),
      );
      provider.bindUser(
        account(1, ['ROLE_USER', 'ROLE_ADMIN']),
        authenticated: true,
      );
      final generation = provider.generation;
      await StorageService.clearAuth();
      expect(provider.authenticated, isFalse);
      expect(provider.userId, isNull);
      expect(provider.isAdmin, isFalse);
      expect(provider.generation, greaterThan(generation));
      provider.dispose();
    },
  );

  test(
    'Purchase retries share a key, acknowledged success starts a new purchase intent',
    () async {
      final keys = <String>[];
      var fail = true;
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          expect(path, '/api/v1/spa/treatment-purchases');
          expect(body['acceptedTerms'], true);
          expect(body['versionId'], 12);
          keys.add(body['idempotencyKey'] as String);
          if (fail) {
            fail = false;
            throw ApiException(statusCode: 500, message: 'Connection lost');
          }
          return success({
            'orderId': 30,
            'orderNumber': 'ORDER30',
            'paymentStatus': 'PENDING',
            'totalAmount': 900000,
          });
        }),
      );
      provider.bindUser(account(1, ['ROLE_USER']), authenticated: true);
      await expectLater(
        provider.purchase(TreatmentTemplate.fromJson(template())),
        throwsA(isA<TreatmentFailure>()),
      );
      final order = await provider.purchase(
        TreatmentTemplate.fromJson(template()),
      );
      expect(order.id, 30);
      expect(order.isPaid, isFalse);
      expect(keys[0], keys[1]);
      await provider.purchase(TreatmentTemplate.fromJson(template()));
      expect(keys[2], isNot(keys[1]));
      provider.dispose();
    },
  );

  test(
    'Reschedule availability sends original appointment id and server errors remain failures',
    () async {
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          if (method == 'GET') {
            expect(path, '/api/v1/spa/treatments/3/sessions/8/available-slots');
            expect(query, {'date': '2026-10-20', 'appointmentId': 99});
            return success(['09:00:00']);
          }
          return {'status': 409, 'message': 'Kế hoạch đã thay đổi'};
        }),
      );
      provider.bindUser(account(1, ['ROLE_USER']), authenticated: true);
      expect(await provider.slots(3, 8, '2026-10-20', appointmentId: 99), [
        '09:00:00',
      ]);
      await expectLater(
        provider.write('/api/v1/spa/treatments/3/changes/4/accept', {
          'expectedVersion': 1,
        }),
        throwsA(isA<TreatmentFailure>()),
      );
      provider.dispose();
    },
  );

  testWidgets(
    'Customer treatment catalog exposes purchase details and three customer tabs',
    (tester) async {
      final auth = TestTreatmentAuth(account(1, ['ROLE_USER']));
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub(
          (method, path, body, query) =>
              success(path.endsWith('treatment-templates') ? [template()] : []),
        ),
      );
      await tester.pumpWidget(app(const TreatmentHubScreen(), provider, auth));
      await tester.pumpAndSettle();
      expect(find.text('Khám phá'), findsOneWidget);
      expect(find.text('Của tôi'), findsOneWidget);
      expect(find.text('Thông báo'), findsOneWidget);
      expect(find.text('Chăm sóc theo lộ trình'), findsOneWidget);
      expect(find.text('Quản trị Spa'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );

  testWidgets(
    'Staff sees intake but cannot approve admin requests; switching account removes clinical detail',
    (tester) async {
      final auth = TestTreatmentAuth(
        account(5, ['ROLE_USER', 'ROLE_STAFF'], permissions: ['SPA_EXECUTION']),
      );
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          if (path.endsWith('/staff')) {
            return success([
              {'id': 23, 'userId': 5},
            ]);
          }
          return success({
            ...course(actions: ['INTAKE', 'PROPOSE_CHANGE']),
            'assignedStaffId': 23,
            'intakeNotes': 'PRIVATE_CLINICAL_NOTE',
            'requests': [
              {
                'id': 8,
                'type': 'EXTEND',
                'status': 'PENDING_APPROVAL',
                'reason': 'Xin gia hạn',
              },
            ],
          });
        }),
      );
      await tester.pumpWidget(
        app(
          const TreatmentEnrollmentScreen(id: 3, area: TreatmentArea.staff),
          provider,
          auth,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Tiếp nhận'), findsOneWidget);
      expect(find.text('PRIVATE_CLINICAL_NOTE'), findsOneWidget);
      expect(find.text('Duyệt'), findsNothing);
      auth.change(null);
      await tester.pumpAndSettle();
      expect(find.text('PRIVATE_CLINICAL_NOTE'), findsNothing);
      expect(
        find.textContaining('Phiên đăng nhập đã thay đổi'),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );

  testWidgets(
    'Reception keeps scheduling controls without requesting or exposing clinical data',
    (tester) async {
      final auth = TestTreatmentAuth(
        account(5, ['ROLE_STAFF'], permissions: ['SPA_RECEPTION']),
      );
      final reads = <String>[];
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          reads.add(path);
          if (path == '/api/v1/appointments/99')
            return success({
              'id': 99,
              'userId': 1,
              'status': 'CONFIRMED',
              'appointmentDate': '2026-10-20',
              'startTime': '09:00:00',
              'items': [
                {
                  'id': 100,
                  'staffId': 23,
                  'serviceName': 'Chăm sóc',
                  'executionStatus': 'PLANNED',
                  'executionNotes': 'PRIVATE_EXECUTION_NOTE',
                },
              ],
            });
          if (path.endsWith('/preparation') || path.endsWith('/care-summary'))
            return {'status': 403, 'message': 'Chưa được phân công'};
          return success([]);
        }),
      );
      await tester.pumpWidget(
        app(
          const TreatmentAppointmentScreen(
            id: 99,
            courseId: 3,
            sessionId: 8,
            operate: true,
          ),
          provider,
          auth,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Xác nhận / Phân công'), findsOneWidget);
      expect(find.text('Check-in'), findsOneWidget);
      expect(find.text('Khách vắng hẹn'), findsOneWidget);
      expect(find.text('Đổi ngày giờ'), findsOneWidget);
      expect(find.text('Bắt đầu dịch vụ'), findsNothing);
      expect(find.text('PRIVATE_EXECUTION_NOTE'), findsNothing);
      expect(find.text('Cảnh báo và hồ sơ chăm sóc nội bộ'), findsNothing);
      expect(
        reads.where(
          (path) =>
              path.contains('/preparation') ||
              path.contains('/care-summary') ||
              path.contains('/follow-up-instructions'),
        ),
        isEmpty,
      );
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );

  testWidgets(
    'Customer explicitly submits required consent with an actual false answer',
    (tester) async {
      final auth = TestTreatmentAuth(account(1, ['ROLE_USER']));
      JsonMap? submitted;
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          if (method == 'POST') {
            expect(
              path,
              '/api/v1/spa/appointments/99/preparation/forms/88/responses',
            );
            submitted = treatmentMap(body);
            return success({'id': 1});
          }
          if (path == '/api/v1/appointments/99')
            return success({
              'id': 99,
              'userId': 1,
              'status': 'PENDING',
              'appointmentDate': '2026-10-20',
              'startTime': '09:00:00',
              'items': [],
            });
          if (path.endsWith('/preparation'))
            return success([
              {
                'appointmentItemId': 100,
                'forms': [
                  {
                    'id': 88,
                    'form': {
                      'title': 'Thông tin trước buổi',
                      'version': 2,
                      'questions': [
                        {
                          'key': 'goal',
                          'label': 'Mục tiêu chăm sóc?',
                          'type': 'TEXT',
                          'required': true,
                        },
                        {
                          'key': 'allergy',
                          'label': 'Có dị ứng?',
                          'type': 'BOOLEAN',
                          'required': true,
                        },
                      ],
                    },
                  },
                ],
              },
            ]);
          return success([]);
        }),
      );
      await tester.pumpWidget(
        app(
          const TreatmentAppointmentScreen(
            id: 99,
            courseId: 3,
            sessionId: 8,
            operate: false,
          ),
          provider,
          auth,
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Điền và xác nhận biểu mẫu'));
      await tester.tap(find.text('Điền và xác nhận biểu mẫu'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Gửi xác nhận'),
            )
            .onPressed,
        isNull,
      );
      await tester.enterText(find.byType(TextFormField), 'Dưỡng ẩm');
      await tester.tap(find.byType(DropdownButtonFormField<bool>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Không').last);
      await tester.pumpAndSettle();
      await tester.tap(find.byType(CheckboxListTile));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Gửi xác nhận'));
      await tester.pumpAndSettle();
      expect(submitted, {
        'acknowledged': true,
        'answers': {'goal': 'Dưỡng ẩm', 'allergy': false},
      });
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );

  testWidgets(
    'Execution grant uses linked user assignment and never exposes reception controls',
    (tester) async {
      tester.view.physicalSize = const Size(900, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });
      final auth = TestTreatmentAuth(
        account(5, ['ROLE_STAFF'], permissions: ['SPA_EXECUTION']),
      );
      var status = 'CONFIRMED';
      final reads = <String>[];
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          reads.add(path);
          if (path.endsWith('/staff')) {
            return success([
              {'id': 23, 'userId': 5},
              {'id': 5, 'userId': 77},
            ]);
          }
          if (path == '/api/v1/appointments/99') {
            return success({
              'id': 99,
              'userId': 1,
              'status': status,
              'appointmentDate': '2026-10-20',
              'startTime': '09:00:00',
              'items': [
                {
                  'id': 100,
                  'staffId': 23,
                  'serviceName': 'Assigned service',
                  'executionStatus': 'PLANNED',
                },
                {
                  'id': 101,
                  'staffId': 5,
                  'serviceName': 'Other service',
                  'executionStatus': 'PLANNED',
                },
              ],
            });
          }
          if (path.endsWith('/preparation')) {
            return success([
              {
                'appointmentItemId': 100,
                'forms': [
                  {
                    'form': {'title': 'OWN_PREPARATION', 'version': 1},
                  },
                ],
              },
              {
                'appointmentItemId': 101,
                'forms': [
                  {
                    'form': {'title': 'OTHER_PREPARATION', 'version': 1},
                  },
                ],
              },
            ]);
          }
          if (path.endsWith('/care-summary')) {
            return success({
              'notes': [
                {'content': 'ASSIGNED_CARE_NOTE'},
              ],
            });
          }
          return success([]);
        }),
      );
      await tester.pumpWidget(
        app(
          const TreatmentAppointmentScreen(id: 99, operate: true),
          provider,
          auth,
        ),
      );
      await tester.pumpAndSettle();
      for (final label in [
        'Xác nhận / Phân công',
        'Check-in',
        'Khách vắng hẹn',
        'Đổi ngày giờ',
        'Hủy lịch',
      ]) {
        expect(find.text(label), findsNothing, reason: label);
      }
      expect(find.textContaining('OWN_PREPARATION'), findsOneWidget);
      expect(find.textContaining('OTHER_PREPARATION'), findsNothing);
      expect(find.text('ASSIGNED_CARE_NOTE'), findsOneWidget);
      expect(find.text('Cọc & phí lịch hẹn'), findsNothing);
      expect(find.text('Bắt đầu buổi'), findsNothing);
      status = 'IN_PROGRESS';
      await tester.tap(find.byTooltip('Cập nhật'));
      await tester.pumpAndSettle();
      expect(find.text('Bắt đầu dịch vụ'), findsOneWidget);
      expect(
        reads.where((path) => path.endsWith('/care-summary')),
        hasLength(2),
      );
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );

  for (final directoryFails in [false, true]) {
    testWidgets(
      directoryFails
          ? 'Unavailable staff directory fails closed without fetching clinical data'
          : 'Unassigned executor cannot use Staff id equal to own User id as clinical assignment',
      (tester) async {
        final auth = TestTreatmentAuth(
          account(5, ['ROLE_STAFF'], permissions: ['SPA_EXECUTION']),
        );
        final reads = <String>[];
        final provider = TreatmentProvider(
          apiClient: TreatmentApiStub((method, path, body, query) {
            reads.add(path);
            if (path.endsWith('/staff')) {
              if (directoryFails) {
                return {'status': 503, 'message': 'Directory unavailable'};
              }
              return success([
                {'id': 5, 'userId': 77},
                {'id': 23, 'userId': 5},
              ]);
            }
            return success({
              'id': 99,
              'userId': 1,
              'status': 'IN_PROGRESS',
              'appointmentDate': '2026-10-20',
              'startTime': '09:00:00',
              'items': [
                {
                  'id': 100,
                  'staffId': 5,
                  'serviceName': 'Other service',
                  'executionStatus': 'IN_PROGRESS',
                  'executionNotes': 'PRIVATE_NOTE',
                },
              ],
            });
          }),
        );
        await tester.pumpWidget(
          app(
            const TreatmentAppointmentScreen(id: 99, operate: true),
            provider,
            auth,
          ),
        );
        await tester.pumpAndSettle();
        expect(reads, [
          '/api/v1/appointments/99',
          '/api/v1/spa/services/staff',
        ]);
        expect(find.text('Đã thực hiện'), findsNothing);
        expect(find.text('Kết thúc lịch hẹn'), findsNothing);
        expect(find.text('PRIVATE_NOTE'), findsNothing);
        expect(find.text('Cảnh báo và hồ sơ chăm sóc nội bộ'), findsNothing);
        if (directoryFails) {
          expect(
            find.textContaining('Chưa xác minh được phân công'),
            findsOneWidget,
          );
        }
        await tester.pumpWidget(const SizedBox());
        auth.dispose();
      },
    );
  }

  testWidgets(
    'Reception course view suppresses clinical fields and actions even in an excessive API payload',
    (tester) async {
      tester.view.physicalSize = const Size(900, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });
      final auth = TestTreatmentAuth(
        account(5, ['ROLE_STAFF'], permissions: ['SPA_RECEPTION']),
      );
      final reads = <String>[];
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          reads.add(path);
          return success({
            ...course(
              actions: ['ASSIGN', 'INTAKE', 'PROPOSE_CHANGE', 'COMPLETE'],
            ),
            'assignedStaffId': 23,
            'intakeNotes': 'PRIVATE_INTAKE',
            'summary': 'PRIVATE_SUMMARY',
            'audit': [
              {'action': 'PRIVATE_AUDIT'},
            ],
            'sessions': [
              {
                'id': 8,
                'sequence': 1,
                'title': 'Session',
                'status': 'COMPLETED',
                'result': 'PRIVATE_RESULT',
                'aftercare': 'PRIVATE_AFTERCARE',
                'internalNotes': 'PRIVATE_INTERNAL',
                'items': [],
                'allowedActions': ['WRITE_RESULT'],
                'results': [
                  {
                    'result': 'PRIVATE_HISTORY',
                    'internalNotes': 'PRIVATE_HISTORY_INTERNAL',
                  },
                ],
              },
            ],
          });
        }),
      );
      await tester.pumpWidget(
        app(
          const TreatmentEnrollmentScreen(id: 3, area: TreatmentArea.staff),
          provider,
          auth,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Phân công phụ trách'), findsOneWidget);
      expect(find.text('Tiếp nhận'), findsNothing);
      expect(find.text('Đề xuất điều chỉnh'), findsNothing);
      expect(find.text('Ghi kết quả / hướng dẫn'), findsNothing);
      expect(find.textContaining('PRIVATE_'), findsNothing);
      expect(reads, ['/api/v1/staff/spa/treatments/3']);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );

  testWidgets(
    'Interrupted treatment item requires reason and sends SKIPPED without claiming completion',
    (tester) async {
      final auth = TestTreatmentAuth(
        account(5, ['ROLE_STAFF'], permissions: ['SPA_EXECUTION']),
      );
      final writes = <JsonMap>[];
      var interrupted = false;
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          if (path.endsWith('/staff')) {
            return success([
              {'id': 23, 'userId': 5},
            ]);
          }
          if (method == 'PUT') {
            expect(path, '/api/v1/appointments/99/items/100/execution');
            writes.add(treatmentMap(body));
            interrupted = true;
            return success({});
          }
          if (path == '/api/v1/appointments/99')
            return success({
              'id': 99,
              'userId': 1,
              'status': 'IN_PROGRESS',
              'appointmentDate': '2026-10-20',
              'startTime': '09:00:00',
              'items': [
                {
                  'id': 100,
                  'staffId': 23,
                  'serviceName': 'Dịch vụ lộ trình',
                  'treatmentSessionItemId': 501,
                  'executionStatus': interrupted ? 'SKIPPED' : 'IN_PROGRESS',
                },
                {
                  'id': 101,
                  'staffId': 24,
                  'serviceName': 'Dịch vụ đơn lẻ',
                  'executionStatus': 'IN_PROGRESS',
                },
              ],
            });
          return success(
            path.endsWith('/care-summary') ? <String, dynamic>{} : [],
          );
        }),
      );
      await tester.pumpWidget(
        app(
          const TreatmentAppointmentScreen(
            id: 99,
            courseId: 3,
            sessionId: 8,
            operate: true,
          ),
          provider,
          auth,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Dừng do sự cố'), findsOneWidget);
      expect(find.text('Đã thực hiện'), findsOneWidget);
      await tester.ensureVisible(find.text('Dừng do sự cố'));
      await tester.tap(find.text('Dừng do sự cố'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Gửi'));
      await tester.pumpAndSettle();
      expect(writes, isEmpty);
      await tester.enterText(
        find.byType(TextFormField),
        'Khách phản ứng; dừng để đánh giá.',
      );
      await tester.tap(find.text('Gửi'));
      await tester.pumpAndSettle();
      expect(writes, [
        {'status': 'SKIPPED', 'notes': 'Khách phản ứng; dừng để đánh giá.'},
      ]);
      expect(find.textContaining('Đã bỏ qua'), findsOneWidget);
      expect(find.text('Dừng do sự cố'), findsNothing);
      expect(find.text('Hoàn thành'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );

  testWidgets(
    'Admin workspace authors a two-session draft using actual catalog services',
    (tester) async {
      tester.view.physicalSize = const Size(600, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });
      final auth = TestTreatmentAuth(account(5, ['ROLE_USER', 'ROLE_ADMIN']));
      JsonMap? saved;
      final provider = TreatmentProvider(
        apiClient: TreatmentApiStub((method, path, body, query) {
          if (method == 'POST') {
            saved = treatmentMap(body);
            return success({...template(), 'status': 'DRAFT'});
          }
          if (path == '/api/v1/spa/services')
            return success([
              {
                'id': 10,
                'name': 'Dịch vụ lộ trình',
                'serviceType': 'TREATMENT_COURSE',
              },
              {
                'id': 11,
                'name': 'Dịch vụ chăm sóc da',
                'serviceType': 'SINGLE_SESSION',
              },
            ]);
          if (path.endsWith('/report')) return success({});
          return success([]);
        }),
      );
      await tester.pumpWidget(
        app(
          const TreatmentHubScreen(initialArea: TreatmentArea.admin),
          provider,
          auth,
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mẫu lộ trình'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Tạo mẫu lộ trình'));
      await tester.pumpAndSettle();
      Finder field(String label) => find.ancestor(
        of: find.text(label),
        matching: find.byType(TextFormField),
      );
      await tester.enterText(field('Tên lộ trình'), 'Kế hoạch 2 buổi');
      await tester.tap(find.byType(DropdownButtonFormField<int>).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Dịch vụ lộ trình').last);
      await tester.pumpAndSettle();
      await tester.enterText(field('Giá toàn lộ trình (VND)'), '900000');
      await tester.enterText(
        field('Điều khoản đặt/đổi/hủy và phần chưa sử dụng'),
        'Đổi lịch trước 24 giờ.',
      );
      await tester.ensureVisible(field('Mục tiêu / nội dung buổi'));
      await tester.enterText(field('Mục tiêu / nội dung buổi'), 'Buổi đầu');
      await tester.ensureVisible(
        find.byType(DropdownButtonFormField<int>).last,
      );
      await tester.tap(find.byType(DropdownButtonFormField<int>).last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Dịch vụ chăm sóc da').last);
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Thêm buổi'));
      await tester.tap(find.text('Thêm buổi'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(field('Mục tiêu / nội dung buổi').last);
      await tester.enterText(
        field('Mục tiêu / nội dung buổi').last,
        'Buổi đánh giá',
      );
      await tester.ensureVisible(
        find.byType(DropdownButtonFormField<int>).last,
      );
      await tester.tap(find.byType(DropdownButtonFormField<int>).last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Dịch vụ chăm sóc da').last);
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Lưu bản nháp'));
      await tester.tap(find.text('Lưu bản nháp'));
      await tester.pumpAndSettle();
      expect(saved?['serviceId'], 10);
      expect(
        treatmentMaps(saved?['sessions']).map((session) => session['sequence']),
        [1, 2],
      );
      expect(treatmentMaps(saved?['sessions']).last['items'], [
        {'serviceId': 11, 'required': true},
      ]);
      await tester.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );
}
