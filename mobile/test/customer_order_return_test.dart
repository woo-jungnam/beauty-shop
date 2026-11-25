import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/presentation/screens/orders/order_detail_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/orders/order_history_screen.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';

class _ReturnApiStub implements ApiClient {
  _ReturnApiStub({required this.onGet, this.onPost});

  final FutureOr<dynamic> Function(String path, Map<String, dynamic>? query)
  onGet;
  final FutureOr<dynamic> Function(
    String path,
    dynamic data,
    Map<String, dynamic>? headers,
  )?
  onPost;

  @override
  Future<ApiResponse<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async => ApiResponse<T>.fromJson({
    'status': 200,
    'data': await onGet(path, queryParameters),
  }, fromJsonT);

  @override
  Future<ApiResponse<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async => ApiResponse<T>.fromJson({
    'status': 200,
    'data': onPost == null ? {} : await onPost!(path, data, headers),
  }, fromJsonT);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    GoogleFonts.config.allowRuntimeFetching = false;
    FlutterSecureStorage.setMockInitialValues({});
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });

  setUp(() async {
    await StorageService.clearAuth();
  });

  test('OrderResponse formats RETURNING_TO_SENDER and OrderHistory includes it', () {
    final order = OrderResponse.fromJson({
      'id': 88,
      'orderNumber': 'ORD-RET-88',
      'status': 'RETURNING_TO_SENDER',
      'paymentStatus': 'REFUND_PENDING',
      'totalAmount': 250000,
    });
    expect(order.statusLabel, 'Đang hoàn về shop');
    expect(order.paymentStatusLabel, 'Chờ hoàn tiền');
  });

  testWidgets(
    'OrderHistoryScreen shows RETURNING_TO_SENDER inside Cancel/Return tab',
    (tester) async {
      await StorageService.setAccessToken('cust-token');
      final provider = OrderProvider(
        loadOnCreate: false,
        apiClient: _ReturnApiStub(
          onGet: (_, _) => {
            'content': [
              {
                'id': 88,
                'orderNumber': 'ORD-RET-88',
                'status': 'RETURNING_TO_SENDER',
                'paymentStatus': 'REFUND_PENDING',
                'totalAmount': 250000,
              },
            ],
            'totalPages': 1,
            'last': true,
          },
        ),
      );
      await tester.pumpWidget(
        ChangeNotifierProvider<OrderProvider>.value(
          value: provider,
          child: const MaterialApp(
            home: OrderHistoryScreen(initialTabIndex: 4),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('ORD-RET-88'), findsOneWidget);
      expect(find.text('Đang hoàn về shop'), findsOneWidget);
    },
  );

  testWidgets(
    'OrderDetailScreen renders Vietnamese return cards, mapped item names, voucher badge, and fits 320px @ 1.5x',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 760));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final postedBodies = <Map<String, dynamic>>[];
      var returnList = <Map<String, dynamic>>[
        {
          'id': 1,
          'orderId': 72,
          'status': 'RECEIVED',
          'reason': 'Vòi nhấn bị kẹt',
          'decisionReason': 'Đã nhận hàng hoàn về kho',
          'refundAmount': 209000,
          'lines': [
            {'orderItemId': 59, 'quantity': 1},
          ],
          'evidenceUrls': [],
          'refundMethod': 'VOUCHER',
          'refundVoucherCode': 'RFD-VOUCHER-72',
        },
      ];

      final api = _ReturnApiStub(
        onGet: (path, _) {
          if (path == '/api/v1/orders/72') {
            return {
              'id': 72,
              'userId': 10,
              'orderNumber': 'ORD-72',
              'status': 'DELIVERED',
              'paymentStatus': 'PARTIALLY_REFUNDED',
              'customerName': 'Nguyễn Thanh Nam',
              'customerPhone': '0902588750',
              'shippingAddress': '12 Lê Lợi',
              'totalAmount': 418000,
              'paidAmount': 418000,
              'refundedAmount': 0,
              'items': [
                {
                  'id': 59,
                  'productName': 'Dầu Gội Tresemmé Rebond',
                  'variantName': 'Chai 850g',
                  'quantity': 2,
                  'price': 220000,
                  'discount': 11000,
                },
              ],
              'statusHistories': [
                {
                  'status': 'DELIVERED',
                  'notes': 'Giao thành công',
                  'createdAt': '2026-11-25T10:00:00Z',
                },
              ],
            };
          }
          if (path == '/api/v1/orders/72/returns') {
            return returnList;
          }
          return {};
        },
        onPost: (path, data, headers) {
          if (path == '/api/v1/orders/72/returns') {
            final map = Map<String, dynamic>.from(data as Map);
            postedBodies.add(map);
            returnList = [
              ...returnList,
              {
                'id': 2,
                'orderId': 72,
                'status': 'REQUESTED',
                'reason': map['reason'],
                'refundAmount': 209000,
                'lines': map['lines'],
                'evidenceUrls': [],
                'refundMethod': map['refundMethod'],
              },
            ];
            return returnList.last;
          }
          return {};
        },
      );

      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(1.5)),
            child: child!,
          ),
          home: OrderDetailScreen(orderId: 72, apiClient: api),
        ),
      );
      await tester.pumpAndSettle();

      // Scroll down ListView so return cards & form enter viewport
      final returnCardFinder = find.text('#1 · Đã nhận hàng trả');
      await tester.scrollUntilVisible(
        returnCardFinder,
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.pumpAndSettle();

      // Vietnamese status label & mapped product line inside existing return card
      expect(returnCardFinder, findsOneWidget);
      expect(
        find.text('Sản phẩm: Dầu Gội Tresemmé Rebond (Chai 850g) × 1'),
        findsOneWidget,
      );
      expect(find.text('Mã Voucher: RFD-VOUCHER-72'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Có thể yêu cầu: 1'),
        150,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Có thể yêu cầu: 1'), findsOneWidget);

      // Select remaining 1 bottle, choose Voucher refund, and submit
      final dropdown = find.byType(DropdownButton<int>);
      await tester.ensureVisible(dropdown);
      await tester.pumpAndSettle();
      await tester.tap(dropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('1').last);
      await tester.pumpAndSettle();

      final reasonField = find.widgetWithText(TextField, 'Lý do đổi trả');
      await tester.ensureVisible(reasonField);
      await tester.pumpAndSettle();
      await tester.enterText(reasonField, 'Chai còn lại bị móp nhẹ');

      final voucherOption = find.text('Nhận Voucher hoàn tiền ngay (dùng 180 ngày)');
      await tester.scrollUntilVisible(
        voucherOption,
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.ensureVisible(voucherOption);
      await tester.pumpAndSettle();
      await tester.tap(voucherOption);
      await tester.pumpAndSettle();

      final submitBtn = find.text('Gửi yêu cầu đổi trả');
      await tester.scrollUntilVisible(
        submitBtn,
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.ensureVisible(submitBtn);
      await tester.pumpAndSettle();
      await tester.tap(submitBtn);
      await tester.pumpAndSettle();

      expect(postedBodies.length, 1);
      expect(postedBodies.first['returnMethod'], 'SHIPPER_PICKUP');
      expect(postedBodies.first['refundMethod'], 'VOUCHER');
      await tester.scrollUntilVisible(
        find.text('#2 · Chờ xét duyệt'),
        -150,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('#2 · Chờ xét duyệt'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Tất cả sản phẩm trong đơn hàng này đã được gửi yêu cầu đổi trả.'),
        150,
        scrollable: find.byType(Scrollable).first,
      );
      expect(
        find.text('Tất cả sản phẩm trong đơn hàng này đã được gửi yêu cầu đổi trả.'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'OrderDetailScreen displays PICKUP_ASSIGNED, PICKED_UP shipper details, and allows SELF_SHIP returnMethod',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 780));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final postedBodies = <Map<String, dynamic>>[];
      var returnList = <Map<String, dynamic>>[
        {
          'id': 11,
          'orderId': 73,
          'status': 'PICKUP_ASSIGNED',
          'returnMethod': 'SHIPPER_PICKUP',
          'reason': 'Hộp bị móp',
          'decisionReason': 'Đã điều shipper thu hồi',
          'pickupShipperName': 'Lê Văn B',
          'pickupShipperPhone': '0988111222',
          'pickupFailureReason': 'Khách hẹn lại ngày mai',
          'refundAmount': 150000,
          'lines': [
            {'orderItemId': 61, 'quantity': 1},
          ],
          'evidenceUrls': [],
          'refundMethod': 'BANK_TRANSFER',
        },
        {
          'id': 12,
          'orderId': 73,
          'status': 'PICKED_UP',
          'returnMethod': 'SHIPPER_PICKUP',
          'reason': 'Sai phân loại',
          'decisionReason': 'Đã phân công shipper thu hồi',
          'pickupShipperName': 'Lê Văn B',
          'pickupShipperPhone': '0988111222',
          'handoverRequestedAt': '2026-11-25T11:30:00Z',
          'refundAmount': 150000,
          'lines': [
            {'orderItemId': 61, 'quantity': 1},
          ],
          'evidenceUrls': [],
          'refundMethod': 'VOUCHER',
        },
      ];

      final api = _ReturnApiStub(
        onGet: (path, _) {
          if (path == '/api/v1/orders/73') {
            return {
              'id': 73,
              'userId': 10,
              'orderNumber': 'ORD-73',
              'status': 'DELIVERED',
              'paymentStatus': 'PAID',
              'customerName': 'Nguyễn Thanh Nam',
              'customerPhone': '0902588750',
              'shippingAddress': '12 Lê Lợi',
              'totalAmount': 450000,
              'paidAmount': 450000,
              'refundedAmount': 0,
              'items': [
                {
                  'id': 61,
                  'productName': 'Serum Vitamin C',
                  'variantName': '30ml',
                  'quantity': 3,
                  'price': 150000,
                  'discount': 0,
                },
              ],
              'statusHistories': [
                {
                  'status': 'DELIVERED',
                  'notes': 'Giao thành công',
                  'createdAt': '2026-11-25T10:00:00Z',
                },
              ],
            };
          }
          if (path == '/api/v1/orders/73/returns') {
            return returnList;
          }
          return {};
        },
        onPost: (path, data, headers) {
          if (path == '/api/v1/orders/73/returns') {
            final map = Map<String, dynamic>.from(data as Map);
            postedBodies.add(map);
            returnList = [
              ...returnList,
              {
                'id': 13,
                'orderId': 73,
                'status': 'REQUESTED',
                'returnMethod': map['returnMethod'],
                'reason': map['reason'],
                'refundAmount': 150000,
                'lines': map['lines'],
                'evidenceUrls': [],
                'refundMethod': map['refundMethod'],
              },
            ];
            return returnList.last;
          }
          return {};
        },
      );

      await tester.pumpWidget(
        MaterialApp(
          home: OrderDetailScreen(orderId: 73, apiClient: api),
        ),
      );
      await tester.pumpAndSettle();

      await tester.scrollUntilVisible(
        find.text('#11 · Đang điều shipper thu hồi'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('#11 · Đang điều shipper thu hồi'), findsOneWidget);
      expect(find.text('Thu hồi hàng: Shipper đến lấy tại nhà'), findsWidgets);
      expect(find.text('Shipper thu hồi: Lê Văn B (0988111222)'), findsWidgets);
      expect(
        find.text('Lý do thu hồi chưa thành công: Khách hẹn lại ngày mai'),
        findsOneWidget,
      );

      await tester.scrollUntilVisible(
        find.text('#12 · Shipper đã lấy hàng hoàn'),
        150,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('#12 · Shipper đã lấy hàng hoàn'), findsOneWidget);
      expect(find.text('Shipper đã báo bàn giao về kho'), findsOneWidget);

      await tester.scrollUntilVisible(
        find.text('Có thể yêu cầu: 1'),
        150,
        scrollable: find.byType(Scrollable).first,
      );
      final dropdown = find.byType(DropdownButton<int>);
      await tester.ensureVisible(dropdown);
      await tester.pumpAndSettle();
      await tester.tap(dropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('1').last);
      await tester.pumpAndSettle();

      final reasonField = find.widgetWithText(TextField, 'Lý do đổi trả');
      await tester.ensureVisible(reasonField);
      await tester.pumpAndSettle();
      await tester.enterText(reasonField, 'Khách tự gửi hàng về shop');

      final selfShipOption = find.text('Tự gửi hàng về kho BeautyShop');
      await tester.scrollUntilVisible(
        selfShipOption,
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(selfShipOption);
      await tester.pumpAndSettle();

      final voucherOption = find.text('Nhận Voucher hoàn tiền ngay (dùng 180 ngày)');
      await tester.scrollUntilVisible(
        voucherOption,
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(voucherOption);
      await tester.pumpAndSettle();

      final submitBtn = find.text('Gửi yêu cầu đổi trả');
      await tester.scrollUntilVisible(
        submitBtn,
        150,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(submitBtn);
      await tester.pumpAndSettle();

      expect(postedBodies.length, 1);
      expect(postedBodies.first['returnMethod'], 'SELF_SHIP');
      expect(tester.takeException(), isNull);
    },
  );
}
