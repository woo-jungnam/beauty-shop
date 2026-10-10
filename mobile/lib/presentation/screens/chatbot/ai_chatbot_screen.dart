import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/providers/chatbot_provider.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/data/models/chat_model.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:beautyshop_mobile/presentation/screens/product_detail/product_detail_screen.dart';

class AiChatbotScreen extends StatefulWidget {
  const AiChatbotScreen({Key? key}) : super(key: key);

  @override
  State<AiChatbotScreen> createState() => _AiChatbotScreenState();
}

class _AiChatbotScreenState extends State<AiChatbotScreen> {
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.canvas,
      appBar: AppBar(
        backgroundColor: HasakiColors.primary,
        title: const Column(
          children: [
            Text('Trợ Lý Làm Đẹp AI', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800)),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.circle, color: Color(0xFF86EFAC), size: 7),
                SizedBox(width: 4),
                Text('Trực tuyến 24/7', style: TextStyle(fontSize: 10, color: Colors.white70)),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => context.read<ChatbotProvider>().resetConversation(),
          ),
        ],
      ),
      body: Consumer<ChatbotProvider>(
        builder: (context, chat, _) {
          return Column(
            children: [
              // 1. Chat Messages List
              Expanded(
                child: ListView.builder(
                  controller: _scrollController,
                  padding: const EdgeInsets.all(12),
                  itemCount: chat.messages.length,
                  itemBuilder: (context, index) {
                    final msg = chat.messages[index];
                    return _buildMessageBubble(msg);
                  },
                ),
              ),

              // 2. Typing Indicator
              if (chat.isTyping)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: HasakiColors.borderSubtle),
                        ),
                        child: const Text('Trợ lý AI đang soạn câu trả lời...', style: TextStyle(fontSize: 11.5, color: HasakiColors.textMuted)),
                      ),
                    ],
                  ),
                ),

              // 3. Quick Suggestions Bar
              Container(
                height: 40,
                color: Colors.white,
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  scrollDirection: Axis.horizontal,
                  itemCount: chat.quickPrompts.length,
                  separatorBuilder: (_, __) => const SizedBox(width: 8),
                  itemBuilder: (context, index) {
                    final prompt = chat.quickPrompts[index];
                    return InkWell(
                      onTap: () {
                        chat.sendMessage(prompt);
                        _scrollToBottom();
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: HasakiColors.primaryLight,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: HasakiColors.primarySoft),
                        ),
                        child: Center(
                          child: Text(
                            prompt,
                            style: const TextStyle(fontSize: 11, color: HasakiColors.primary, fontWeight: FontWeight.w700),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),

              // 4. Message Input Row
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                color: Colors.white,
                child: SafeArea(
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _messageController,
                          decoration: InputDecoration(
                            hintText: 'Hỏi về routine, hoạt chất, liệu trình spa...',
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(24)),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: const BorderSide(color: HasakiColors.border),
                            ),
                          ),
                          onSubmitted: (text) {
                            if (text.isNotEmpty) {
                              chat.sendMessage(text);
                              _messageController.clear();
                              _scrollToBottom();
                            }
                          },
                        ),
                      ),
                      const SizedBox(width: 8),
                      IconButton.filled(
                        style: IconButton.styleFrom(backgroundColor: HasakiColors.primary),
                        icon: const Icon(Icons.send, size: 18, color: Colors.white),
                        onPressed: () {
                          if (_messageController.text.isNotEmpty) {
                            chat.sendMessage(_messageController.text);
                            _messageController.clear();
                            _scrollToBottom();
                          }
                        },
                      ),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildMessageBubble(ChatMessage msg) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: msg.isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: msg.isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (!msg.isUser) ...[
                const CircleAvatar(
                  radius: 14,
                  backgroundColor: HasakiColors.primary,
                  child: Icon(Icons.smart_toy, color: Colors.white, size: 16),
                ),
                const SizedBox(width: 8),
              ],
              Flexible(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: msg.isUser ? HasakiColors.primary : Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    border: msg.isUser ? null : Border.all(color: HasakiColors.borderSubtle),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.03),
                        blurRadius: 4,
                        offset: const Offset(0, 1),
                      ),
                    ],
                  ),
                  child: Text(
                    msg.text,
                    style: TextStyle(
                      color: msg.isUser ? Colors.white : HasakiColors.textMain,
                      fontSize: 13,
                      height: 1.45,
                    ),
                  ),
                ),
              ),
            ],
          ),

          // Recommended Products Cards inside chat bubble
          if (msg.recommendedProducts != null && msg.recommendedProducts!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.only(left: 36),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('GỢI Ý PHÙ HỢP CHO BẠN:', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: HasakiColors.primary)),
                  const SizedBox(height: 6),
                  ...msg.recommendedProducts!.map((p) => _buildChatProductTile(p)).toList(),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildChatProductTile(ProductItem product) {
    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: HasakiColors.primaryLight),
      ),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: SizedBox(
              width: 44,
              height: 44,
              child: product.thumbnailUrl != null
                  ? CachedNetworkImage(
                      imageUrl: product.thumbnailUrl!,
                      fit: BoxFit.cover,
                      errorWidget: (_, __, ___) => Container(color: HasakiColors.canvas),
                    )
                  : Container(color: HasakiColors.canvas),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(product.name, style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700), maxLines: 1, overflow: TextOverflow.ellipsis),
                Text(HasakiFormatters.formatCurrency(product.displayPrice), style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w800, color: HasakiColors.dealRed)),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.add_shopping_cart, size: 18, color: HasakiColors.primary),
            onPressed: () {
              context.read<CartProvider>().addToCart(
                    productId: product.id,
                    variantId: 0,
                    productName: product.name,
                    variantName: 'Tiêu chuẩn',
                    price: product.displayPrice,
                    thumbnailUrl: product.thumbnailUrl,
                  );
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Đã thêm vào giỏ hàng'), backgroundColor: HasakiColors.primary),
              );
            },
          ),
        ],
      ),
    );
  }
}
