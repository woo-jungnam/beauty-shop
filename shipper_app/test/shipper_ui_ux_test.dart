import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:beautyshop_shipper/core/storage/shipper_settings_store.dart';
import 'package:beautyshop_shipper/core/theme/app_colors.dart';
import 'package:beautyshop_shipper/core/storage/storage_service.dart';
import 'package:beautyshop_shipper/data/models/shipper_account_model.dart';
import 'package:beautyshop_shipper/data/models/shipper_order_model.dart';
import 'package:beautyshop_shipper/presentation/screens/account/shipper_account_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/dashboard/shipper_dashboard_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/delivery/delivery_confirmation_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/delivery/delivery_dates.dart';
import 'package:beautyshop_shipper/presentation/screens/delivery/shipper_order_detail_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/notification/notification_screen.dart';
import 'package:beautyshop_shipper/providers/auth_provider.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';
import 'package:beautyshop_shipper/presentation/screens/account/shipper_profile_edit_screens.dart';

class _FakeAuth extends AuthProvider {
  String _fullName = 'Nguyễn Văn Giao';
  bool loggedOut = false;

  @override
  bool get isAuthenticated => true;

  @override
  String? get currentFullName => _fullName;

  @override
  String? get currentUsername => 'shipper01';

  @override
  Future<void> updateCachedFullName(String fullName) async {
    _fullName = fullName.trim();
    notifyListeners();
  }

  @override
  Future<void> logout() async {
    loggedOut = true;
  }
}

class _FakeShipperProvider extends ShipperProvider {
  int historyCalls = 0;
  int statsCalls = 0;
  int profileCalls = 0;
  String? lastChangedCurrentPassword;
  String? lastChangedNewPassword;
  final List<int> markedReadIds = [];

  ShipperUserProfile? _fakeUserProfile = ShipperUserProfile.fromJson({
    'id': 50,
    'username': 'shipper01',
    'email': 'shipper01@beautyshop.vn',
    'fullName': 'Nguyễn Văn Giao',
    'phone': null,
    'bio': null,
    'gender': null,
    'dateOfBirth': null,
    'status': 'ACTIVE',
    'roles': ['ROLE_SHIPPER'],
  });

  ShipperWorkProfile? _fakeWorkProfile = const ShipperWorkProfile();

  @override
  ShipperUserProfile? get userProfile => _fakeUserProfile;

  @override
  ShipperWorkProfile? get workProfile => _fakeWorkProfile;

  final ShipperOrderModel assignedOrder = ShipperOrderModel.fromJson({
    'id': 11,
    'orderNumber': 'ORD-ASSIGNED-11',
    'customerName': 'Trần Thị Hoa',
    'customerPhone': '0901234567',
    'shippingAddress': '12 Lê Lợi, Quận 1, TP.HCM',
    'totalAmount': 350000,
    'codAmount': 350000,
    'paymentMethod': 'COD',
    'paymentStatus': 'PENDING',
    'status': 'SHIPPED',
  });

  final ShipperOrderModel failedOrder = ShipperOrderModel.fromJson({
    'id': 12,
    'orderNumber': 'ORD-FAILED-12',
    'customerName': 'Lê Minh Tuấn',
    'customerPhone': '0987654321',
    'shippingAddress': '45 Hai Bà Trưng, Quận 3, TP.HCM',
    'totalAmount': 500000,
    'codAmount': 0,
    'paymentMethod': 'BANK_TRANSFER',
    'paymentStatus': 'PARTIALLY_REFUNDED',
    'status': 'DELIVERY_FAILED',
  });

  @override
  List<ShipperOrderModel> get orders => [assignedOrder, failedOrder];

  @override
  int get unreadCount => 1;

  @override
  Future<void> fetchOrders({
    int? page,
    bool background = false,
  }) async {}

  @override
  Future<void> refreshUnreadCount() async {}

  @override
  Future<ShipperOrderModel> fetchOrderDetail(int orderId) async =>
      orders.firstWhere((o) => o.id == orderId, orElse: () => assignedOrder);

