import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/constants/api_constants.dart';
import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:beautyshop_mobile/data/models/spa_model.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/providers/chatbot_provider.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';
import 'package:beautyshop_mobile/providers/product_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/chatbot/ai_chatbot_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/orders/order_history_screen.dart';

typedef _Handler = FutureOr<Map<String, dynamic>> Function(
  String method,
  String path,
  Map<String, dynamic>? query,
  dynamic body,
);

class _StubApi implements ApiClient {
  _StubApi(this.handler);
  final _Handler handler;

  @override
  Future<ApiResponse<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async =>
      ApiResponse<T>.fromJson(
        await handler('GET', path, queryParameters, null),
        fromJsonT,
      );

  @override
  Future<ApiResponse<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async =>
      ApiResponse<T>.fromJson(
        await handler('POST', path, queryParameters, data),
        fromJsonT,
      );

  @override
  Future<ApiResponse<T>> put<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async =>
      ApiResponse<T>.fromJson(
        await handler('PUT', path, queryParameters, data),
        fromJsonT,
      );

  @override
  Future<ApiResponse<T>> delete<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async =>
      ApiResponse<T>.fromJson(
        await handler('DELETE', path, queryParameters, data),
        fromJsonT,
      );

  @override
  Stream<Map<String, dynamic>> postSse(
    String path, {
    required dynamic data,
  }) async* {
    throw StateError('SSE unavailable');
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Map<String, dynamic> _ok(dynamic data) => {'status': 200, 'data': data};

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

  group('Phase 1: CartProvider calculations, rollback & session isolation', () {
    test('updateQuantity syncs blended Flash Sale price & membershipDiscount and rolls back on error', () async {
      var failNextPut = false;
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: _StubApi((method, path, query, body) {
          if (method == 'GET' && path == ApiConstants.cart) {
            return _ok({
              'items': [
                {'id': 1, 'variantId': 10, 'productName': 'Serum', 'price': 100000, 'quantity': 1},
              ],
              'membershipDiscountPercentage': 10,
              'estimatedMembershipDiscount': 10000,
              'freeShippingThreshold': 249000,
              'defaultShippingFee': 25000,
            });
          }
          if (method == 'PUT' && path.startsWith(ApiConstants.updateCartItem)) {
            if (failNextPut) {
              throw ApiException(statusCode: 400, message: 'Không đủ hàng trong kho');
            }
            return _ok({
              'items': [
                {'id': 1, 'variantId': 10, 'productName': 'Serum', 'price': 200000, 'quantity': 2},
              ],
              'membershipDiscountPercentage': 10,
              'estimatedMembershipDiscount': 40000,
            });
          }
          return _ok({});
        }),
      );

      await cart.fetchCart();
      expect(cart.selectedSubtotal, 100000);
      expect(cart.membershipDiscount, 10000);

      final ok = await cart.updateQuantity(1, 2);
      expect(ok, isTrue);
      expect(cart.cart.items.single.price, 200000);
      expect(cart.selectedSubtotal, 400000);
      expect(cart.membershipDiscount, 40000);

      failNextPut = true;
      final failed = await cart.updateQuantity(1, 5);
      expect(failed, isFalse);
      expect(cart.cart.items.single.quantity, 2);
      expect(cart.selectedSubtotal, 400000);
      expect(cart.errorMessage, 'Không đủ hàng trong kho');
      cart.dispose();
    });

    test('membershipDiscount recalculates proportionally when unselecting items and clears on logout', () async {
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: _StubApi((method, path, query, body) {
          if (path == ApiConstants.cart) {
            return _ok({
              'items': [
                {'id': 1, 'variantId': 10, 'productName': 'A', 'price': 100000, 'quantity': 1},
                {'id': 2, 'variantId': 20, 'productName': 'B', 'price': 200000, 'quantity': 1},
              ],
              'membershipDiscountPercentage': 10,
              'estimatedMembershipDiscount': 30000,
              'freeShippingThreshold': 249000,
              'defaultShippingFee': 25000,
            });
          }
          return _ok({});
        }),
      );

      await cart.fetchCart();
      expect(cart.membershipDiscount, 30000);

      cart.toggleItemSelection(2);
      expect(cart.selectedSubtotal, 100000);
      expect(cart.membershipDiscount, 10000);
      expect(cart.finalTotal, 100000 + 25000 - 10000);

      cart.toggleItemSelection(1);
      expect(cart.selectedSubtotal, 0);
      expect(cart.membershipDiscount, 0);
      expect(cart.finalTotal, 0);

      await StorageService.clearAuth();
      expect(cart.cart.items, isEmpty);
      expect(cart.membershipDiscount, 0);
      cart.dispose();
    });

    test('addToCart fallback selects in-stock variant over out-of-stock default variant', () async {
      int? submittedVariantId;
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: _StubApi((method, path, query, body) {
          if (method == 'GET' && path == '${ApiConstants.productDetail}55') {
            return _ok({
              'id': 55,
              'variants': [
                {'id': 501, 'isActive': true, 'isDefault': true, 'stockQuantity': 0},
                {'id': 502, 'isActive': true, 'isDefault': false, 'stockQuantity': 7},
              ],
            });
          }
          if (method == 'POST' && path == ApiConstants.addToCart) {
            submittedVariantId = body['variantId'] as int?;
            return _ok({
              'items': [
                {'id': 1, 'variantId': 502, 'productName': 'P', 'price': 100000, 'quantity': 1},
              ],
            });
          }
          return _ok({});
        }),
      );

      final added = await cart.addToCart(
        productId: 55,
        variantId: 0,
        productName: 'P',
        variantName: '',
        price: 100000,
      );
      expect(added, isTrue);
      expect(submittedVariantId, 502);
      cart.dispose();
    });
  });

  group('Phase 2: Catalog pricing, pagination & child categories', () {
    test('ProductItem does not fabricate discount from non-discounted smaller variant', () {
      final item = ProductItem.fromJson({
        'id': 1,
        'name': 'Toner',
        'basePrice': 500000,
        'variants': [
          {'id': 11, 'price': 150000},
          {'id': 12, 'price': 500000},
        ],
      });
      expect(item.salePrice, isNull);
      expect(item.discountPercent, 0);
      expect(item.displayPrice, 500000);
    });

    test('ProductProvider.loadMoreCatalogProducts uses consistent page size and deduplicates', () async {
      final queries = <Map<String, dynamic>?>[];
      final provider = ProductProvider(
        loadOnCreate: false,
        apiClient: _StubApi((method, path, query, body) {
          if (path == ApiConstants.productSearch) {
            queries.add(query);
            final page = query?['page'] as int? ?? 0;
            final size = query?['size'] as int? ?? 60;
            if (page == 0) {
              return _ok({
                'content': List.generate(
                  size,
                  (i) => {'id': i + 1, 'name': 'SP ${i + 1}', 'minPrice': 100000},
                ),
              });
            }
            return _ok({
              'content': [
                {'id': 60, 'name': 'SP 60', 'minPrice': 100000},
                {'id': 61, 'name': 'SP 61', 'minPrice': 100000},
              ],
            });
          }
          return _ok([]);
        }),
      );

      await provider.fetchProducts(size: 60);
      expect(provider.products.length, 60);

      final loaded = await provider.loadMoreCatalogProducts();
      expect(loaded, isTrue);
      expect(queries[1]?['page'], 1);
      expect(queries[1]?['size'], 60);
      expect(provider.products.length, 61);
      expect(provider.products.last.id, 61);
    });

    test('fetchProductsByCategory sends includeChildCategories=true', () async {
      Map<String, dynamic>? capturedQuery;
      final provider = ProductProvider(
        loadOnCreate: false,
        apiClient: _StubApi((method, path, query, body) {
          if (path == ApiConstants.productSearch) {
            capturedQuery = query;
          }
          return _ok({'content': []});
        }),
      );

      await provider.fetchProductsByCategory(12);
      expect(capturedQuery?['categoryId'], 12);
      expect(capturedQuery?['includeChildCategories'], isTrue);
    });
  });

  group('Phase 3: Orders discount & cancel lifecycle', () {
    test('OrderItem computes effectiveUnitPrice subtracting discount', () {
      final item = OrderItem.fromJson({
        'id': 1,
        'productName': 'Serum',
        'price': 500000,
        'discount': 120000,
        'quantity': 2,
      });
      expect(item.effectiveUnitPrice, 380000);
      expect(item.effectiveUnitPrice * item.quantity, 760000);
    });

    testWidgets('OrderHistoryScreen hides cancel for CONFIRMED orders and preserves service tab on error', (tester) async {
      await StorageService.setAccessToken('tok');
      final orderProvider = OrderProvider(
        loadOnCreate: false,
        apiClient: _StubApi((method, path, query, body) {
          if (path == ApiConstants.myOrders) {
            return _ok({
              'content': [
                {
                  'id': 10,
                  'orderNumber': 'ORD-10',
                  'status': 'CONFIRMED',
                  'totalAmount': 380000,
                  'items': [
                    {'id': 1, 'productName': 'Serum', 'price': 500000, 'discount': 120000, 'quantity': 1},
                  ],
                },
                {
                  'id': 11,
                  'orderNumber': 'ORD-11',
                  'status': 'PENDING',
                  'totalAmount': 200000,
                  'items': [],
                },
              ],
            });
          }
          if (method == 'DELETE') {
            return {'status': 400, 'message': 'Không thể hủy'};
          }
          return _ok([]);
        }),
      );
      await orderProvider.fetchOrders();
      expect(await orderProvider.cancelOrder(10), isFalse);
      expect(orderProvider.actionError, 'Không thể hủy');

      await tester.pumpWidget(
        ChangeNotifierProvider.value(
          value: orderProvider,
          child: const MaterialApp(home: OrderHistoryScreen()),
        ),
      );
      await tester.pumpAndSettle();

      // Only PENDING order shows Hủy đơn (1 button, not 2)
      expect(find.text('Hủy đơn'), findsOneWidget);
      // Effective discounted price shown
      expect(find.textContaining('380.000'), findsWidgets);
      orderProvider.dispose();
    });
  });

  group('Phase 4: Spa Appointment.canCancel', () {
    test('Appointment.canCancel returns false if checkedInAt is set or time passed', () {
      final futureDate = DateTime.now().toUtc().add(const Duration(days: 3));
      final dateStr = '${futureDate.year}-${futureDate.month.toString().padLeft(2, '0')}-${futureDate.day.toString().padLeft(2, '0')}';

      final checkedIn = Appointment.fromJson({
        'id': 1,
        'status': 'CONFIRMED',
        'appointmentDate': dateStr,
        'startTime': '10:00:00',
        'checkedInAt': '2026-11-12T03:00:00Z',
      });
      expect(checkedIn.canCancel, isFalse);

      final notCheckedIn = Appointment.fromJson({
        'id': 2,
        'status': 'CONFIRMED',
        'appointmentDate': dateStr,
        'startTime': '10:00 - 11:00',
      });
      expect(notCheckedIn.canCancel, isTrue);
    });
  });

  group('Phase 5: Chatbot fallback actions, service cards & logout reset', () {
    test('ChatbotProvider parses fallback actions, keeps SERVICE targetType, and resets on clearAuth', () async {
      final chat = ChatbotProvider(
        apiClient: _StubApi((method, path, query, body) {
          return _ok({
            'answer': 'Bạn nên đặt dịch vụ chăm sóc da.',
            'products': [
              {
                'id': '99',
                'name': 'Chăm sóc da chuyên sâu',
                'price': 450000,
                'target_type': 'SERVICE',
                'duration_minutes': 60,
              },
            ],
            'actions': [
              {'action_type': 'BOOKING_PROPOSAL', 'label': 'Đặt lịch Spa', 'payload': {}},
            ],
          });
        }),
      );

      await chat.sendMessage('Tư vấn spa');
      expect(chat.messages.length, 3);
      final bot = chat.messages.last;
      expect(bot.recommendedProducts?.single.isService, isTrue);
      expect(bot.recommendedProducts?.single.durationMinutes, 60);
      expect(bot.actions?.single.actionType, 'BOOKING_PROPOSAL');

      await StorageService.clearAuth();
      expect(chat.messages.length, 1);
      chat.dispose();
    });

    testWidgets('AiChatbotScreen renders calendar icon instead of add_shopping_cart for SERVICE items', (tester) async {
      final chat = ChatbotProvider(
        apiClient: _StubApi((method, path, query, body) {
          return _ok({
            'answer': 'Gợi ý dịch vụ:',
            'products': [
              {
                'id': '99',
                'name': 'Chăm sóc da chuyên sâu',
                'price': 450000,
                'target_type': 'SERVICE',
                'duration_minutes': 60,
              },
            ],
          });
        }),
      );
      await chat.sendMessage('spa');

      await tester.pumpWidget(
        ChangeNotifierProvider.value(
          value: chat,
          child: const MaterialApp(home: AiChatbotScreen()),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.add_shopping_cart), findsNothing);
      expect(find.byIcon(Icons.calendar_month), findsOneWidget);
      chat.dispose();
    });
  });
}
