import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/presentation/screens/orders/order_history_screen.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/presentation/screens/orders/order_invoice.dart';
import 'package:pdf/pdf.dart';

class AccountApiStub implements ApiClient {
  AccountApiStub(this.handler);
  final FutureOr<dynamic> Function(String, Map<String, dynamic>?) handler;
  @override
  Future<ApiResponse<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async => ApiResponse<T>.fromJson({
    'status': 200,
    'data': await handler(path, queryParameters),
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
  test(
    'history search handles order number, phone, product, variant and SKU',
    () {
      final order = OrderResponse.fromJson({
        'id': 7,
        'orderNumber': 'ORD-0017',
        'customerPhone': '0901234567',
        'carrierName': 'Giao Hàng Tiết Kiệm',
        'trackingCode': 'GHTK-1234',
        'cancelReason': 'Khách yêu cầu hủy',
        'refundReference': 'RF-77',
        'items': [
          {
            'id': 10,
            'productName': 'Kem dưỡng',
            'variantName': 'Chai 50 ml',
            'sku': 'KEM-50',
          },
        ],
      });
      for (final query in [
        '  ord-0017 ',
        '1234567',
        'DƯỠNG',
        '50 ml',
        'kem-50',
        'giao hàng tiết kiệm',
        'ghtk-1234',
      ]) {
        expect(order.matchesHistoryQuery(query), isTrue, reason: query);
      }
      expect(order.matchesHistoryQuery('ORD-0001'), isFalse);
      expect(order.cancelReason, 'Khách yêu cầu hủy');
      expect(order.refundReference, 'RF-77');
    },
  );

  test('order invoice renders an offline PDF with the bundled font', () async {
    final order = OrderResponse.fromJson({
      'id': 8,
      'orderNumber': 'ORD-0008',
      'customerName': 'Nguyễn Thị Hạnh',
      'shippingAddress': 'Phường Sài Gòn',
      'totalAmount': 125000,
      'items': [
        {'id': 1, 'productName': 'Sữa rửa mặt', 'price': 125000, 'quantity': 1},
      ],
    });
    final bytes = await buildOrderInvoice(order, PdfPageFormat.a4);
    expect(utf8.decode(bytes.take(5).toList()), '%PDF-');
    expect(bytes.length, greaterThan(1000));
  });

  testWidgets('order history search can find an order on the next page', (
    tester,
  ) async {
    await StorageService.setAccessToken('account-token');
    final pages = <int>[];
    final provider = OrderProvider(
      loadOnCreate: false,
      apiClient: AccountApiStub((_, query) {
        final page = query!['page'] as int;
        pages.add(page);
        return {
          'content': [
            {
              'id': page + 1,
              'orderNumber': page == 0 ? 'ORD-FIRST' : 'ORD-MATCH',
              'status': 'DELIVERED',
            },
          ],
          'totalPages': 2,
          'last': page == 1,
        };
      }),
    );
    addTearDown(provider.dispose);
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: provider,
        child: const MaterialApp(home: OrderHistoryScreen()),
      ),
    );
    await tester.pumpAndSettle();

    final input = find.byType(TextField);
    await tester.enterText(input, 'ord-match');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(pages, [0, 1]);
    expect(find.textContaining('ORD-MATCH'), findsOneWidget);
    expect(find.textContaining('ORD-FIRST'), findsNothing);
  });