  @override
  Future<Map<String, dynamic>> fetchHistory(
    String from,
    String to,
    int page,
    bool deliveredOnly,
  ) async {
    historyCalls++;
    return {
      'content': [
        {
          'id': 12,
          'orderNumber': 'ORD-HIST-12',
          'customerName': 'Lê Minh Tuấn',
          'status': 'DELIVERY_FAILED',
        },
      ],
      'last': true,
    };
  }

  @override
  Future<Map<String, dynamic>> fetchStats(String from, String to) async {
    statsCalls++;
    return {
      'todayOrders': null,
      'weekOrders': 3,
      'monthOrders': 12,
      'totalOrders': 15,
      'deliveredOrders': 12,
      'totalCodCollected': null,
      'totalRevenue': 2400000,
      'totalDeliveryFees': 360000,
      'estimatedEarnings': null,
      'ordersByDay': {'2026-11-24': 2, '2026-11-25': 1},
    };
  }

  @override
  Future<Map<String, dynamic>> fetchNotifications(int page) async => {
    'content': [
      {
        'id': 901,
        'type': 'SHIPPER_ASSIGNMENT',
        'title': 'Bạn có đơn giao mới',
        'body': 'Đơn #ORD-ASSIGNED-11 vừa được phân công.',
        'target': '/api/v1/shipper/orders/11',
        'createdAt': '2026-11-25T02:00:00Z',
        'readAt': null,
      },
    ],
    'last': true,
  };

  @override
  Future<void> markRead(int id) async {
    markedReadIds.add(id);
  }

  @override
  Future<void> fetchAccountProfile() async {
    profileCalls++;
    notifyListeners();
  }

  @override
  Future<ShipperUserProfile> updateUserProfile({
    required String fullName,
    String? phone,
    String? avatarUrl,
    String? bio,
    String? gender,
    String? dateOfBirth,
  }) async {
    _fakeUserProfile = ShipperUserProfile(
      id: 50,
      username: 'shipper01',
      email: 'shipper01@beautyshop.vn',
      fullName: fullName,
      phone: (phone == null || phone.trim().isEmpty) ? null : phone.trim(),
      bio: (bio == null || bio.trim().isEmpty) ? null : bio.trim(),
      gender: gender,
      dateOfBirth: dateOfBirth,
      status: 'ACTIVE',
      roles: const ['ROLE_SHIPPER'],
    );
    notifyListeners();
    return _fakeUserProfile!;
  }

  @override
  Future<ShipperWorkProfile> updateWorkProfile({
    String? vehicleType,
    String? vehiclePlate,
    String? serviceArea,
    String? emergencyContactName,
    String? emergencyContactPhone,
    String? workShift,
  }) async {
    String? clean(String? v) =>
        (v == null || v.trim().isEmpty) ? null : v.trim();
    _fakeWorkProfile = ShipperWorkProfile(
      vehicleType: clean(vehicleType),
      vehiclePlate: clean(vehiclePlate)?.toUpperCase(),
      serviceArea: clean(serviceArea),
      emergencyContactName: clean(emergencyContactName),
      emergencyContactPhone: clean(emergencyContactPhone),
      workShift: clean(workShift),
      updatedAt: DateTime.utc(2026, 11, 25, 2, 0),
    );
    notifyListeners();
    return _fakeWorkProfile!;
  }

  @override
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    lastChangedCurrentPassword = currentPassword;
    lastChangedNewPassword = newPassword;
  }
}

