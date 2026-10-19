import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:provider/provider.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/core/constants/api_constants.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/providers/product_provider.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'package:beautyshop_mobile/providers/theme_provider.dart';
import 'package:beautyshop_mobile/providers/chatbot_provider.dart';
import 'package:beautyshop_mobile/presentation/widgets/hasaki_product_card.dart';
import 'package:beautyshop_mobile/presentation/widgets/hasaki_flash_deal_card.dart';
import 'package:beautyshop_mobile/presentation/widgets/hasaki_banner_carousel.dart';
import 'package:beautyshop_mobile/presentation/screens/catalog/category_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/home/home_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/profile/profile_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/cart/cart_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/checkout/checkout_screen.dart';

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
  Stream<Map<String, dynamic>> postSse(
    String path, {
    required dynamic data,
  }) async* {
    throw StateError('Stream unavailable');
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Map<String, dynamic> success(dynamic data) => {'status': 200, 'data': data};
Map<String, dynamic> page(List<Map<String, dynamic>> products) =>
    success({'content': products});
Map<String, dynamic> product(int id, String name) => {
  'id': id,
  'name': name,
  'minPrice': 100000,
  'totalSold': 3,
};
Map<String, dynamic> cartData() => success({
  'items': [
    {
      'id': 1,
      'variantId': 10,
      'productName': 'Serum',
      'quantity': 2,
      'price': 100000,
    },
  ],
});

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
    await StorageService.setDeliveryDetails({});
  });

  test(
    'Clearance uses its endpoint and keeps selected SKU and zero admin discount',
    () async {
      final raw = {
        'id': 10,
        'name': 'Clearance serum',
        'slug': 'clearance-serum',
        'minPrice': 0,
        'maxPrice': 0,
        'earliestExpirationDate': '2026-12-01',
        'daysRemaining': 45,
        'clearanceStock': 3,
        'clearanceVariant': {
          'id': 22,
          'sku': 'CLEARANCE-22',
          'variantName': 'Small',
          'price': 100000,
          'discountPrice': 0,
          'stockQuantity': 3,
          'isActive': true,
        },
      };
      var fail = false;
      final provider = ProductProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          expect(path, ApiConstants.expiringSoon);
          expect(query?['thresholdDays'], 90);
          expect(query?.containsKey('onSale'), isFalse);
          if (fail) throw StateError('Offline');
          return success([raw, product(99, 'Not a clearance deal')]);
        }),
      );
      await provider.fetchFlashSaleProducts();
      expect(provider.flashSaleProducts.length, 1);
      final selected = provider.flashSaleProducts.single;
      expect(selected.clearanceVariant!.id, 22);
      expect(selected.displayPrice, 0);
      expect(selected.basePrice, 100000);
      expect(selected.discountPercent, 100);
      expect(HasakiFormatters.calculateDiscountPercent(100000, 0), 100);
      expect(HasakiFormatters.calculateDiscountPercent(100000, 80000), 20);
      expect(HasakiFormatters.calculateDiscountPercent(100000, -1), 0);
      final detail = ProductDetail.fromJson({
        'id': 10,
        'variants': [
          {
            'id': 11,
            'isDefault': true,
            'isActive': true,
            'stockQuantity': 9,
            'price': 200000,
          },
          raw['clearanceVariant'],
        ],
      });
      expect(detail.variantForSelection(22)?.id, 22);
      expect(detail.variantForSelection(999), isNull);
      expect(detail.variantForSelection(null)?.id, 11);
      fail = true;
      await provider.fetchFlashSaleProducts();
      expect(provider.flashSaleProducts, isEmpty);
      expect(provider.dealsError, isNotNull);
    },
  );

  test(
    'Explicit clearance SKU is submitted unchanged and never resolved to default',
    () async {
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          expect(path, ApiConstants.addToCart);
          expect(body['variantId'], 22);
          return success({
            'items': [
              {
                'id': 1,
                'variantId': 22,
                'productName': 'Deal',
                'quantity': 1,
                'price': 0,
              },
            ],
          });
        }),
      );
      expect(
        await cart.addToCart(
          productId: 10,
          variantId: 22,
          productName: 'Deal',
          variantName: 'Small',
          price: 0,
        ),
        isTrue,
      );
      expect(cart.cart.items.single.variantId, 22);
    },
  );

  test(
    'Successful GET without data means no cart, not an error or a stale local cart',
    () async {
      var empty = false;
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient(
          (path, query, body) =>
              empty ? {'status': 200, 'message': 'Success'} : cartData(),
        ),
      );
      await cart.fetchCart();
      expect(cart.cart.items, isNotEmpty);
      empty = true;
      await cart.fetchCart();
      expect(cart.cart.items, isEmpty);
      expect(cart.errorMessage, isNull);
    },
  );

  test('Shipping threshold and fee use current API configuration', () async {
    final cart = CartProvider(
      loadOnCreate: false,
      apiClient: StubApiClient(
        (path, query, body) => path == ApiConstants.publicConfigs
            ? success({
                'shipping.free_threshold': '300000',
                'shipping.default_fee': 42000,
              })
            : cartData(),
      ),
    );
    await cart.fetchCart();
    await cart.fetchShippingPolicy();
    expect(cart.freeShippingThreshold, 300000);
    expect(cart.shippingFee, 42000);
    expect(cart.finalTotal, 242000);
  });

  test(
    'Shipping configuration failure remains unknown and can be retried',
    () async {
      var fail = true;
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          if (fail) throw StateError('Offline');
          return success({
            'shipping.free_threshold': 100000,
            'shipping.default_fee': 99000,
          });
        }),
      );
      await cart.fetchShippingPolicy();
      expect(cart.hasShippingPolicy, isFalse);
      expect(cart.shippingError, isNotNull);
      fail = false;
      await cart.fetchShippingPolicy();
      expect(cart.hasShippingPolicy, isTrue);
    },
  );

  test(
    'Voucher validation uses backend amount and invalidates on selection changes',
    () async {
      await StorageService.setAccessToken('test-token');
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          if (path == ApiConstants.cart) return cartData();
          expect(path, ApiConstants.validateVoucher);
          expect(body, {'code': 'WELCOME', 'orderAmount': 200000.0});
          return success({'code': 'WELCOME', 'discountAmount': 17000});
        }),
      );
      await cart.fetchCart();
      expect(await cart.applyVoucher(' welcome '), isTrue);
      expect(cart.voucherDiscount, 17000);
      cart.toggleItemSelection(1);
      expect(cart.appliedVoucherCode, isNull);
      expect(cart.voucherDiscount, 0);
    },
  );

  test('Guest and rejected voucher cannot produce local discounts', () async {
    var requests = 0;
    final cart = CartProvider(
      loadOnCreate: false,
      apiClient: StubApiClient((path, query, body) {
        if (path == ApiConstants.cart) return cartData();
        requests++;
        return {'status': 400, 'message': 'Mã hết hạn'};
      }),
    );
    await cart.fetchCart();
    expect(await cart.applyVoucher('HASAKI100'), isFalse);
    expect(requests, 0);
    await StorageService.setAccessToken('test-token');
    expect(await cart.applyVoucher('HASAKI100'), isFalse);
    expect(cart.voucherError, 'Mã hết hạn');
    expect(cart.voucherDiscount, 0);
  });

  test('Late voucher response cannot apply to a changed cart', () async {
    await StorageService.setAccessToken('test-token');
    final response = Completer<Map<String, dynamic>>();
    final cart = CartProvider(
      loadOnCreate: false,
      apiClient: StubApiClient(
        (path, query, body) =>
            path == ApiConstants.cart ? cartData() : response.future,
      ),
    );
    await cart.fetchCart();
    final pending = cart.applyVoucher('WELCOME');
    expect(cart.isApplyingVoucher, isTrue);
    cart.toggleItemSelection(1);
    response.complete(success({'code': 'WELCOME', 'discountAmount': 17000}));
    expect(await pending, isFalse);
    expect(cart.appliedVoucherCode, isNull);
    expect(cart.isApplyingVoucher, isFalse);
  });

  test(
    'Category errors and empty categories never use arbitrary products',
    () async {
      var fail = true;
      final provider = ProductProvider(
        loadOnCreate: false,
        apiClient: StubApiClient((path, query, body) {
          if (query?['categoryId'] == 2) {
            if (fail) throw StateError('Offline');
            return page([]);
          }
          return page([product(8, 'Other category')]);
        }),
      );
      await provider.fetchProducts();
      expect(await provider.fetchProductsByCategory(2), isEmpty);
      expect(provider.categoryError(2), isNotNull);
      expect(provider.categoryProductsMap.containsKey(2), isFalse);
      fail = false;
      expect(await provider.fetchProductsByCategory(2, refresh: true), isEmpty);
      expect(provider.categoryError(2), isNull);
      expect(provider.categoryProductsMap[2], isEmpty);
    },
  );

  test(
    'Missing product fields remain unknown instead of invented business facts',
    () {
      final detail = ProductDetail.fromJson({'id': 1});
      expect(detail.skinType, isNull);
      expect(detail.originCountry, isNull);
      expect(detail.hasAlcohol, isNull);
      expect(detail.hasFragrance, isNull);
      expect(detail.averageRating, 0);
      expect(detail.totalSold, 0);
      expect(ProductVariant.fromJson({'id': 1}).stockQuantity, 0);
    },
  );

  test('Failed chatbot requests display a connection failure', () async {
    final chat = ChatbotProvider(
      apiClient: StubApiClient(
        (path, query, body) => throw StateError('Offline'),
      ),
    );
    await chat.sendMessage('Hello');
    expect(chat.messages.last.text, contains('Không thể kết nối'));
    expect(chat.isTyping, isFalse);
  });

  test('Delivery details round trip without a default address', () async {
    expect(StorageService.getDeliveryAddress(), isEmpty);
    await StorageService.setDeliveryDetails({
      'address': '10 Example',
      'ward': 'Ward',
      'district': 'District',
      'city': 'City',
    });
    expect(
      StorageService.getFullDeliveryAddress(),
      '10 Example, Ward, District, City',
    );
    expect(StorageService.getDeliveryDetails()['city'], 'City');
  });

  testWidgets('Product and deal cards never create sales or discounts', (
    tester,
  ) async {
    final item = ProductItem.fromJson({
      'id': 7,
      'name': 'Serum',
      'minPrice': 100000,
    });
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 150,
            height: 250,
            child: HasakiFlashDealCard(product: item, onTap: () {}),
          ),
        ),
      ),
    );
    expect(find.text('-35%'), findsNothing);
    expect(find.textContaining('ĐÃ BÁN'), findsNothing);
    expect(find.text('Chưa có đánh giá'), findsOneWidget);
    expect(find.byType(HasakiProductCard), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Empty banner list never displays sample photos', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: HasakiBannerCarousel(banners: [])),
      ),
    );
    expect(find.byType(PageView), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('Empty category UI never renders unrelated product cards', (
    tester,
  ) async {
    final provider = ProductProvider(
      loadOnCreate: false,
      apiClient: StubApiClient((path, query, body) {
        if (path == ApiConstants.categories)
          return success([
            {'id': 2, 'name': 'Empty category'},
          ]);
        return query?['categoryId'] == 2
            ? page([])
            : page([product(8, 'Unrelated')]);
      }),
    );
    await provider.fetchCategories();
    await provider.fetchProducts();
    await provider.fetchProductsByCategory(2);
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: provider,
        child: const MaterialApp(home: CategoryScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Unrelated'), findsNothing);
    expect(find.text('Danh mục này chưa có sản phẩm'), findsOneWidget);
  });

  testWidgets('Home discovery tabs render their own API results', (
    tester,
  ) async {
    final provider = ProductProvider(
      loadOnCreate: false,
      apiClient: StubApiClient((path, query, body) {
        if (path == ApiConstants.recommendedForYou)
          return success([product(1, 'Recommended serum')]);
        if (query?['onSale'] == true) return page([]);
        if (query?['sort'] == 'totalSold,desc')
          return page([product(2, 'Best seller serum')]);
        return page([product(3, 'New serum')]);
      }),
    );
    await provider.fetchProducts();
    await provider.fetchRecommendedProducts();
    await provider.fetchBestSellingProducts();
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider.value(value: provider),
          ChangeNotifierProvider(
            create: (_) => CartProvider(loadOnCreate: false),
          ),
        ],
        child: const MaterialApp(home: HomeScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Recommended serum'), findsOneWidget);
    await tester.tap(find.text('Bán chạy'));
    await tester.pumpAndSettle();
    expect(find.text('Best seller serum'), findsOneWidget);
    expect(find.text('Recommended serum'), findsNothing);
    await tester.tap(find.text('Mới nhất'));
    await tester.pumpAndSettle();
    expect(find.text('New serum'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Guest profile never displays loyalty balances', (tester) async {
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthProvider()),
          ChangeNotifierProvider(create: (_) => ThemeProvider()),
        ],
        child: const MaterialApp(home: ProfileScreen()),
      ),
    );
    expect(find.text('150 Đ'), findsNothing);
    expect(find.text('Hạng Thành Viên'), findsNothing);
  });

  testWidgets('Cart with a zero-price admin deal can proceed to checkout', (
    tester,
  ) async {
    final cart = CartProvider(
      loadOnCreate: false,
      apiClient: StubApiClient(
        (path, query, body) => path == ApiConstants.publicConfigs
            ? success({
                'shipping.free_threshold': 249000,
                'shipping.default_fee': 25000,
              })
            : success({
                'items': [
                  {
                    'id': 1,
                    'variantId': 22,
                    'productName': 'Free deal',
                    'quantity': 1,
                    'price': 0,
                  },
                ],
              }),
      ),
    );
    await cart.fetchCart();
    await cart.fetchShippingPolicy();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: cart,
        child: const MaterialApp(home: CartScreen()),
      ),
    );
    final button = tester.widget<ElevatedButton>(
      find.widgetWithText(ElevatedButton, 'MUA HÀNG (1)'),
    );
    expect(button.onPressed, isNotNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Cart freeship banner uses the API threshold', (tester) async {
    final cart = CartProvider(
      loadOnCreate: false,
      apiClient: StubApiClient(
        (path, query, body) => path == ApiConstants.publicConfigs
            ? success({
                'shipping.free_threshold': 300000,
                'shipping.default_fee': 42000,
              })
            : cartData(),
      ),
    );
    await cart.fetchCart();
    await cart.fetchShippingPolicy();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: cart,
        child: const MaterialApp(home: CartScreen()),
      ),
    );
    expect(find.textContaining('Mua thêm 100.000'), findsOneWidget);
    expect(find.textContaining('49.000'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Checkout starts with empty address fields and validates them', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthProvider()),
          ChangeNotifierProvider(
            create: (_) => CartProvider(loadOnCreate: false),
          ),
        ],
        child: const MaterialApp(home: CheckoutScreen()),
      ),
    );
    for (final field in tester.widgetList<TextField>(find.byType(TextField))) {
      expect(field.controller!.text, isEmpty);
    }
    final button = find.text('ĐẶT HÀNG NGAY');
    await tester.ensureVisible(button);
    await tester.tap(button);
    await tester.pump();
    expect(find.textContaining('Vui lòng điền đầy đủ'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  test(
    'Logout immediately removes the previously validated discount from totals',
    () async {
      await StorageService.setAccessToken('test-token');
      final cart = CartProvider(
        loadOnCreate: false,
        apiClient: StubApiClient(
          (path, query, body) => path == ApiConstants.cart
              ? cartData()
              : success({'code': 'WELCOME', 'discountAmount': 17000}),
        ),
      );
      await cart.fetchCart();
      expect(await cart.applyVoucher('WELCOME'), isTrue);
      await StorageService.clearAuth();
      expect(cart.appliedVoucherCode, isNull);
      expect(cart.voucherDiscount, 0);
    },
  );
}
