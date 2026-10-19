import 'dart:async';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';

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
    FlutterSecureStorage.setMockInitialValues({});
    SharedPreferences.setMockInitialValues({});
    await StorageService.init();
  });
  setUp(() async {
    await StorageService.clearAuth();
  });
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
