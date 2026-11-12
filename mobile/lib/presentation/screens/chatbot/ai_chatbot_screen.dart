import '../spa/spa_screen.dart';
import '../orders/order_detail_screen.dart';
import '../orders/order_history_screen.dart';
import '../product_detail/product_detail_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import '../../../core/utils/cart_actions.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/core/utils/image_utils.dart';
import 'package:beautyshop_mobile/providers/chatbot_provider.dart';
import 'package:beautyshop_mobile/data/models/chat_model.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';

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
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        backgroundColor: AppColors.primary,
        title: const Column(
          children: [
            Text(
              'Trợ Lý Làm Đẹp AI',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.circle, color: Color(0xFF86EFAC), size: 7),
                SizedBox(width: 4),
                Text(
                  'Tư vấn sản phẩm và dịch vụ',
                  style: TextStyle(fontSize: 10, color: Colors.white70),
                ),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () =>
                context.read<ChatbotProvider>().resetConversation(),
          ),
        ],
      ),
      body: Consumer<ChatbotProvider>(
        builder: (context, chat, _) {
          return Column(
            children: [
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

              if (chat.isTyping)
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 4,
                  ),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.getSurface(context),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: AppColors.borderSubtle),
                        ),
                        child: const Text(
                          'Trợ lý AI đang soạn câu trả lời...',
                          style: TextStyle(
                            fontSize: 11.5,
                            color: AppColors.textMuted,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

              Container(
                height: 40,
                color: AppColors.getSurface(context),
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
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
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: AppColors.primaryLight,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: AppColors.primarySoft),
                        ),
                        child: Center(
                          child: Text(
                            prompt,
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.primary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),

              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 8,
                ),
                color: AppColors.getSurface(context),
                child: SafeArea(
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _messageController,
                          decoration: InputDecoration(
                            hintText:
                                'Hỏi về routine, hoạt chất, liệu trình spa...',
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 14,
                              vertical: 10,
                            ),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                            ),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: const BorderSide(
                                color: AppColors.border,
                              ),
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
                        style: IconButton.styleFrom(
                          backgroundColor: AppColors.primary,
                        ),
                        icon: const Icon(
                          Icons.send,
                          size: 18,
                          color: Colors.white,
                        ),
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
        crossAxisAlignment: msg.isUser
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: msg.isUser
                ? MainAxisAlignment.end
                : MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (!msg.isUser) ...[
                const CircleAvatar(
                  radius: 14,
                  backgroundColor: AppColors.primary,
                  child: Icon(Icons.smart_toy, color: Colors.white, size: 16),
                ),
                const SizedBox(width: 8),
              ],
              Flexible(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 10,
                  ),
                  decoration: BoxDecoration(
                    color: msg.isUser ? AppColors.primary : Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    border: msg.isUser
                        ? null
                        : Border.all(color: AppColors.borderSubtle),
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
                      color: msg.isUser
                          ? Colors.white
                          : AppColors.getTextMain(context),
                      fontSize: 13,
                      height: 1.45,
                    ),
                  ),
                ),
              ),
            ],
          ),

          if (msg.recommendedProducts != null &&
              msg.recommendedProducts!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.only(left: 36),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'GỢI Ý PHÙ HỢP CHO BẠN:',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                      color: AppColors.primary,
                    ),
                  ),
                  const SizedBox(height: 6),
                  ...msg.recommendedProducts!
                      .map((p) => _buildChatProductTile(p))
                      .toList(),
                ],
              ),
            ),
          ],

          if (msg.actions != null && msg.actions!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.only(left: 36),
              child: Wrap(
                spacing: 8,
                runSpacing: 6,
                children: msg.actions!.map((act) => ActionChip(
                  avatar: Icon(
                    act.actionType == 'BOOKING_PROPOSAL'
                        ? Icons.calendar_today
                        : act.actionType == 'VIEW_ORDER'
                            ? Icons.receipt_long
                            : Icons.shopping_bag_outlined,
                    size: 14,
                    color: AppColors.primary,
                  ),
                  label: Text(
                    act.label,
                    style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: AppColors.primary),
                  ),
                  backgroundColor: AppColors.primaryLight,
                  side: const BorderSide(color: AppColors.border),
                  onPressed: () => _handleChatAction(act),
                )).toList(),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildChatProductTile(ProductItem product) {
    return InkWell(
      onTap: () {
        if (product.isService) {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const SpaScreen()),
          );
        } else if (product.id > 0) {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => ProductDetailScreen(
                productId: product.id,
                initialVariantId: product.clearanceVariant?.id,
              ),
            ),
          );
        }
      },
      borderRadius: BorderRadius.circular(8),
      child: Container(
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: AppColors.getSurface(context),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.primaryLight),
        ),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: SizedBox(
                width: 44,
                height: 44,
                child: () {
                  final imgUrl = ImageUtils.resolveUrl(product.thumbnailUrl);
                  return imgUrl != null && imgUrl.isNotEmpty
                      ? CachedNetworkImage(
                          imageUrl: imgUrl,
                          fit: BoxFit.cover,
                          errorWidget: (_, __, ___) =>
                              Container(color: AppColors.canvas),
                        )
                      : Container(
                          color: AppColors.canvas,
                          child: product.isService
                              ? const Icon(
                                  Icons.spa_outlined,
                                  color: AppColors.primary,
                                  size: 20,
                                )
                              : null,
                        );
                }(),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    product.name,
                    style: const TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w700,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    product.isService && (product.durationMinutes ?? 0) > 0
                        ? '${AppFormatters.formatCurrency(product.displayPrice)} • ${product.durationMinutes} phút'
                        : AppFormatters.formatCurrency(product.displayPrice),
                    style: const TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w800,
                      color: AppColors.dealRed,
                    ),
                  ),
                ],
              ),
            ),
            if (product.isService)
              IconButton(
                tooltip: 'Xem dịch vụ Spa',
                icon: const Icon(
                  Icons.calendar_month,
                  size: 18,
                  color: AppColors.primary,
                ),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const SpaScreen()),
                  );
                },
              )
            else if (!product.isOutOfStock)
              IconButton(
                icon: const Icon(
                  Icons.add_shopping_cart,
                  size: 18,
                  color: AppColors.primary,
                ),
                onPressed: () async {
                  await addProductToCart(
                    context,
                    productId: product.id,
                    variantId: product.clearanceVariant?.id ?? 0,
                    productName: product.name,
                    variantName:
                        product.clearanceVariant?.variantName ?? 'Tiêu chuẩn',
                    price: product.displayPrice,
                    thumbnailUrl: product.thumbnailUrl,
                  );
                },
              )
            else
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 6),
                child: Text(
                  'Hết hàng',
                  style: TextStyle(
                    fontSize: 10.5,
                    color: AppColors.textMuted,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  void _handleChatAction(ChatAction act) {
    if (act.actionType == 'ADD_TO_CART') {
      final pId = (act.payload['product_id'] as num?)?.toInt() ?? 0;
      final pName = '${act.payload['product_name'] ?? 'Sản phẩm'}';
      final price = (act.payload['price'] as num?)?.toDouble() ?? 0;
      final img = act.payload['image_url']?.toString();
      addProductToCart(
        context,
        productId: pId,
        variantId: 0,
        variantName: 'Tiêu chuẩn',
        productName: pName,
        price: price,
        thumbnailUrl: img,
      );
    } else if (act.actionType == 'BOOKING_PROPOSAL') {
      Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const SpaScreen()),
      );
    } else if (act.actionType == 'VIEW_ORDER') {
      final orderId = (act.payload['order_id'] as num?)?.toInt();
      if (orderId != null && orderId > 0) {
        Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => OrderDetailScreen(orderId: orderId)),
        );
      } else {
        Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const OrderHistoryScreen()),
        );
      }
    }
  }
}
