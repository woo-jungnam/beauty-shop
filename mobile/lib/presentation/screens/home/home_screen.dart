import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/cart_actions.dart';
import '../../../providers/product_provider.dart';
import '../../../data/models/product_model.dart';
import '../../widgets/hasaki_app_bar.dart';
import '../../widgets/hasaki_banner_carousel.dart';
import '../../widgets/hasaki_product_card.dart';
import '../../widgets/catalog_state.dart';
import '../catalog/product_collection_screen.dart';
import '../cart/cart_screen.dart';
import '../chatbot/ai_chatbot_screen.dart';
import '../product_detail/product_detail_screen.dart';

class HomeScreen extends StatefulWidget {
  final Function(int tabIndex)? onNavigateTab;
  const HomeScreen({super.key, this.onNavigateTab});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this)..addListener(_onTabChanged);
  }

  void _onTabChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _tabs.removeListener(_onTabChanged);
    _tabs.dispose();
    super.dispose();
  }

  void _openCollection(String title, Map<String, dynamic> filters) =>
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) =>
              ProductCollectionScreen(title: title, filters: filters),
        ),
      );

  Widget _title(String text, {VoidCallback? onMore}) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 16, 12, 8),
    child: Row(
      children: [
        Expanded(
          child: Text(text, style: Theme.of(context).textTheme.titleMedium),
        ),
        if (onMore != null)
          TextButton(onPressed: onMore, child: const Text('Xem tất cả')),
      ],
    ),
  );

  Widget _logo(String name, String? url, VoidCallback onTap) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(12),
    child: SizedBox(
      width: 92,
      child: Column(
        children: [
          Container(
            width: 56,
            height: 56,
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: HasakiColors.getSurface(context),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: HasakiColors.getBorder(context)),
            ),
            child: url != null && url.isNotEmpty
                ? CachedNetworkImage(
                    imageUrl: url,
                    fit: BoxFit.contain,
                    errorWidget: (_, _, _) =>
                        const Icon(Icons.storefront_outlined),
                  )
                : const Icon(Icons.spa_outlined),
          ),
          const SizedBox(height: 6),
          Text(
            name,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 11),
          ),
        ],
      ),
    ),
  );

  Widget _card(ProductItem product) => HasakiProductCard(
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

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ProductProvider>();
    final feed = switch (_tabs.index) {
      0 => provider.recommendedProducts,
      1 => provider.bestSellingProducts,
      _ => provider.products,
    };
    final feedError = switch (_tabs.index) {
      0 => provider.recommendedError,
      1 => provider.bestSellingError,
      _ => provider.errorMessage,
    };
    return Scaffold(
      backgroundColor: HasakiColors.getCanvas(context),
      appBar: HasakiAppBar(
        onSearchTap: () => widget.onNavigateTab?.call(1),
        onCartTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const CartScreen()),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const AiChatbotScreen()),
        ),
        icon: const Icon(Icons.smart_toy_outlined),
        label: const Text('Tư vấn AI'),
      ),
      body: RefreshIndicator(
        onRefresh: provider.loadInitialData,
        child: ListView(
          padding: const EdgeInsets.only(bottom: 96),
          children: [
            if (provider.isLoading) const LinearProgressIndicator(),
            const SizedBox(height: 12),
            if (provider.banners.isNotEmpty)
              HasakiBannerCarousel(
                banners: provider.banners
                    .map((banner) => banner.imageUrl)
                    .toList(),
              ),
            Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  _action(
                    Icons.search,
                    'Tìm sản phẩm',
                    () => widget.onNavigateTab?.call(1),
                  ),
                  _action(
                    Icons.spa_outlined,
                    'Spa & Làm đẹp',
                    () => widget.onNavigateTab?.call(2),
                  ),
                  _action(
                    Icons.local_offer_outlined,
                    'Ưu đãi',
                    () => widget.onNavigateTab?.call(3),
                  ),
                  _action(
                    Icons.shopping_bag_outlined,
                    'Giỏ hàng',
                    () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const CartScreen()),
                    ),
                  ),
                ],
              ),
            ),
            if (provider.categories.isNotEmpty) ...[
              _title('Danh mục', onMore: () => widget.onNavigateTab?.call(1)),
              SizedBox(
                height: 105,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount: provider.categories.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (_, index) {
                    final category = provider.categories[index];
                    return _logo(
                      category.name,
                      category.displayImage,
                      () => _openCollection(category.name, {
                        'categoryId': category.id,
                      }),
                    );
                  },
                ),
              ),
            ],
            _title(
              'Xả kho cận hạn · 31–90 ngày',
              onMore: () => widget.onNavigateTab?.call(3),
            ),
            if (provider.dealsError != null)
              CatalogState(
                message: provider.dealsError!,
                onRetry: provider.fetchFlashSaleProducts,
              )
            else if (!provider.isLoading && provider.flashSaleProducts.isEmpty)
              const CatalogState(message: 'Hiện chưa có sản phẩm ưu đãi')
            else if (provider.flashSaleProducts.isNotEmpty)
              SizedBox(
                height: 245,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount: provider.flashSaleProducts.take(6).length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (_, index) => SizedBox(
                    width: 145,
                    child: _card(provider.flashSaleProducts[index]),
                  ),
                ),
              ),
            if (provider.brands.isNotEmpty) ...[
              _title('Thương hiệu'),
              SizedBox(
                height: 105,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount: provider.brands.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (_, index) {
                    final brand = provider.brands[index];
                    return _logo(
                      brand.name,
                      brand.logoUrl,
                      () => _openCollection(brand.name, {'brandId': brand.id}),
                    );
                  },
                ),
              ),
            ],
            TabBar(
              controller: _tabs,
              tabs: const [
                Tab(text: 'Gợi ý'),
                Tab(text: 'Bán chạy'),
                Tab(text: 'Mới nhất'),
              ],
            ),
            if (feedError != null)
              CatalogState(
                message: feedError,
                onRetry: provider.loadInitialData,
              )
            else if (!provider.isLoading && feed.isEmpty)
              const CatalogState(message: 'Chưa có sản phẩm trong mục này')
            else
              Padding(
                padding: const EdgeInsets.all(12),
                child: GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: feed.take(12).length,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    crossAxisSpacing: 10,
                    mainAxisSpacing: 10,
                    childAspectRatio: 0.65,
                  ),
                  itemBuilder: (_, index) => _card(feed[index]),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _action(IconData icon, String label, VoidCallback onTap) => Expanded(
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Column(
          children: [
            Icon(icon, color: HasakiColors.primary),
            const SizedBox(height: 8),
            Text(
              label,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 11),
            ),
          ],
        ),
      ),
    ),
  );
}
