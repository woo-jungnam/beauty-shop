import 'dart:convert';
import 'dart:io';

import 'package:beautyshop_shipper/core/constants/api_constants.dart';
import 'package:beautyshop_shipper/core/storage/storage_service.dart';
import 'package:beautyshop_shipper/data/models/shipper_order_model.dart';
import 'package:beautyshop_shipper/data/models/shipper_return_model.dart';
import 'package:beautyshop_shipper/presentation/screens/dashboard/shipper_dashboard_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/notification/notification_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/returns/return_pickup_confirmation_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/returns/shipper_return_tab.dart';
import 'package:beautyshop_shipper/providers/auth_provider.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttp extends HttpOverrides {}

class _FakeReturnAuthProvider extends AuthProvider {
  @override
  bool get isAuthenticated => true;

  @override
  String? get currentUsername => 'shipper1';

  @override
  String? get currentFullName => 'Tài xế Minh';
}

class _FakeReturnShipperProvider extends ShipperProvider {
  List<ShipperReturnModel> seededReturns = [];
  List<ShipperOrderModel> seededOrders = [];
  List<Map<String, dynamic>> seededNotifications = [];
  ShipperReturnModel? detailResult;
  final List<String> calls = [];

  @override
  List<ShipperReturnModel> get returnPickups => seededReturns;

  @override
  List<ShipperOrderModel> get orders => seededOrders;

  @override
  int get activeReturnPickupCount =>
      seededReturns.where((r) => r.isPickupAssigned || r.isPickedUp).length;

  @override
  Future<void> refreshUnreadCount() async {}

  @override
  Future<void> fetchOrders({int? page, bool background = false}) async {}

  @override
  Future<void> fetchReturnPickups({int? page, bool background = false}) async {
    calls.add('fetchReturnPickups');
    notifyListeners();
  }

  @override
  Future<ShipperReturnModel> fetchReturnDetail(int returnId) async {
    calls.add('fetchReturnDetail:$returnId');
    return detailResult ??
        seededReturns.firstWhere((item) => item.id == returnId);
  }

  @override
  Future<void> startReturnPickup(int returnId) async {
    calls.add('startReturnPickup:$returnId');
  }

  @override
  Future<void> reportReturnPickupFailure(int returnId, String reason) async {
    calls.add('reportReturnPickupFailure:$returnId:$reason');
  }

  @override
  Future<void> handoverReturnToWarehouse(int returnId, {String? notes}) async {
    calls.add('handoverReturnToWarehouse:$returnId:${notes ?? ''}');
  }

  @override
  Future<Map<String, dynamic>> fetchNotifications(int page) async {
    return {'content': seededNotifications, 'last': true};
  }

  @override
  Future<void> markRead(int id) async {
    calls.add('markRead:$id');
  }
}

