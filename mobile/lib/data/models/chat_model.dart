import 'product_model.dart';

class ChatAction {
  final String actionType;
  final String label;
  final Map<String, dynamic> payload;

  ChatAction({
    required this.actionType,
    required this.label,
    required this.payload,
  });

  factory ChatAction.fromJson(Map<String, dynamic> json) {
    return ChatAction(
      actionType: json['actionType']?.toString() ?? json['action_type']?.toString() ?? '',
      label: json['label']?.toString() ?? '',
      payload: json['payload'] is Map ? Map<String, dynamic>.from(json['payload'] as Map) : {},
    );
  }
}

class ChatMessage {
  final String id;
  final String text;
  final bool isUser;
  final DateTime timestamp;
  final List<ProductItem>? recommendedProducts;
  final List<ChatAction>? actions;

  ChatMessage({
    required this.id,
    required this.text,
    required this.isUser,
    required this.timestamp,
    this.recommendedProducts,
    this.actions,
  });

  factory ChatMessage.user(String text) {
    return ChatMessage(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      text: text,
      isUser: true,
      timestamp: DateTime.now(),
    );
  }

  factory ChatMessage.bot(String text, {List<ProductItem>? products, List<ChatAction>? actions}) {
    return ChatMessage(
      id: (DateTime.now().millisecondsSinceEpoch + 1).toString(),
      text: text,
      isUser: false,
      timestamp: DateTime.now(),
      recommendedProducts: products,
      actions: actions,
    );
  }
}
