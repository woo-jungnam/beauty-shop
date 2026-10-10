import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/providers/product_provider.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/presentation/widgets/hasaki_product_card.dart';
import 'package:beautyshop_mobile/presentation/screens/product_detail/product_detail_screen.dart';

class FlashDealsScreen extends StatelessWidget {
  const FlashDealsScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.canvas,
      appBar: AppBar(
        title: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.local_fire_department, color: Colors.yellow, size: 22),
            SizedBox(width: 6),
            Text('FLASH DEALS GIỜ VÀNG', style: TextStyle(fontWeight: FontWeight.w900)),
          ],
        ),
        backgroundColor: HasakiColors.dealRed,
        elevation: 0,
      ),
      body: Consumer<ProductProvider>(
        builder: (context, provider, _) {
          final deals = provider.flashSaleProducts.isNotEmpty ? provider.flashSaleProducts : provider.products;

          return Column(
            children: [
              // Hasaki Time Slot Selector
              Container(
                color: HasakiColors.dealRedDark,
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _buildTimeSlot('09:00', 'Đang diễn ra', true),
                    _buildTimeSlot('12:00', 'Sắp diễn ra', false),
                    _buildTimeSlot('20:00', 'Sắp diễn ra', false),
                  ],
                ),
              ),

              // Product Deals Grid
              Expanded(
                child: GridView.builder(
                  padding: const EdgeInsets.all(10),
                  itemCount: deals.length,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    crossAxisSpacing: 8,
                    mainAxisSpacing: 8,
                    childAspectRatio: 0.62,
                  ),
                  itemBuilder: (context, index) {
                    final item = deals[index];
                    return HasakiProductCard(
                      product: item,
                      onTap: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => ProductDetailScreen(productId: item.id),
                          ),
                        );
                      },
                      onAddToCart: () {
                        context.read<CartProvider>().addToCart(
                              productId: item.id,
                              variantId: 0,
                              productName: item.name,
                              variantName: 'Tiêu chuẩn',
                              price: item.displayPrice,
                              thumbnailUrl: item.thumbnailUrl,
                            );
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text('Đã thêm "${item.name}" vào giỏ hàng'),
                            duration: const Duration(seconds: 2),
                            backgroundColor: HasakiColors.primary,
                          ),
                        );
                      },
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildTimeSlot(String time, String status, bool isActive) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      decoration: BoxDecoration(
        color: isActive ? Colors.white : Colors.transparent,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Column(
        children: [
          Text(
            time,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w900,
              color: isActive ? HasakiColors.dealRed : Colors.white,
            ),
          ),
          Text(
            status,
            style: TextStyle(
              fontSize: 10.5,
              fontWeight: FontWeight.w600,
              color: isActive ? HasakiColors.dealRed : Colors.white70,
            ),
          ),
        ],
      ),
    );
  }
}
