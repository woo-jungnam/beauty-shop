import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../data/models/chat_model.dart';
import '../data/models/product_model.dart';

class ChatbotProvider extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();

  final List<ChatMessage> _messages = [
    ChatMessage.bot(
      'Xin chào! Tôi là Chuyên Viên Tư Vấn Làm Đẹp AI của BeautyShop. Bạn đang tìm kiếm sản phẩm làm đẹp nào hoặc cần gợi ý chu trình dưỡng da?',
    ),
  ];

  bool _isTyping = false;
  String _sessionId = 'mobile_${DateTime.now().millisecondsSinceEpoch}';

  List<ChatMessage> get messages => _messages;
  bool get isTyping => _isTyping;

  final List<String> quickPrompts = [
    'Da dầu nên chọn loại serum nào?',
    'Gợi ý chu trình dưỡng ẩm sáng da',
    'Dịch vụ chăm sóc da thư giãn tại Spa',
    'Kem chống nắng nâng tông nhẹ nhàng',
  ];

  Future<void> sendMessage(String text) async {
    final cleanText = text.trim();
    if (cleanText.isEmpty || _isTyping) return;

    final userMessage = ChatMessage.user(cleanText);
    _messages.add(userMessage);
    _isTyping = true;
    notifyListeners();

    var answer = '';
    List<ProductItem> suggestions = [];
    final botMessageIndex = _messages.length;
    _messages.add(ChatMessage.bot(''));

    try {
      await for (final event in _apiClient.postSse(
        ApiConstants.chatStream,
        data: {'message': cleanText, 'session_id': _sessionId},
      )) {
        switch (event['type']) {
          case 'token':
            answer += event['content']?.toString() ?? '';
            _messages[botMessageIndex] = ChatMessage.bot(answer, products: suggestions.isNotEmpty ? suggestions : null);
            notifyListeners();
            break;
          case 'metadata':
            if (event['products'] is List) {
              suggestions = (event['products'] as List)
                  .map((item) => ProductItem.fromJson(item as Map<String, dynamic>))
                  .toList();
              _messages[botMessageIndex] = ChatMessage.bot(answer, products: suggestions);
              notifyListeners();
            }
            break;
          case 'done':
            if (event['session_id'] != null) {
              _sessionId = event['session_id'].toString();
            }
            break;
        }
      }

      if (answer.isEmpty) {
        throw StateError('Empty stream response');
      }
    } catch (_) {
      // Fallback regular POST or local response
      try {
        final fallbackRes = await _apiClient.post(
          ApiConstants.chat,
          data: {'sessionId': _sessionId, 'message': cleanText},
        );
        final raw = fallbackRes.data;
        if (raw is Map) {
          final replyText = raw['response'] ?? raw['text'] ?? raw['answer'] ?? raw['message'] ?? '';
          if (raw['products'] is List) {
            suggestions = (raw['products'] as List)
                .map((p) => ProductItem.fromJson(p as Map<String, dynamic>))
                .toList();
          }
          _messages[botMessageIndex] = ChatMessage.bot(replyText.toString(), products: suggestions.isNotEmpty ? suggestions : null);
        } else {
          _messages[botMessageIndex] = ChatMessage.bot(
            'Dạ, đối với tình trạng da này, bạn nên ưu tiên làm sạch dịu nhẹ với sữa rửa mặt chuẩn pH 5.5, cấp ẩm phục hồi với Panthenol B5 và bảo vệ da tuyệt đối bằng kem chống nắng phổ rộng.',
          );
        }
      } catch (_) {
        _messages[botMessageIndex] = ChatMessage.bot(
          'Dạ, đối với tình trạng da này, bạn nên ưu tiên làm sạch dịu nhẹ với sữa rửa mặt chuẩn pH 5.5, cấp ẩm phục hồi với Panthenol B5 và bảo vệ da tuyệt đối bằng kem chống nắng phổ rộng.',
        );
      }
    }

    _isTyping = false;
    notifyListeners();
  }

  void resetConversation() {
    _sessionId = 'mobile_${DateTime.now().millisecondsSinceEpoch}';
    _messages.clear();
    _messages.add(
      ChatMessage.bot(
        'Cuộc trò chuyện đã được làm mới. Bạn cần tư vấn về routine chăm sóc da hay dịch vụ Spa nào?',
      ),
    );
    notifyListeners();
  }
}
