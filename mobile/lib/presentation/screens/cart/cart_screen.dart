import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/checkout/checkout_screen.dart';
import '../../widgets/catalog_state.dart';

class CartScreen extends StatefulWidget {
  const CartScreen({Key? key}) : super(key: key);

  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  final TextEditingController _voucherController = TextEditingController();

  @override
  void dispose() {
    _voucherController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Giỏ Hàng Của Bạn'),
        backgroundColor: AppColors.primary,
        elevation: 0,
      ),
      body: Consumer<CartProvider>(
        builder: (context, cart, _) {
          if (cart.isLoading && cart.cart.items.isEmpty)
            return const Center(child: CircularProgressIndicator());
          if (cart.errorMessage != null && cart.cart.items.isEmpty)
            return CatalogState(
              message: cart.errorMessage!,
              onRetry: cart.fetchCart,
            );
          if (cart.cart.items.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.remove_shopping_cart_outlined,
                    size: 64,
                    color: AppColors.textLight,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Giỏ hàng chưa có sản phẩm',
                    style: AppTypography.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Khám phá hàng ngàn mỹ phẩm và dịch vụ làm đẹp chính hãng',
                    style: AppTypography.bodySmall,
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Tiếp tục mua sắm'),
                  ),
                ],
              ),
            );
          }

          return Column(
            children: [
              Container(
                color: AppColors.primaryLight,
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 8,
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.local_shipping_outlined,
                      color: AppColors.primary,
                      size: 18,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        !cart.hasShippingPolicy
                            ? cart.shippingError ??
                                  'Đang tải chính sách vận chuyển...'
                            : cart.selectedSubtotal >=
                                  cart.freeShippingThreshold!
                            ? 'Bạn đã đủ điều kiện miễn phí vận chuyển!'
                            : 'Mua thêm ${AppFormatters.formatCurrency(cart.freeShippingThreshold! - cart.selectedSubtotal)} để nhận Freeship',
                        style: const TextStyle(
                          fontSize: 11.5,
                          color: AppColors.primaryDark,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    if (cart.shippingError != null)
                      IconButton(
                        tooltip: 'Thử lại',
                        onPressed: cart.fetchShippingPolicy,
                        icon: const Icon(Icons.refresh),
                      ),
                  ],
                ),
              ),

              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.all(10),
                  itemCount: cart.cart.items.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final item = cart.cart.items[index];
                    return _buildCartItemCard(context, item, cart);
                  },
                ),
              ),

              Container(
                color: AppColors.getSurface(context),
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 10,
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _voucherController,
                        decoration: InputDecoration(
                          hintText: 'Nhập mã giảm giá',
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 8,
                          ),
                          suffixIcon: cart.appliedVoucherCode != null
                              ? IconButton(
                                  icon: const Icon(Icons.close, size: 16),
                                  onPressed: () {
                                    cart.clearVoucher();
                                    _voucherController.clear();
                                  },
                                )
                              : null,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    ElevatedButton(
                      onPressed: cart.isApplyingVoucher
                          ? null
                          : () async {
                              final success = await cart.applyVoucher(
                                _voucherController.text,
                              );
                              if (!context.mounted) return;
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(
                                    success
                                        ? 'Áp dụng mã giảm giá thành công!'
                                        : cart.voucherError ??
                                              'Giỏ hàng đã thay đổi. Vui lòng áp dụng lại mã.',
                                  ),
                                  backgroundColor: success
                                      ? AppColors.primary
                                      : AppColors.dealRed,
                                ),
                              );
                            },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                      ),
                      child: Text(
                        cart.isApplyingVoucher ? 'Đang kiểm tra' : 'Áp dụng',
                      ),
                    ),
                  ],
                ),
              ),

              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppColors.getSurface(context),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.06),
                      blurRadius: 6,
                      offset: const Offset(0, -2),
                    ),
                  ],
                ),
                child: SafeArea(
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Tổng dự kiến:',
                              style: TextStyle(
                                fontSize: 11.5,
                                color: AppColors.textMuted,
                              ),
                            ),
                            Text(
                              cart.hasShippingPolicy
                                  ? AppFormatters.formatCurrency(
                                      cart.finalTotal,
                                    )
                                  : 'Chưa xác định phí giao hàng',
                              style: AppTypography.priceLarge.copyWith(
                                fontSize: 18,
                              ),
                            ),
                            if (cart.voucherDiscount > 0)
                              Text(
                                'Tiết kiệm: ${AppFormatters.formatCurrency(cart.voucherDiscount)}',
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: AppColors.success,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                          ],
                        ),
                      ),

                      ElevatedButton(
                        onPressed:
                            cart.cart.items.any((item) => item.isSelected) &&
                                cart.hasShippingPolicy &&
                                !cart.isApplyingVoucher
                            ? () {
                                if (!cart.allItemsSelected) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(
                                      content: Text(
                                        'Vui lòng chọn tất cả sản phẩm trong giỏ để đặt hàng.',
                                      ),
                                    ),
                                  );
                                  return;
                                }
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) => const CheckoutScreen(),
                                  ),
                                );
                              }
                            : null,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.dealRed,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 24,
                            vertical: 12,
                          ),
                        ),
                        child: Text(
                          'MUA HÀNG (${cart.cart.items.where((i) => i.isSelected).length})',
                          style: const TextStyle(fontWeight: FontWeight.w800),
                        ),
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

  Widget _buildCartItemCard(
    BuildContext context,
    dynamic item,
    CartProvider cart,
  ) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.borderSubtle),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Checkbox(
            value: item.isSelected,
            activeColor: AppColors.primary,
            onChanged: (_) => cart.toggleItemSelection(item.id),
          ),

          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: SizedBox(
              width: 70,
              height: 70,
              child: item.thumbnailUrl != null && item.thumbnailUrl!.isNotEmpty
                  ? CachedNetworkImage(
                      imageUrl: item.thumbnailUrl!,
                      fit: BoxFit.cover,
                      errorWidget: (_, __, ___) =>
                          Container(color: AppColors.canvas),
                    )
                  : Container(color: AppColors.canvas),
            ),
          ),
          const SizedBox(width: 10),

          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.productName,
                  style: AppTypography.bodySmall.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                if (item.variantName.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    'Phân loại: ${item.variantName}',
                    style: AppTypography.caption.copyWith(
                      color: AppColors.textMuted,
                    ),
                  ),
                ],
                const SizedBox(height: 6),

                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      AppFormatters.formatCurrency(item.price),
                      style: AppTypography.priceMedium.copyWith(
                        fontSize: 14,
                      ),
                    ),

                    Container(
                      decoration: BoxDecoration(
                        border: Border.all(color: AppColors.border),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Row(
                        children: [
                          InkWell(
                            onTap: () =>
                                cart.updateQuantity(item.id, item.quantity - 1),
                            child: const Padding(
                              padding: EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 2,
                              ),
                              child: Text(
                                '-',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            child: Text(
                              '${item.quantity}',
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          InkWell(
                            onTap: () =>
                                cart.updateQuantity(item.id, item.quantity + 1),
                            child: const Padding(
                              padding: EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 2,
                              ),
                              child: Text(
                                '+',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