Future<void> _initTestStorage() async {
  SharedPreferences.setMockInitialValues({});
  FlutterSecureStorage.setMockInitialValues({});
  await StorageService.init();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Dashboard preserves tab state, filters search, and logout dialog uses white foreground on danger red',
    (tester) async {
      await _initTestStorage();
      final shipper = _FakeShipperProvider();

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>(create: (_) => _FakeAuth()),
            ChangeNotifierProvider<ShipperProvider>.value(value: shipper),
          ],
          child: const MaterialApp(home: ShipperDashboardScreen()),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.textContaining('Đang phụ trách: 1 đơn'), findsOneWidget);
      expect(find.text('2 đơn'), findsOneWidget);

      // Search filters orders locally
      await tester.tap(find.byTooltip('Tìm kiếm đơn'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), '0901234567');
      await tester.pumpAndSettle();
      expect(find.text('#ORD-ASSIGNED-11'), findsOneWidget);
      expect(find.text('#ORD-FAILED-12'), findsNothing);

      // Clear search restores both (close search first or scroll vertical list)
      await tester.tap(find.byTooltip('Xóa tìm kiếm'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Đóng tìm kiếm'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('#ORD-FAILED-12'),
        120,
        scrollable: find.byWidgetPredicate(
          (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
        ),
      );
      expect(find.text('#ORD-FAILED-12'), findsOneWidget);

      // Lazy tab state preservation via IndexedStack
      expect(shipper.historyCalls, 0);
      await tester.tap(find.text('Lịch sử'));
      await tester.pumpAndSettle();
      expect(shipper.historyCalls, 1);

      await tester.tap(find.text('Thống kê'));
      await tester.pumpAndSettle();
      expect(shipper.statsCalls, 1);
      // Null-safe stats rendering
      expect(find.text('0 đơn'), findsOneWidget);
      expect(find.text('Chưa cấu hình'), findsOneWidget);

      // Switching back to History keeps it alive without re-fetching
      await tester.tap(find.text('Lịch sử'));
      await tester.pumpAndSettle();
      expect(shipper.historyCalls, 1);

      // Logout inside account menu with accessible white-on-danger button
      await tester.tap(find.byTooltip('Menu tài khoản'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Đăng xuất'));
      await tester.pumpAndSettle();
      final confirmLogoutBtn = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Đăng xuất'),
      );
      expect(
        confirmLogoutBtn.style?.backgroundColor?.resolve({}),
        AppColors.danger,
      );
      expect(
        confirmLogoutBtn.style?.foregroundColor?.resolve({}),
        Colors.white,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'DeliveryConfirmationScreen surfaces COD + photo requirements clearly on narrow width',
    (tester) async {
      await _initTestStorage();
      await tester.binding.setSurfaceSize(const Size(320, 700));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final shipper = _FakeShipperProvider();
      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<ShipperProvider>.value(value: shipper),
          ],
          child: MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(1.5)),
              child: child!,
            ),
            home: DeliveryConfirmationScreen(order: shipper.assignedOrder),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Cần tích xác nhận thu COD và chụp ảnh bằng chứng'),
        findsOneWidget,
      );
      final submitFinder = find.widgetWithText(
        ElevatedButton,
        'Xác nhận giao thành công',
      );
      expect(tester.widget<ElevatedButton>(submitFinder).onPressed, isNull);

      await tester.ensureVisible(find.byType(CheckboxListTile));
      await tester.tap(find.byType(CheckboxListTile));
      await tester.pumpAndSettle();
      expect(
        find.text('Vui lòng chụp hoặc chọn ảnh bằng chứng giao hàng'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'ShipperOrderDetailScreen renders PARTIALLY_REFUNDED and action buttons cleanly',
    (tester) async {
      final shipper = _FakeShipperProvider();
      await tester.pumpWidget(
        MaterialApp(
          home: ShipperOrderDetailScreen(order: shipper.failedOrder),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Đã hoàn một phần'), findsOneWidget);
      expect(find.text('Chuyển khoản ngân hàng'), findsOneWidget);
      expect(find.text('Gọi ngay'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'DeliveryDates highlights selected preset chip cleanly',
    (tester) async {
      DateTimeRange range = DeliveryDates.month();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) => DeliveryDates(
                range: range,
                onChange: (next) => setState(() => range = next),
              ),
            ),
          ),
        ),
      );

      expect(
        tester
            .widget<ChoiceChip>(find.widgetWithText(ChoiceChip, 'Tháng này'))
            .selected,
        isTrue,
      );
      await tester.tap(find.text('Hôm nay'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<ChoiceChip>(find.widgetWithText(ChoiceChip, 'Hôm nay'))
            .selected,
        isTrue,
      );
    },
  );

  testWidgets(
    'NotificationScreen marks read in place and navigates to order detail',
    (tester) async {
      await _initTestStorage();
      final shipper = _FakeShipperProvider();

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<ShipperProvider>.value(value: shipper),
          ],
          child: const MaterialApp(home: NotificationScreen()),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Đọc hết'), findsOneWidget);
      expect(find.text('• Nhấn để xem đơn'), findsOneWidget);

      await tester.tap(find.text('Bạn có đơn giao mới'));
      await tester.pumpAndSettle();

      expect(shipper.markedReadIds, [901]);
      expect(find.text('Chi tiết đơn giao'), findsOneWidget);
      expect(find.text('#ORD-ASSIGNED-11'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Account tab edits personal profile, adds shipper vehicle info, updates personal settings, and validates password change',
    (tester) async {
      await _initTestStorage();
      await tester.binding.setSurfaceSize(const Size(360, 760));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final auth = _FakeAuth();
      final shipper = _FakeShipperProvider();

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>.value(value: auth),
            ChangeNotifierProvider<ShipperProvider>.value(value: shipper),
          ],
          child: const MaterialApp(home: ShipperDashboardScreen()),
        ),
      );
      await tester.pumpAndSettle();

      // Switch to 4th tab: Tài khoản
      await tester.tap(find.text('Tài khoản'));
      await tester.pumpAndSettle();
      expect(find.text('Tài khoản & Cài đặt'), findsOneWidget);
      expect(shipper.profileCalls, 1);

      // Edit personal account info (full-screen ShipperEditPersonalScreen)
      await tester.tap(find.text('Chỉnh sửa'));
      await tester.pumpAndSettle();
      expect(find.byType(ShipperEditPersonalScreen), findsOneWidget);
      expect(find.byType(AlertDialog), findsNothing);
      final textFields = find.descendant(
        of: find.byType(ShipperEditPersonalScreen),
        matching: find.byType(TextFormField),
      );
      await tester.enterText(textFields.at(0), 'Nguyễn Hoàng Nam');
      await tester.enterText(textFields.at(1), '0912345678');
      await tester.ensureVisible(find.widgetWithText(ChoiceChip, 'Nam'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ChoiceChip, 'Nam'));
      await tester.tap(find.text('Lưu thay đổi'));
      await tester.pumpAndSettle();

      expect(find.byType(ShipperEditPersonalScreen), findsNothing);
      expect(find.text('Nguyễn Hoàng Nam'), findsWidgets);
      expect(find.text('0912345678'), findsOneWidget);
      expect(auth.currentFullName, 'Nguyễn Hoàng Nam');

      // Add shipper vehicle & work profile (full-screen ShipperEditWorkScreen)
      await tester.scrollUntilVisible(
        find.text('Bổ sung'),
        150,
        scrollable: find.byWidgetPredicate(
          (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
        ),
      );
      await tester.ensureVisible(find.text('Bổ sung'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Bổ sung'));
      await tester.pumpAndSettle();
      expect(find.byType(ShipperEditWorkScreen), findsOneWidget);
      expect(find.byType(AlertDialog), findsNothing);
      await tester.tap(find.widgetWithText(ChoiceChip, 'Xe máy'));
      final workFields = find.descendant(
        of: find.byType(ShipperEditWorkScreen),
        matching: find.byType(TextFormField),
      );
      await tester.enterText(workFields.at(1), '59x1-999.88');
      await tester.enterText(workFields.at(2), 'Quận 1, Quận 3');
      await tester.tap(find.text('Lưu thông tin'));
      await tester.pumpAndSettle();

      expect(find.byType(ShipperEditWorkScreen), findsNothing);
      expect(find.text('Xe máy'), findsOneWidget);
      expect(find.text('59X1-999.88'), findsOneWidget);
      expect(find.text('Quận 1, Quận 3'), findsOneWidget);

      // Adjust personal settings & verify persistence in ShipperSettingsStore
      await tester.scrollUntilVisible(
        find.text('Lớn'),
        150,
        scrollable: find.byWidgetPredicate(
          (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
        ),
      );
      await tester.ensureVisible(find.text('Lớn'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Lớn'));
      await tester.pumpAndSettle();
      expect(ShipperSettingsStore.instance.textScaleFactor, 1.15);

      await tester.ensureVisible(
        find.text('Hỏi xác nhận trước khi "Bắt đầu giao"'),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hỏi xác nhận trước khi "Bắt đầu giao"'));
      await tester.pumpAndSettle();
      expect(ShipperSettingsStore.instance.confirmBeforeStart, isTrue);

      await tester.scrollUntilVisible(
        find.text('Mặc định'),
        -150,
        scrollable: find.byWidgetPredicate(
          (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
        ),
      );
      await tester.ensureVisible(find.text('Mặc định'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mặc định'));
      await tester.pumpAndSettle();
      expect(ShipperSettingsStore.instance.textScaleFactor, 1.0);
      expect(ShipperSettingsStore.instance.confirmBeforeStart, isFalse);

      // Change password flow (full-screen ShipperChangePasswordScreen)
      await tester.scrollUntilVisible(
        find.text('Đổi mật khẩu'),
        150,
        scrollable: find.byWidgetPredicate(
          (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
        ),
      );
      await tester.ensureVisible(find.text('Đổi mật khẩu'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Đổi mật khẩu'));
      await tester.pumpAndSettle();
      expect(find.byType(ShipperChangePasswordScreen), findsOneWidget);
      expect(find.byType(AlertDialog), findsNothing);
      final pwdFields = find.descendant(
        of: find.byType(ShipperChangePasswordScreen),
        matching: find.byType(TextFormField),
      );
      await tester.enterText(pwdFields.at(0), 'OldPass123!');
      await tester.enterText(pwdFields.at(1), 'NewPass123!');
      await tester.enterText(pwdFields.at(2), 'NewPass123!');
      await tester.tap(find.text('Xác nhận đổi'));
      await tester.pumpAndSettle();

      expect(shipper.lastChangedCurrentPassword, 'OldPass123!');
      expect(shipper.lastChangedNewPassword, 'NewPass123!');
      expect(auth.loggedOut, isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'ShipperAccountScreen fits 320px width with 1.5x text scale without overflow',
    (tester) async {
      await _initTestStorage();
      await tester.binding.setSurfaceSize(const Size(320, 700));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final auth = _FakeAuth();
      final shipper = _FakeShipperProvider();
      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>.value(value: auth),
            ChangeNotifierProvider<ShipperProvider>.value(value: shipper),
          ],
          child: MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(1.5)),
              child: child!,
            ),
            home: const Scaffold(body: ShipperAccountScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Tài khoản & Cài đặt'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Full-screen edit screens render cleanly across 320px (1.5x text), 640x360 landscape, and 840px tablet',
    (tester) async {
      await _initTestStorage();
      addTearDown(() => tester.binding.setSurfaceSize(null));

      for (final (size, scale) in const [
        (Size(320, 568), 1.5),
        (Size(640, 360), 1.0),
        (Size(840, 900), 1.0),
      ]) {
        await tester.binding.setSurfaceSize(size);
        await tester.pumpWidget(
          MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: TextScaler.linear(scale)),
              child: child!,
            ),
            home: ShipperEditPersonalScreen(
              initial: const ShipperUserProfile(
                id: 50,
                username: 'shipper01',
                email: 'shipper01@beautyshop.vn',
                fullName: 'Nguyễn Văn Giao',
                phone: '0901234567',
                gender: 'MALE',
                dateOfBirth: '1998-05-20',
              ),
              onChangeAvatar: () async => null,
              onSave: (_) async {},
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Chỉnh sửa thông tin cá nhân'), findsOneWidget);
        expect(find.text('Lưu thay đổi'), findsOneWidget);
        expect(tester.takeException(), isNull);

        await tester.pumpWidget(
          MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: TextScaler.linear(scale)),
              child: child!,
            ),
            home: ShipperEditWorkScreen(
              initial: const ShipperWorkProfile(
                vehicleType: 'Xe máy',
                vehiclePlate: '59X1-123.45',
                serviceArea: 'Quận 1, Quận 3',
                workShift: 'Cả ngày',
              ),
              onSave: (_) async {},
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Thông tin tài xế & phương tiện'), findsOneWidget);
        expect(find.text('Lưu thông tin'), findsOneWidget);
        expect(tester.takeException(), isNull);

        await tester.pumpWidget(
          MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: TextScaler.linear(scale)),
              child: child!,
            ),
            home: ShipperChangePasswordScreen(
              onSubmit: (_, _) async {},
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Đổi mật khẩu'), findsOneWidget);
        expect(find.text('Xác nhận đổi'), findsOneWidget);
        expect(tester.takeException(), isNull);
      }
    },
  );

  testWidgets(
    'Dashboard uses select status filter and full pagination cleanly across mobile viewports',
    (tester) async {
      await _initTestStorage();
      await tester.binding.setSurfaceSize(const Size(320, 640));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final shipper = _PaginatedShipperProvider();

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>(create: (_) => _FakeAuth()),
            ChangeNotifierProvider<ShipperProvider>.value(value: shipper),
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
      await tester.pumpAndSettle();

      // Home header uses select dropdown instead of ChoiceChips
      expect(find.byKey(const ValueKey('shipper-status-filter')), findsOneWidget);
      expect(find.byType(ChoiceChip), findsNothing);
      expect(find.text('25 đơn'), findsOneWidget);
      expect(find.text('#ORD-PAGE-01'), findsOneWidget);
      expect(find.text('#ORD-PAGE-11'), findsNothing);

      // Select status filter: DELIVERED ('Đã giao')
      await tester.tap(find.byKey(const ValueKey('shipper-status-filter')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Đã giao').last);
      await tester.pumpAndSettle();

      expect(shipper.currentFilter, 'DELIVERED');
      expect(shipper.currentPage, 0);
      expect(find.text('8 đơn'), findsOneWidget);
      expect(find.text('#ORD-PAGE-02'), findsOneWidget);
      expect(find.text('#ORD-PAGE-01'), findsNothing);

      // Reset status filter back to ALL ('Tất cả')
      await tester.tap(find.byKey(const ValueKey('shipper-status-filter')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Tất cả').last);
      await tester.pumpAndSettle();
      expect(shipper.currentFilter, 'ALL');
      expect(find.text('25 đơn'), findsOneWidget);

      // Scroll to pagination controls and verify Page 1 -> Page 2 -> Page 3 -> Page 2
      final scrollable = find.byWidgetPredicate(
        (w) => w is Scrollable && w.axisDirection == AxisDirection.down,
      );
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('shipper-pagination-card')),
        400,
        scrollable: scrollable,
        maxScrolls: 100,
      );
      await tester.pumpAndSettle();
      await tester.pumpAndSettle();
      expect(find.text('Trang 1/3 • 1–10 / 25 đơn'), findsOneWidget);

      await tester.ensureVisible(find.byKey(const ValueKey('shipper-page-btn-2')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('shipper-page-btn-2')));
      await tester.pumpAndSettle();
      expect(shipper.currentPage, 1);
      expect(find.text('#ORD-PAGE-11'), findsOneWidget);
      expect(find.text('#ORD-PAGE-01'), findsNothing);

      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('shipper-page-next')),
        220,
        scrollable: scrollable,
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byKey(const ValueKey('shipper-page-next')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('shipper-page-next')));
      await tester.pumpAndSettle();
      expect(shipper.currentPage, 2);
      expect(find.text('#ORD-PAGE-21'), findsOneWidget);

      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('shipper-page-prev')),
        220,
        scrollable: scrollable,
      );
      await tester.pumpAndSettle();
      expect(find.text('Trang 3/3 • 21–25 / 25 đơn'), findsOneWidget);
      await tester.ensureVisible(find.byKey(const ValueKey('shipper-page-prev')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('shipper-page-prev')));
      await tester.pumpAndSettle();
      expect(shipper.currentPage, 1);

      // Change page size from 10 to 20 via select dropdown
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('shipper-page-size-select')),
        220,
        scrollable: scrollable,
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('shipper-page-size-select')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('shipper-page-size-select')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('20 / trang').last);
      await tester.pumpAndSettle();

      expect(shipper.pageSize, 20);
      expect(shipper.currentPage, 0);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('shipper-pagination-card')),
        400,
        scrollable: scrollable,
        maxScrolls: 100,
      );
      await tester.pumpAndSettle();
      expect(find.text('Trang 1/2 • 1–20 / 25 đơn'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}

class _PaginatedShipperProvider extends ShipperProvider {
  _PaginatedShipperProvider() {
    _recompute(page: 0);
  }

  final List<ShipperOrderModel> _all = List.generate(25, (index) {
    final number = index + 1;
    final code = number.toString().padLeft(2, '0');
    final status = switch (index % 3) {
      0 => 'SHIPPED',
      1 => 'DELIVERED',
      _ => 'DELIVERY_FAILED',
    };
    return ShipperOrderModel.fromJson({
      'id': number,
      'orderNumber': 'ORD-PAGE-$code',
      'customerName': 'Khách hàng $code',
      'customerPhone': '09000000$code',
      'shippingAddress': '$code Nguyễn Trãi, Quận 1',
      'totalAmount': 250000,
      'codAmount': status == 'SHIPPED' ? 250000 : 0,
      'paymentMethod': status == 'SHIPPED' ? 'COD' : 'BANK_TRANSFER',
      'paymentStatus': status == 'DELIVERED' ? 'PAID' : 'PENDING',
      'status': status,
    });
  });

  List<ShipperOrderModel> _pageOrders = [];
  int _page = 0;
  int _total = 0;
  int _pages = 0;
  int _size = ShipperProvider.defaultPageSize;
  String _filter = 'ALL';
  String _keyword = '';

  @override
  List<ShipperOrderModel> get orders => _pageOrders;

  @override
  int get currentPage => _page;

  @override
  int get totalElements => _total;

  @override
  int get totalPages => _pages;

  @override
  int get pageSize => _size;

  @override
  String get currentFilter => _filter;

  @override
  String get searchKeyword => _keyword;

  @override
  bool get hasMore => _pages > 0 && _page + 1 < _pages;

  void _recompute({required int page}) {
    final q = _keyword.toLowerCase();
    final filtered = _all.where((o) {
      final matchesStatus = _filter == 'ALL' || o.status == _filter;
      final matchesQuery =
          q.isEmpty ||
          o.orderNumber.toLowerCase().contains(q) ||
          o.customerName.toLowerCase().contains(q) ||
          o.customerPhone.contains(q);
      return matchesStatus && matchesQuery;
    }).toList();
    _total = filtered.length;
    _pages = _total == 0 ? 0 : ((_total + _size - 1) ~/ _size);
    _page = _pages == 0 ? 0 : page.clamp(0, _pages - 1);
    final start = _page * _size;
    final end = (start + _size).clamp(0, filtered.length);
    _pageOrders = start >= filtered.length ? [] : filtered.sublist(start, end);
  }

  @override
  Future<void> setFilter(String filter) async {
    _filter = filter;
    _recompute(page: 0);
    notifyListeners();
  }

  @override
  Future<void> setPageSize(int size) async {
    _size = size;
    _recompute(page: 0);
    notifyListeners();
  }

  @override
  Future<void> setSearchKeyword(String keyword) async {
    _keyword = keyword.trim();
    _recompute(page: 0);
    notifyListeners();
  }

  @override
  Future<void> fetchOrders({int? page, bool background = false}) async {
    _recompute(page: page ?? _page);
    notifyListeners();
  }

  @override
  Future<void> refreshUnreadCount() async {}
}

