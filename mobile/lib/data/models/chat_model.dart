import 'product_model.dart';

class ChatMessage {
  final String id;
  final String text;
  final bool isUser;
  final DateTime timestamp;
  final List<ProductItem>? recommendedProducts;

  ChatMessage({
    required this.id,
    required this.text,
    required this.isUser,
    required this.timestamp,
    this.recommendedProducts,
  });

  factory ChatMessage.user(String text) {
    return ChatMessage(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      text: text,
      isUser: true,
      timestamp: DateTime.now(),
    );
  }

  factory ChatMessage.bot(String text, {List<ProductItem>? products}) {
    return ChatMessage(
      id: (DateTime.now().millisecondsSinceEpoch + 1).toString(),
      text: text,
      isUser: false,
      timestamp: DateTime.now(),
      recommendedProducts: products,
    );
  }
}
