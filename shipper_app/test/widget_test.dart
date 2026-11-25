import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_shipper/data/models/shipper_order_model.dart';
import 'package:beautyshop_shipper/providers/auth_provider.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';
import 'package:beautyshop_shipper/presentation/screens/dashboard/shipper_dashboard_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/delivery/delivery_confirmation_screen.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:beautyshop_shipper/core/storage/storage_service.dart';
import 'package:beautyshop_shipper/main.dart';

class _TestAuth extends AuthProvider {
  @override
  bool get isAuthenticated => true;
}

class _TestShipper extends ShipperProvider {
  final delivered = ShipperOrderModel.fromJson({
    'id': 2,
    'orderNumber': 'BEAUTYSHOP-20261121-987654321-DELIVERED',
    'customerName': 'Khách thử nghiệm',
    'status': 'DELIVERED',
    'deliveredNotes': List.filled(12, 'Đã bàn giao cho người nhận.').join(' '),
  });

  final order = ShipperOrderModel.fromJson({
    'id': 1,
    'orderNumber': 'BEAUTYSHOP-20261121-123456789',
    'customerName': 'Khách thử nghiệm',
    'shippingAddress': '123 Đường Nguyễn Huệ, Phường Sài Gòn, TP. Hồ Chí Minh',
    'totalAmount': 123456789,
    'codAmount': 123456789,
    'status': 'SHIPPED',
    'deliveryStartedAt': '2026-11-21T06:00:00Z',
  });

  @override
  List<ShipperOrderModel> get orders => [order, delivered];

  @override
  Future<void> fetchOrders({
    int? page,
    bool background = false,
  }) async {}

  @override
  Future<void> refreshUnreadCount() async {}

  @override
  Future<Map<String, dynamic>> fetchHistory(
    String from,
    String to,
    int page,
    bool deliveredOnly,
  ) async => {
    'content': [
      {
        'id': 3,
        'orderNumber': 'ORDER-HISTORY-WITH-LONG-REFERENCE',
        'customerName': 'Khách thử nghiệm',
        'status': 'RETURNING_TO_SENDER',
      },
    ],
    'last': true,
  };

  @override
  Future<Map<String, dynamic>> fetchStats(String from, String to) async => {
    'todayOrders': 1,
    'weekOrders': 1,
    'monthOrders': 1,
    'totalOrders': 1,
    'deliveredOrders': 1,
    'totalCodCollected': 123456789,
    'totalRevenue': 123456789,
    'totalDeliveryFees': 123456789,
    'estimatedEarnings': 123456789,
    'ordersByDay': {'2026-11-21': 1},
  };
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Delivery screens fit 320 logical pixels with enlarged text', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    await StorageService.init();
    tester.view.physicalSize = const Size(320, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final shipper = _TestShipper();
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthProvider>(create: (_) => _TestAuth()),
          ChangeNotifierProvider<ShipperProvider>.value(value: shipper),
        ],
        child: MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(1.5)),
            child: child!,
          ),
          home: const ShipperDashboardScreen(),
        ),
      ),
    );
    await tester.pump();
    expect(find.text('#BEAUTYSHOP-20261121-123456789'), findsOneWidget);
    expect(tester.takeException(), isNull);
    final context = tester.element(find.byType(ShipperDashboardScreen));
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => DeliveryConfirmationScreen(order: shipper.order),
      ),
    );
    await tester.pumpAndSettle();
    final confirmation = find.text('Xác nhận giao thành công');
    final confirmationPosition = tester.getCenter(confirmation);
    await tester.drag(
      find.byType(SingleChildScrollView).last,
      const Offset(0, -550),
    );
    await tester.pumpAndSettle();
    expect(tester.getCenter(confirmation), confirmationPosition);
    expect(tester.takeException(), isNull);
    Navigator.of(context).pop();
    await tester.pumpAndSettle();

    tester.view.physicalSize = const Size(320, 380);
    await tester.pumpAndSettle();
    final proofButton = find.text('Xem bằng chứng giao hàng').hitTestable();
    await tester.scrollUntilVisible(proofButton, 120);
    await tester.pumpAndSettle();
    await tester.tap(proofButton);
    await tester.pumpAndSettle();
    expect(find.byType(Dialog), findsOneWidget);
    expect(tester.takeException(), isNull);
    Navigator.of(context).pop();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Lịch sử'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('#ORDER-HISTORY-WITH-LONG-REFERENCE'),
      120,
    );
    expect(tester.takeException(), isNull);

    await tester.tap(find.text('Thống kê'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('COD tài xế đã thu'),
      120,
      scrollable: find.byWidgetPredicate(
        (widget) =>
            widget is Scrollable && widget.axisDirection == AxisDirection.down,
      ),
    );
    expect(tester.takeException(), isNull);

    tester.view.physicalSize = const Size(1024, 600);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
    shipper.dispose();
  });

  testWidgets('ShipperApp smoke test', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    await StorageService.init();

    await tester.pumpWidget(const ShipperApp());
    expect(find.text('BeautyShip'), findsOneWidget);
    expect(find.text('Đăng nhập tài xế'), findsOneWidget);
  });
}
