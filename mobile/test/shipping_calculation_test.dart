import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:beautyshop_mobile/core/constants/api_constants.dart';
import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/data/models/cart_model.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';

typedef ApiHandler =
    FutureOr<Map<String, dynamic>> Function(
      String path,
      Map<String, dynamic>? query,
      dynamic body,
    );

class StubApiClient implements ApiClient {
  final ApiHandler handler;
  StubApiClient(this.handler);

  @override
  Future<ApiResponse<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async => ApiResponse<T>.fromJson(
    await handler(path, queryParameters, null),
    fromJsonT,
  );

  @override
  Future<ApiResponse<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic)? fromJsonT,
  }) async => ApiResponse<T>.fromJson(
    await handler(path, queryParameters, data),
    fromJsonT,
  );

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  Map<String, dynamic> success(dynamic data) => {
    'status': 200,
    'message': 'Success',
    'data': data,
  };

  group('Mobile Shipping Calculation & Fallback Tests', () {
    test('Calculates 25,000 VND shipping fee when subtotal is below threshold (249,000 VND)', () async {
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          if (path == ApiConstants.publicConfigs) {
            return success({
              'shipping.free_threshold': 249000,
              'shipping.default_fee': 25000,
            });
          }
          if (path == ApiConstants.cart) {
            return success({
              'items': [
                {
                  'id': 1,
                  'variantId': 10,
                  'productName': 'Sữa rửa mặt',
                  'price': 150000,
                  'quantity': 1,
                },
              ],
            });
          }
          return success({});
        }),
      );

      await cart.fetchShippingPolicy();
      await cart.fetchCart();

      expect(cart.hasShippingPolicy, isTrue);
      expect(cart.freeShippingThreshold, 249000);
      expect(cart.selectedSubtotal, 150000);
      expect(cart.shippingFee, 25000);
      expect(cart.finalTotal, 175000);
    });

    test('Calculates 0 VND shipping fee when subtotal meets or exceeds threshold', () async {
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          if (path == ApiConstants.publicConfigs) {
            return success({
              'shipping.free_threshold': 249000,
              'shipping.default_fee': 25000,
            });
          }
          if (path == ApiConstants.cart) {
            return success({
              'items': [
                {
                  'id': 1,
                  'variantId': 10,
                  'productName': 'Kem dưỡng cao cấp',
                  'price': 300000,
                  'quantity': 1,
                },
              ],
            });
          }
          return success({});
        }),
      );

      await cart.fetchShippingPolicy();
      await cart.fetchCart();

      expect(cart.hasShippingPolicy, isTrue);
      expect(cart.selectedSubtotal, 300000);
      expect(cart.shippingFee, 0.0);
      expect(cart.finalTotal, 300000);
    });

    test('Safe casting handles dynamic/Object map type without throwing TypeError', () async {
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          if (path == ApiConstants.publicConfigs) {
            final Map<dynamic, dynamic> dynamicMap = <dynamic, dynamic>{
              'shipping.free_threshold': '249000',
              'shipping.default_fee': '25000',
            };
            return success(dynamicMap);
          }
          return success({});
        }),
      );

      await cart.fetchShippingPolicy();
      expect(cart.hasShippingPolicy, isTrue);
      expect(cart.freeShippingThreshold, 249000);
    });

    test('Preserves existing shipping policy if subsequent background refresh fails', () async {
      var fail = false;
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          if (fail) throw StateError('Network timeout');
          return success({
            'shipping.free_threshold': 249000,
            'shipping.default_fee': 25000,
          });
        }),
      );

      await cart.fetchShippingPolicy();
      expect(cart.hasShippingPolicy, isTrue);
      expect(cart.freeShippingThreshold, 249000);

      fail = true;
      await cart.fetchShippingPolicy();
      expect(cart.hasShippingPolicy, isTrue);
      expect(cart.freeShippingThreshold, 249000);
    });

    test('CartResponse parses freeShippingThreshold and defaultShippingFee correctly', () {
      final cartResponse = CartResponse.fromJson({
        'items': [],
        'subtotal': 0,
        'totalPrice': 0,
        'freeShippingThreshold': 249000,
        'defaultShippingFee': 25000,
      });

      expect(cartResponse.freeShippingThreshold, 249000);
      expect(cartResponse.defaultShippingFee, 25000);
    });
  });
}
