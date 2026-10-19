import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/product_provider.dart';
import '../../../core/utils/cart_actions.dart';
import '../../widgets/catalog_state.dart';
import '../../widgets/hasaki_product_card.dart';
import '../product_detail/product_detail_screen.dart';

class FlashDealsScreen extends StatelessWidget {
  const FlashDealsScreen({super.key});
  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ProductProvider>();
    final deals = provider.flashSaleProducts;
    return Scaffold(
      appBar: AppBar(title: const Text('Xả kho cận hạn · 31–90 ngày')),
      body: provider.isLoading
          ? const Center(child: CircularProgressIndicator())
          : provider.dealsError != null
          ? CatalogState(
              message: provider.dealsError!,
              onRetry: provider.fetchFlashSaleProducts,
            )
          : deals.isEmpty
          ? const CatalogState(message: 'Hiện chưa có sản phẩm ưu đãi')
          : RefreshIndicator(
              onRefresh: provider.fetchFlashSaleProducts,
              child: GridView.builder(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(12),
                itemCount: deals.length,
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  crossAxisSpacing: 10,
                  mainAxisSpacing: 10,
                  childAspectRatio: 0.65,
                ),
                itemBuilder: (context, index) {
                  final product = deals[index];
                  return HasakiProductCard(
                    product: product,
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => ProductDetailScreen(
                          productId: product.id,
                          initialVariantId: product.clearanceVariant?.id,
                        ),
                      ),
                    ),
                    onAddToCart: () => addProductToCart(
                      context,
                      productId: product.id,
                      variantId: product.clearanceVariant?.id ?? 0,
                      variantName: product.clearanceVariant?.variantName ?? '',
                      productName: product.name,
                      price: product.displayPrice,
                      thumbnailUrl: product.thumbnailUrl,
                    ),
                  );
                },
              ),
            ),
    );
  }
}
