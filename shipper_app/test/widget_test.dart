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
  final order = ShipperOrderModel.fromJson({
    'id': 1,
    'orderNumber': 'BEAUTYSHOP-20261121-123456789',
    'customerName': 'Khách thử nghiệm',
    'totalAmount': 123456789,
    'codAmount': 123456789,
    'status': 'SHIPPED',
    'deliveryStartedAt': '2026-11-21T06:00:00Z',
  });

  @override
  List<ShipperOrderModel> get orders => [order];

  @override
  Future<void> fetchOrders({
    bool loadMore = false,
    bool background = false,
  }) async {}

  @override
  Future<void> refreshUnreadCount() async {}
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
    await tester.drag(
      find.byType(SingleChildScrollView).last,
      const Offset(0, -550),
    );
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
    expect(find.text('BeautyShop Shipper'), findsOneWidget);
    expect(find.text('Đăng nhập tài xế'), findsOneWidget);
  });
}
