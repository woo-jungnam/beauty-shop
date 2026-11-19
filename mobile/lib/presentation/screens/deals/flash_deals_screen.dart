import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/utils/cart_actions.dart';
import '../../../providers/product_provider.dart';
import '../../widgets/app_product_card.dart';
import '../../widgets/catalog_state.dart';
import '../product_detail/product_detail_screen.dart';

enum _DealFilter { all, campaign, clearance }

class FlashDealsScreen extends StatefulWidget {
  const FlashDealsScreen({super.key});

  @override
  State<FlashDealsScreen> createState() => _FlashDealsScreenState();
}

class _FlashDealsScreenState extends State<FlashDealsScreen> {
  _DealFilter _filter = _DealFilter.all;

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ProductProvider>();
    final deals = provider.flashSaleProducts;
    final campaignCount = deals
        .where((row) => row.flashSaleVariant != null)
        .length;
    final clearanceCount = deals.length - campaignCount;
    final filters = {
      _DealFilter.all: 'Tất cả (${deals.length})',
      if (campaignCount > 0) _DealFilter.campaign: 'Giờ vàng ($campaignCount)',
      if (clearanceCount > 0)
        _DealFilter.clearance: 'Khuyến mãi ($clearanceCount)',
    };
    final active = filters.containsKey(_filter) ? _filter : _DealFilter.all;
    final visibleDeals = switch (active) {
      _DealFilter.all => deals,
      _DealFilter.campaign =>
        deals.where((row) => row.flashSaleVariant != null).toList(),
      _DealFilter.clearance =>
        deals.where((row) => row.clearanceVariant != null).toList(),
    };

    return Scaffold(
      appBar: AppBar(title: const Text('Flash Sale giờ vàng')),
      body: provider.isLoading
          ? const Center(child: CircularProgressIndicator())
          : provider.dealsError != null
          ? CatalogState(
              message: provider.dealsError!,
              onRetry: provider.fetchFlashSaleProducts,
            )
          : RefreshIndicator(
              onRefresh: provider.fetchFlashSaleProducts,
              child: CustomScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                slivers: [
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 4),
                    sliver: SliverToBoxAdapter(
                      child: Wrap(
                        spacing: 8,
                        children: [
                          for (final filter in filters.entries)
                            ChoiceChip(
                              label: Text(filter.value),
                              selected: filter.key == active,
                              onSelected: (_) =>
                                  setState(() => _filter = filter.key),
                            ),
                        ],
                      ),
                    ),
                  ),
                  if (visibleDeals.isEmpty)
                    const SliverToBoxAdapter(
                      child: CatalogState(
                        message: 'Hiện chưa có sản phẩm ưu đãi',
                      ),
                    )
                  else
                    SliverPadding(
                      padding: const EdgeInsets.all(12),
                      sliver: SliverGrid.builder(
                        itemCount: visibleDeals.length,
                        gridDelegate:
                            const SliverGridDelegateWithMaxCrossAxisExtent(
                              maxCrossAxisExtent: 250,
                              crossAxisSpacing: 10,
                              mainAxisSpacing: 10,
                              childAspectRatio: 0.55,
                            ),
                        itemBuilder: (context, index) {
                          final product = visibleDeals[index];
                          final variant = product.dealVariant;
                          return AppProductCard(
                            product: product,
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => ProductDetailScreen(
                                  productId: product.id,
                                  initialVariantId: variant?.id,
                                ),
                              ),
                            ),
                            onAddToCart: () => addProductToCart(
                              context,
                              productId: product.id,
                              variantId: variant?.id ?? 0,
                              variantName: variant?.variantName ?? '',
                              productName: product.name,
                              price: product.displayPrice,
                              thumbnailUrl: product.thumbnailUrl,
                            ),
                          );
                        },
                      ),
                    ),
                ],
              ),
            ),
    );
  }
}