ShipperReturnModel _sampleReturn({
  required int id,
  String status = 'PICKUP_ASSIGNED',
  DateTime? pickupStartedAt,
  DateTime? pickedUpAt,
  DateTime? handoverRequestedAt,
  String? pickupProofUrl,
}) {
  return ShipperReturnModel(
    id: id,
    orderId: 500 + id,
    orderNumber: 'ORD-$id',
    status: status,
    returnMethod: 'SHIPPER_PICKUP',
    reason: 'Sản phẩm bị nứt vòi bơm',
    decisionReason: 'Đã duyệt thu hồi tận nơi',
    refundAmount: 245000,
    customerName: 'Trần Minh Anh',
    customerPhone: '0901234567',
    pickupAddress: '28 Lý Tự Trọng, Quận 1, TP.HCM',
    paymentMethod: 'COD',
    pickupShipperId: 9,
    pickupShipperName: 'Nguyễn Văn Giao',
    pickupShipperPhone: '0988777666',
    pickupAssignedAt: DateTime.utc(2026, 11, 25, 8, 0),
    pickupStartedAt: pickupStartedAt,
    pickedUpAt: pickedUpAt,
    handoverRequestedAt: handoverRequestedAt,
    pickupProofUrl: pickupProofUrl,
    itemsSummary: const ['Serum B5 La Roche-Posay × 1'],
    items: const [
      ShipperReturnItem(
        orderItemId: 12,
        sku: 'LRP-B5',
        productName: 'Serum B5 La Roche-Posay',
        variantName: '30ml',
        returnQuantity: 1,
        purchasedQuantity: 2,
        unitPrice: 245000,
      ),
    ],
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  HttpOverrides.global = _RealHttp();

  setUpAll(() async {
    GoogleFonts.config.allowRuntimeFetching = false;
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    await StorageService.init();
  });

  test('ShipperReturnModel parses PICKUP_ASSIGNED and PICKED_UP fields cleanly', () {
    ApiConstants.setBaseUrl('https://api.example.test');
    final model = ShipperReturnModel.fromJson({
      'id': 42,
      'orderId': 77,
      'orderNumber': 'ORD-77',
      'status': 'PICKED_UP',
      'returnMethod': 'SHIPPER_PICKUP',
      'reason': 'Vòi nhấn hỏng',
      'refundAmount': 320000,
      'customerName': 'Lê Thảo',
      'customerPhone': '0912345678',
      'pickupAddress': '15 Hai Bà Trưng',
      'pickupProofUrl': '/uploads/pickup-42.jpg',
      'pickupAssignedAt': '2026-11-25T08:00:00Z',
      'pickupStartedAt': '2026-11-25T08:15:00Z',
      'pickedUpAt': '2026-11-25T08:40:00Z',
      'handoverRequestedAt': '2026-11-25T10:00:00Z',
      'itemsSummary': ['Kem dưỡng ẩm × 1'],
      'items': [
        {
          'orderItemId': 5,
          'sku': 'MOIST-50',
          'productName': 'Kem dưỡng ẩm',
          'variantName': '50ml',
          'imageUrl': '/uploads/item-5.jpg',
          'returnQuantity': 1,
          'purchasedQuantity': 1,
          'unitPrice': 320000,
        },
      ],
    });

    expect(model.id, 42);
    expect(model.isPickedUp, isTrue);
    expect(model.isStarted, isTrue);
    expect(model.isHandoverRequested, isTrue);
    expect(model.pickupProofUrl, 'https://api.example.test/uploads/pickup-42.jpg');
    expect(model.items.single.imageUrl, 'https://api.example.test/uploads/item-5.jpg');
  });

  test(
    'ShipperProvider executes return pickup, failure, photo upload, and handover endpoints',
    () async {
      SharedPreferences.setMockInitialValues({});
      FlutterSecureStorage.setMockInitialValues({});
      await StorageService.init();

      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final origin = 'http://127.0.0.1:${server.port}';
      ApiConstants.setBaseUrl(origin);
      await StorageService.setTokens((
        access: 'shipper-token',
        refresh: 'shipper-refresh',
        server: origin,
      ), newSession: true);

      final recordedRequests = <String>[];
      String? lastMultipartBody;

      server.listen((request) async {
        final rawBody = await utf8.decoder.bind(request).join();
        recordedRequests.add('${request.method} ${request.uri}');
        request.response.headers.contentType = ContentType.json;

        if (request.uri.path == ApiConstants.shipperReturns) {
          request.response.write(
            jsonEncode({
              'status': 200,
              'data': {
                'content': [
                  {
                    'id': 10,
                    'orderId': 501,
                    'orderNumber': 'ORD-501',
                    'status': 'PICKUP_ASSIGNED',
                    'returnMethod': 'SHIPPER_PICKUP',
                    'reason': 'Lỗi bao bì',
                    'refundAmount': 199000,
                    'customerName': 'Khách A',
                    'customerPhone': '0909000111',
                    'pickupAddress': '99 Nguyễn Huệ',
                  },
                ],
                'page': 0,
                'size': 10,
                'totalElements': 1,
                'totalPages': 1,
                'last': true,
              },
            }),
          );
        } else {
          if (request.uri.path == ApiConstants.shipperCompleteReturnPickup(10)) {
            lastMultipartBody = rawBody;
          }
          request.response.write(
            jsonEncode({
              'status': 200,
              'data': {'id': 10, 'status': 'PICKED_UP'},
            }),
          );
        }
        await request.response.close();
      });

      final provider = ShipperProvider();
      addTearDown(provider.dispose);

      await provider.setReturnFilter('PICKUP_ASSIGNED');
      expect(provider.returnPickups.length, 1);
      expect(provider.returnPickups.first.orderNumber, 'ORD-501');

      await provider.setReturnSearchKeyword('ORD-501');
      await provider.startReturnPickup(10);

      final tmpDir = Directory.systemTemp.createTempSync('shipper_pickup_test_');
      addTearDown(() => tmpDir.deleteSync(recursive: true));
      final proofFile = File('${tmpDir.path}/proof.jpg')
        ..writeAsBytesSync(utf8.encode('fake-jpeg-bytes'));

      final uploaded = await provider.completeReturnPickup(
        returnId: 10,
        imageFile: XFile(proofFile.path, name: 'proof.jpg'),
        notes: 'Còn nguyên seal',
      );
      expect(uploaded, isTrue);
      expect(lastMultipartBody, contains('fake-jpeg-bytes'));
      expect(lastMultipartBody, contains('Còn nguyên seal'));

      await provider.reportReturnPickupFailure(10, 'Khách không nghe máy');
      await provider.handoverReturnToWarehouse(10, notes: 'Giao cho thủ kho');

      expect(
        recordedRequests,
        containsAll([
          contains('GET /api/v1/shipper/returns?page=0&size=10&status=PICKUP_ASSIGNED'),
          contains('PATCH /api/v1/shipper/returns/10/start-pickup'),
          contains('POST /api/v1/shipper/returns/10/pickup'),
          contains('POST /api/v1/shipper/returns/10/pickup-failed'),
          contains('POST /api/v1/shipper/returns/10/handover'),
        ]),
      );
    },
  );

  testWidgets(
    'ShipperDashboardScreen shows Thu hoi hoan tab with returns and returning orders at 320px @ 1.5x',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 740));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final provider = _FakeReturnShipperProvider()
        ..seededReturns = [
          _sampleReturn(id: 101, status: 'PICKUP_ASSIGNED'),
          _sampleReturn(
            id: 102,
            status: 'PICKED_UP',
            pickedUpAt: DateTime.utc(2026, 11, 25, 9, 0),
          ),
        ]
        ..seededOrders = [
          ShipperOrderModel(
            id: 909,
            orderNumber: 'ORD-RETURNING-9',
            status: 'RETURNING_TO_SENDER',
            paymentStatus: 'PENDING',
            paymentMethod: 'COD',
            totalAmount: 180000,
            customerName: 'Phạm Long',
            customerPhone: '0933444555',
            shippingAddress: '45 Pasteur, Q1',
            codAmount: 180000,
          ),
        ];

      final auth = _FakeReturnAuthProvider();
      addTearDown(auth.dispose);
      addTearDown(provider.dispose);

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>.value(value: auth),
            ChangeNotifierProvider<ShipperProvider>.value(value: provider),
          ],
          child: MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(1.5)),
              child: child!,
            ),
            home: const ShipperDashboardScreen(),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Thu hồi hoàn'), findsOneWidget);
      await tester.tap(find.text('Thu hồi hoàn'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Thu hồi hàng hoàn'), findsOneWidget);
      expect(find.text('Thu hồi #101 · #ORD-101'), findsOneWidget);
      expect(find.text('Cần lấy: 1 phiếu • Đang giữ chờ trả kho: 2 kiện'), findsOneWidget);

      for (var i = 0; i < 4 && find.text('Thu hồi #102 · #ORD-102').evaluate().isEmpty; i++) {
        await tester.drag(find.byType(ShipperReturnTab), const Offset(0, -450));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.text('Thu hồi #102 · #ORD-102'), findsOneWidget);

      for (var i = 0; i < 4 && find.text('#ORD-RETURNING-9 · Phạm Long').evaluate().isEmpty; i++) {
        await tester.drag(find.byType(ShipperReturnTab), const Offset(0, -450));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.text('#ORD-RETURNING-9 · Phạm Long'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'ReturnPickupConfirmationScreen requires both item checklist and photo proof',
    (tester) async {
      final ret = _sampleReturn(
        id: 101,
        status: 'PICKUP_ASSIGNED',
        pickupStartedAt: DateTime.utc(2026, 11, 25, 8, 10),
      );
      final provider = _FakeReturnShipperProvider();
      addTearDown(provider.dispose);

      await tester.pumpWidget(
        ChangeNotifierProvider<ShipperProvider>.value(
          value: provider,
          child: MaterialApp(
            home: ReturnPickupConfirmationScreen(returnRequest: ret),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Cần xác nhận đủ sản phẩm thu hồi và chụp ảnh bằng chứng'),
        findsOneWidget,
      );
      final submitButtonFinder = find.widgetWithText(
        ElevatedButton,
        'Xác nhận đã lấy hàng hoàn',
      );
      expect(
        tester.widget<ElevatedButton>(submitButtonFinder).onPressed,
        isNull,
      );

      final checkbox = find.text(
        'Đã kiểm đủ sản phẩm, số lượng & đóng gói niêm phong',
      );
      await tester.scrollUntilVisible(
        checkbox,
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(checkbox);
      await tester.pumpAndSettle();

      expect(
        find.text('Vui lòng chụp hoặc chọn ảnh xác nhận lấy hàng hoàn'),
        findsOneWidget,
      );
      expect(
        tester.widget<ElevatedButton>(submitButtonFinder).onPressed,
        isNull,
      );
    },
  );

  testWidgets(
    'NotificationScreen routes SHIPPER_RETURN_PICKUP tap to ShipperReturnDetailScreen',
    (tester) async {
      final provider = _FakeReturnShipperProvider()
        ..seededReturns = [
          _sampleReturn(
            id: 55,
            status: 'PICKED_UP',
            pickupStartedAt: DateTime.utc(2026, 11, 25, 8, 5),
            pickedUpAt: DateTime.utc(2026, 11, 25, 8, 25),
          ),
        ]
        ..seededNotifications = [
          {
            'id': 701,
            'type': 'SHIPPER_RETURN_PICKUP',
            'title': 'Yêu cầu thu hồi hàng hoàn mới',
            'message': 'Đơn #ORD-55 cần đến lấy hàng hoàn',
            'target': '/api/v1/shipper/returns/55',
            'readAt': null,
            'createdAt': '2026-11-25T08:00:00Z',
          },
        ];
      addTearDown(provider.dispose);

      await tester.pumpWidget(
        ChangeNotifierProvider<ShipperProvider>.value(
          value: provider,
          child: const MaterialApp(home: NotificationScreen()),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Yêu cầu thu hồi hàng hoàn mới'));
      await tester.pumpAndSettle();

      expect(provider.calls, contains('markRead:701'));
      expect(provider.calls, contains('fetchReturnDetail:55'));
      expect(find.text('Chi tiết thu hồi hoàn'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Mốc đối soát thu hồi'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Mốc đối soát thu hồi'), findsOneWidget);

      await tester.tap(find.text('Báo đã bàn giao cho kho'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xác nhận bàn giao'));
      await tester.pumpAndSettle();

      expect(provider.calls, contains('handoverReturnToWarehouse:55:'));
    },
  );
}
