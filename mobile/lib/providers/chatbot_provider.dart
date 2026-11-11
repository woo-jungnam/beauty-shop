import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../data/models/chat_model.dart';
import '../data/models/product_model.dart';

class ChatbotProvider extends ChangeNotifier {
  final ApiClient _apiClient;

  ChatbotProvider({ApiClient? apiClient})
    : _apiClient = apiClient ?? ApiClient();

  final List<ChatMessage> _messages = [
    ChatMessage.bot(
      'Xin chào! Tôi là Chuyên Viên Tư Vấn Làm Đẹp AI. Bạn đang tìm kiếm sản phẩm làm đẹp nào hoặc cần gợi ý chu trình dưỡng da?',
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
    List<ChatAction> actions = [];
    final botMessageIndex = _messages.length;
    _messages.add(ChatMessage.bot(''));

    try {
      await for (final event in _apiClient.postSse(
        ApiConstants.chatStream,
        data: {
          'message': cleanText,
          'session_id': _sessionId,
          'sessionId': _sessionId,
        },
      )) {
        switch (event['type']) {
          case 'token':
            answer += event['content']?.toString() ?? '';
            _messages[botMessageIndex] = ChatMessage.bot(
              answer,
              products: suggestions.isNotEmpty ? suggestions : null,
              actions: actions.isNotEmpty ? actions : null,
            );
            notifyListeners();
            break;
          case 'metadata':
            if (event['products'] is List) {
              final productList = <ProductItem>[];
              for (final item in event['products']) {
                if (item is Map) {
                  try {
                    productList.add(
                      ProductItem.fromJson(Map<String, dynamic>.from(item)),
                    );
                  } catch (e) {
                    debugPrint('Chatbot product parse warning: $e');
                  }
                }
              }
              suggestions = productList;
            }
            if (event['actions'] is List) {
              final actionList = <ChatAction>[];
              for (final act in event['actions']) {
                if (act is Map) {
                  try {
                    actionList.add(
                      ChatAction.fromJson(Map<String, dynamic>.from(act)),
                    );
                  } catch (e) {
                    debugPrint('Chatbot action parse warning: $e');
                  }
                }
              }
              actions = actionList;
            }
            _messages[botMessageIndex] = ChatMessage.bot(
              answer,
              products: suggestions.isNotEmpty ? suggestions : null,
              actions: actions.isNotEmpty ? actions : null,
            );
            notifyListeners();
            break;
          case 'done':
            if (event['session_id'] != null) {
              _sessionId = event['session_id'].toString();
            }
            break;
          case 'error':
            final detail = event['detail']?.toString() ?? 'Lỗi dịch vụ AI';
            debugPrint('Chatbot stream error event: $detail');
            if (answer.isEmpty) {
              throw StateError(detail);
            }
            break;
        }
      }

      if (answer.trim().isEmpty) {
        throw StateError('Empty stream response');
      }
    } catch (sseError) {
      debugPrint('Chatbot SSE stream error: $sseError');
      if (answer.trim().isNotEmpty) {
        // Da nhan duoc cau tra loi mot phan hoac toan bo tu stream, giu nguyen khong ghi de loi
        _messages[botMessageIndex] = ChatMessage.bot(
          answer,
          products: suggestions.isNotEmpty ? suggestions : null,
          actions: actions.isNotEmpty ? actions : null,
        );
      } else {
        try {
          final fallbackRes = await _apiClient.post(
            ApiConstants.chat,
            data: {
              'sessionId': _sessionId,
              'session_id': _sessionId,
              'message': cleanText,
            },
          );
          final raw = fallbackRes.data;
          if (fallbackRes.isSuccess && raw is Map) {
            final replyText =
                raw['answer'] ??
                raw['message'] ??
                raw['response'] ??
                raw['text'] ??
                '';
            if (replyText.toString().trim().isEmpty) {
              throw StateError('Empty reply');
            }
            if (raw['products'] is List) {
              final productList = <ProductItem>[];
              for (final p in raw['products']) {
                if (p is Map) {
                  try {
                    productList.add(
                      ProductItem.fromJson(Map<String, dynamic>.from(p)),
                    );
                  } catch (e) {
                    debugPrint('Fallback product parse error: $e');
                  }
                }
              }
              suggestions = productList;
            }
            _messages[botMessageIndex] = ChatMessage.bot(
              replyText.toString(),
              products: suggestions.isNotEmpty ? suggestions : null,
            );
          } else {
            _messages[botMessageIndex] = ChatMessage.bot(
              'Không thể kết nối trợ lý AI. Vui lòng thử gửi lại tin nhắn.',
            );
          }
        } catch (fallbackError) {
          debugPrint('Chatbot fallback error: $fallbackError');
          _messages[botMessageIndex] = ChatMessage.bot(
            'Không thể kết nối trợ lý AI. Vui lòng thử gửi lại tin nhắn.',
          );
        }
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
