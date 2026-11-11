import 'package:flutter_test/flutter_test.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:beautyshop_mobile/data/models/chat_model.dart';

void main() {
  group('Chatbot Model & Parsing Tests', () {
    test('ProductItem parses chatbot/RAG product with prefixed string id', () {
      final chatbotProduct = {
        'id': 'mysql_164',
        'name': 'Tinh Chất Niacinamide 15% COSRX',
        'brand': 'COSRX',
        'category': 'serum',
        'price': 380000.0,
        'rating': 4.85,
        'totalReviews': 44,
        'totalSold': 94,
        'imageUrl': '/uploads/a90ef412.webp',
        'reasonForRecommendation': 'Đúng danh mục yêu cầu',
      };

      final product = ProductItem.fromJson(chatbotProduct);
      expect(product.id, equals(164));
      expect(product.name, equals('Tinh Chất Niacinamide 15% COSRX'));
      expect(product.brandName, equals('COSRX'));
      expect(product.basePrice, equals(380000.0));
      expect(product.averageRating, equals(4.85));
      expect(product.totalReviews, equals(44));
      expect(product.totalSold, equals(94));
      expect(product.thumbnailUrl, contains('a90ef412.webp'));
      expect(product.shortDescription, equals('Đúng danh mục yêu cầu'));
    });

    test('ChatAction parses successfully', () {
      final actionJson = {
        'action_type': 'ADD_TO_CART',
        'label': 'Thêm vào giỏ',
        'payload': {
          'product_id': '164',
          'price': 380000.0,
        },
      };

      final action = ChatAction.fromJson(actionJson);
      expect(action.actionType, equals('ADD_TO_CART'));
      expect(action.label, equals('Thêm vào giỏ'));
      expect(action.payload['product_id'], equals('164'));
    });
  });
}
