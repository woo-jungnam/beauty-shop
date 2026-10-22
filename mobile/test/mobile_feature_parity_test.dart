import 'package:flutter_test/flutter_test.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/core/constants/api_constants.dart';

void main() {
  group('Order Model Backend Parity Tests', () {
    test('OrderItem correctly parses imageUrl from backend OrderItemResponse', () {
      final jsonWithImageUrl = {
        'id': 101,
        'variantId': 201,
        'productName': 'Kem Chống Nắng',
        'variantName': '50ml',
        'imageUrl': 'https://cdn.beautyshop.com/images/sunscreen.jpg',
        'price': 350000,
        'quantity': 2,
      };

      final item = OrderItem.fromJson(jsonWithImageUrl);
      expect(item.id, 101);
      expect(item.variantId, 201);
      expect(item.thumbnailUrl, 'https://cdn.beautyshop.com/images/sunscreen.jpg');
    });

    test('OrderItem falls back to thumbnailUrl or image if imageUrl is absent', () {
      final jsonLegacy = {
        'id': 102,
        'variantId': 202,
        'productName': 'Serum B5',
        'thumbnailUrl': 'https://cdn.beautyshop.com/images/b5.jpg',
        'price': 250000,
        'quantity': 1,
      };

      final item = OrderItem.fromJson(jsonLegacy);
      expect(item.thumbnailUrl, 'https://cdn.beautyshop.com/images/b5.jpg');
    });

    test('ApiConstants includes searchByImage endpoint', () {
      expect(ApiConstants.searchByImage, '/api/v1/products/search-by-image');
    });

    test('Delivered order items can be matched via variantId set', () {
      final productVariantIds = {201, 202, 203};
      final order = OrderResponse.fromJson({
        'id': 5001,
        'orderNumber': 'ORD-5001',
        'status': 'DELIVERED',
        'items': [
          {
            'id': 1,
            'variantId': 202,
            'productName': 'Kem Chống Nắng',
            'quantity': 1,
            'price': 300000,
          }
        ],
      });

      final hasDelivered = order.status == 'DELIVERED' &&
          order.items.any((i) => i.variantId != null && productVariantIds.contains(i.variantId));
      expect(hasDelivered, isTrue);
    });

    test('ApiConstants baseUrl defaults or connects to https://api.dermascan.world', () {
      ApiConstants.setCustomBaseUrl('https://api.dermascan.world');
      expect(ApiConstants.baseUrl, 'https://api.dermascan.world');
    });
  });
}
