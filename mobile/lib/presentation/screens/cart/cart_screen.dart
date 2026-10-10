import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/checkout/checkout_screen.dart';

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
      backgroundColor: HasakiColors.canvas,
      appBar: AppBar(
        title: const Text('Giỏ Hàng Của Bạn'),
        backgroundColor: HasakiColors.primary,
        elevation: 0,
      ),
      body: Consumer<CartProvider>(
        builder: (context, cart, _) {
          if (cart.cart.items.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.remove_shopping_cart_outlined, size: 64, color: HasakiColors.textLight),
                  const SizedBox(height: 12),
                  Text('Giỏ hàng chưa có sản phẩm', style: HasakiTypography.titleMedium),
                  const SizedBox(height: 8),
                  Text('Khám phá hàng ngàn mỹ phẩm chính hãng tại BeautyShop', style: HasakiTypography.bodySmall),
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
              // 1. Freeship 2H Progress Banner
              Container(
                color: HasakiColors.primaryLight,
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                child: Row(
                  children: [
                    const Icon(Icons.local_shipping_outlined, color: HasakiColors.primary, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        cart.selectedSubtotal >= 249000
                            ? 'Bạn đã đủ điều kiện Miễn Phí Vận Chuyển 2H!'
                            : 'Mua thêm ${HasakiFormatters.formatCurrency(249000 - cart.selectedSubtotal)} để nhận Freeship',
                        style: const TextStyle(fontSize: 11.5, color: HasakiColors.primaryDark, fontWeight: FontWeight.w700),
                      ),
                    ),
                  ],
                ),
              ),

              // 2. Cart Items List
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

              // 3. Voucher Promo Code Box
              Container(
                color: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _voucherController,
                        decoration: InputDecoration(
                          hintText: 'Nhập mã giảm giá (VD: BEAUTY100)',
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
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
                      onPressed: () {
                        final success = cart.applyVoucher(_voucherController.text);
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(success ? 'Áp dụng mã giảm giá thành công!' : 'Mã giảm giá không hợp lệ'),
                            backgroundColor: success ? HasakiColors.primary : HasakiColors.dealRed,
                          ),
                        );
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: HasakiColors.primary,
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      ),
                      child: const Text('Áp dụng'),
                    ),
                  ],
                ),
              ),

              // 4. Bottom Order Summary & Checkout Button (Hasaki Style)
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.white,
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
                      // Total Amount Column
                      Expanded(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Tổng thanh toán:', style: TextStyle(fontSize: 11.5, color: HasakiColors.textMuted)),
                            Text(
                              HasakiFormatters.formatCurrency(cart.finalTotal),
                              style: HasakiTypography.priceLarge.copyWith(fontSize: 18),
                            ),
                            if (cart.voucherDiscount > 0)
                              Text(
                                'Tiết kiệm: ${HasakiFormatters.formatCurrency(cart.voucherDiscount)}',
                                style: const TextStyle(fontSize: 11, color: HasakiColors.success, fontWeight: FontWeight.w600),
                              ),
                          ],
                        ),
                      ),

                      // Checkout Button
                      ElevatedButton(
                        onPressed: cart.selectedSubtotal > 0
                            ? () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(builder: (_) => const CheckoutScreen()),
                                );
                              }
                            : null,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: HasakiColors.dealRed,
                          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
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

  Widget _buildCartItemCard(BuildContext context, dynamic item, CartProvider cart) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: HasakiColors.borderSubtle),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Checkbox Select Item
          Checkbox(
            value: item.isSelected,
            activeColor: HasakiColors.primary,
            onChanged: (_) => cart.toggleItemSelection(item.id),
          ),

          // Thumbnail
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: SizedBox(
              width: 70,
              height: 70,
              child: item.thumbnailUrl != null && item.thumbnailUrl!.isNotEmpty
                  ? CachedNetworkImage(
                      imageUrl: item.thumbnailUrl!,
                      fit: BoxFit.cover,
                      errorWidget: (_, __, ___) => Container(color: HasakiColors.canvas),
                    )
                  : Container(color: HasakiColors.canvas),
            ),
          ),
          const SizedBox(width: 10),

          // Details & Stepper
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.productName,
                  style: HasakiTypography.bodySmall.copyWith(fontWeight: FontWeight.w700),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                if (item.variantName.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    'Phân loại: ${item.variantName}',
                    style: HasakiTypography.caption.copyWith(color: HasakiColors.textMuted),
                  ),
                ],
                const SizedBox(height: 6),

                // Price and Quantity Stepper
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      HasakiFormatters.formatCurrency(item.price),
                      style: HasakiTypography.priceMedium.copyWith(fontSize: 14),
                    ),

                    // Quantity Stepper [-] 1 [+]
                    Container(
                      decoration: BoxDecoration(
                        border: Border.all(color: HasakiColors.border),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Row(
                        children: [
                          InkWell(
                            onTap: () => cart.updateQuantity(item.id, item.quantity - 1),
                            child: const Padding(
                              padding: EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              child: Text('-', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                            ),
                          ),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            child: Text('${item.quantity}', style: const TextStyle(fontWeight: FontWeight.bold)),
                          ),
                          InkWell(
                            onTap: () => cart.updateQuantity(item.id, item.quantity + 1),
                            child: const Padding(
                              padding: EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              child: Text('+', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
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