  testWidgets(
    'Spa history separates invoices, packages and uninvoiced visits',
    (tester) async {
      await StorageService.setAccessToken('account-token');
      final provider = OrderProvider(
        loadOnCreate: false,
        apiClient: AccountApiStub(
          (_, _) => {
            'content': [
              {'id': 1, 'orderNumber': 'ORD-PRODUCT'},
              {
                'id': 2,
                'orderNumber': 'ORD-VISIT',
                'appointmentId': 22,
                'spaVisitItems': [
                  {'serviceName': 'Massage'},
                ],
              },
              {'id': 3, 'orderNumber': 'ORD-PACKAGE', 'servicePackageId': 77},
            ],
            'totalPages': 1,
            'last': true,
          },
        ),
      );
      addTearDown(provider.dispose);
      await tester.pumpWidget(
        ChangeNotifierProvider.value(
          value: provider,
          child: MaterialApp(
            home: OrderHistoryScreen(
              initialTabIndex: 5,
              serviceOrdersApi: AccountApiStub((path, _) {
                expect(path, '/api/v1/appointments/my-service-orders');
                return {
                  'content': [
                    {
                      'appointmentId': 91,
                      'services': ['Facial'],
                    },
                  ],
                  'totalPages': 1,
                  'last': true,
                };
              }),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Hóa đơn tại quầy'));
      await tester.pumpAndSettle();
      expect(find.text('ORD-VISIT'), findsOneWidget);
      expect(find.text('ORD-PACKAGE'), findsNothing);
      expect(find.text('ORD-PRODUCT'), findsNothing);

      await tester.tap(find.text('Gói liệu trình & Lộ trình'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), '77');
      await tester.pumpAndSettle();
      expect(find.text('ORD-PACKAGE'), findsOneWidget);

      await tester.enterText(find.byType(TextField), '91');
      await tester.tap(find.text('Chưa lập hóa đơn'));
      await tester.pumpAndSettle();
      expect(find.textContaining('LH-91'), findsOneWidget);
    },
  );

  test(
    'checkout drafts remain isolated when accounts change on the same device',
    () async {
      await StorageService.setAccessToken('a');
      await StorageService.saveUserData(jsonEncode({'id': 1}));
      await StorageService.setDeliveryDetails({
        'city': 'Đồng Nai',
        'address': '12 Đường Hoa',
      });
      await StorageService.setAccessToken('b');
      await StorageService.saveUserData(jsonEncode({'id': 2}));
      expect(StorageService.getDeliveryAddress(), isEmpty);
      await StorageService.setDeliveryDetails({
        'city': 'Huế',
        'address': '10 Đường Sông',
      });
      await StorageService.setAccessToken('a2');
      await StorageService.saveUserData(jsonEncode({'id': 1}));
      expect(StorageService.getDeliveryDetails()['city'], 'Đồng Nai');
      expect(StorageService.getDeliveryAddress(), '12 Đường Hoa');
    },
  );
  test(
    'orders load subsequent pages and refresh starts again from page zero',
    () async {
      await StorageService.setAccessToken('account-token');
      final requested = <int>[];
      final provider = OrderProvider(
        loadOnCreate: false,
        apiClient: AccountApiStub((path, query) {
          expect(path, '/api/v1/orders/my-orders');
          final page = query!['page'] as int;
          requested.add(page);
          return {
            'content': List.generate(
              page == 0 ? 20 : 1,
              (index) => {
                'id': page * 20 + index + 1,
                'orderNumber': 'ORD-${page * 20 + index + 1}',
                'status': 'DELIVERED',
                'totalAmount': 100000,
              },
            ),
            'totalPages': 2,
            'totalElements': 21,
          };
        }),
      );
      await provider.fetchOrders();
      expect(provider.orders.length, 20);
      expect(provider.hasMore, isTrue);
      await provider.loadMore();
      expect(provider.orders.length, 21);
      expect(provider.orders.last.id, 21);
      expect(provider.hasMore, isFalse);
      await provider.fetchOrders();
      expect(provider.orders.length, 20);
      expect(requested, [0, 1, 0]);
      provider.dispose();
    },
  );
  test('latest tab wins and refreshing preserves its status filter', () async {
    await StorageService.setAccessToken('account-token');
    final first = Completer<dynamic>();
    final filters = <String?>[];
    final provider = OrderProvider(
      loadOnCreate: false,
      apiClient: AccountApiStub((_, query) {
        final status = query?['status'] as String?;
        filters.add(status);
        if (status == 'SHIPPED') return first.future;
        return {
          'content': List.generate(
            20,
            (index) => {'id': index + 10, 'status': status},
          ),
          'totalPages': 1,
          'last': true,
        };
      }),
    );
    addTearDown(provider.dispose);
    final shipped = provider.fetchOrders(status: 'SHIPPED');
    await provider.fetchOrders(status: 'DELIVERED');
    await provider.refreshOrders();
    first.complete({
      'content': [
        {'id': 1, 'status': 'SHIPPED'},
      ],
    });
    await shipped;

    expect(filters, ['SHIPPED', 'DELIVERED', 'DELIVERED']);
    expect(provider.orders.map((row) => row.status).toSet(), {'DELIVERED'});
    expect(provider.hasMore, isFalse);
  });

  test(
    'QR is offered only for pending bank payments within their deadline',
    () {
      bool canPay(
        String method,
        String paymentStatus,
        String status, [
        String? deadline,
      ]) => OrderResponse.fromJson({
        'id': 1,
        'paymentMethod': method,
        'paymentStatus': paymentStatus,
        'status': status,
        'paymentDeadline': deadline,
      }).canPayByQr;

      expect(
        canPay('BANK', 'PENDING', 'PENDING', '2099-01-01T00:00:00Z'),
        isTrue,
      );
      expect(
        canPay('BANK', 'PENDING', 'PENDING', '2000-01-01T00:00:00Z'),
        isFalse,
      );
      expect(canPay('BANK', 'REFUNDED', 'DELIVERED'), isFalse);
      expect(canPay('BANK', 'PENDING', 'CANCELLED'), isFalse);
      expect(canPay('COD', 'PENDING', 'PENDING'), isFalse);
    },
  );

  test(
    'guest history reads known owner-protected order IDs without calling account listing',
    () async {
      await StorageService.rememberGuestOrder(90);
      final paths = <String>[];
      final provider = OrderProvider(
        loadOnCreate: false,
        apiClient: AccountApiStub((path, query) {
          paths.add(path);
          return {
            'id': 90,
            'orderNumber': 'ORD-90',
            'status': 'PENDING',
            'totalAmount': 100000,
          };
        }),
      );
      await provider.fetchOrders();
      expect(paths, contains('/api/v1/orders/90'));
      expect(paths, isNot(contains('/api/v1/orders/my-orders')));
      expect(provider.orders.single.id, 90);
      provider.dispose();
    },
  );
  test(
    'a late order response cannot populate a different authenticated session',
    () async {
      await StorageService.setAccessToken('old-account');
      final response = Completer<dynamic>();
      final provider = OrderProvider(
        loadOnCreate: false,
        apiClient: AccountApiStub((path, query) => response.future),
      );
      final pending = provider.fetchOrders();
      await StorageService.setAccessToken('new-account');
      response.complete({
        'content': [
          {'id': 1, 'status': 'PAID', 'totalAmount': 100000},
        ],
        'totalPages': 1,
      });
      await pending;
      expect(provider.orders, isEmpty);
      provider.dispose();
    },
  );
}
