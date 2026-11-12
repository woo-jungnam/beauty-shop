import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/cart_actions.dart';
import '../../../core/utils/image_utils.dart';
import '../../../providers/product_provider.dart';
import '../../../data/models/product_model.dart';
import '../../widgets/app_top_bar.dart';
import '../../widgets/app_banner_carousel.dart';
import '../../widgets/app_product_card.dart';
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
  late final ScrollController _scrollController;
  late final ScrollController _brandScrollController;
  final Map<int, int> _visibleCountByTab = {0: 6, 1: 6, 2: 6};
  bool _isLoadingMore = false;
  Timer? _brandMarqueeTimer;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this)..addListener(_onTabChanged);
    _scrollController = ScrollController()..addListener(_onScroll);
    _brandScrollController = ScrollController();
    _startBrandMarquee();
  }

  void _startBrandMarquee() {
    _brandMarqueeTimer?.cancel();
    _brandMarqueeTimer = Timer.periodic(const Duration(milliseconds: 40), (timer) {
      if (!_brandScrollController.hasClients || !mounted) return;
      final max = _brandScrollController.position.maxScrollExtent;
      final current = _brandScrollController.position.pixels;
      if (current >= max) {
        _brandScrollController.jumpTo(0);
      } else {
        _brandScrollController.jumpTo(current + 1);
      }
    });
  }

  void _onScroll() {
    if (!_scrollController.hasClients || _isLoadingMore) return;
    final maxScroll = _scrollController.position.maxScrollExtent;
    final currentScroll = _scrollController.position.pixels;
    if (currentScroll >= maxScroll - 200) {
      _loadMore();
    }
  }

  Future<void> _loadMore() async {
    if (_isLoadingMore) return;
    final tabIndex = _tabs.index;
    final provider = context.read<ProductProvider>();
    final feed = switch (tabIndex) {
      0 => provider.recommendedProducts,
      1 => provider.bestSellingProducts,
      _ => provider.products,
    };
    final currentVisible = _visibleCountByTab[tabIndex] ?? 6;

    if (currentVisible < feed.length) {
      setState(() => _isLoadingMore = true);
      await Future.delayed(const Duration(milliseconds: 250));
      if (!mounted) return;
      setState(() {
        _visibleCountByTab[tabIndex] = currentVisible + 6;
        _isLoadingMore = false;
      });
      return;
    }

    if (tabIndex == 2 && provider.hasMoreCatalog) {
      setState(() => _isLoadingMore = true);
      final loaded = await provider.loadMoreCatalogProducts();
      if (!mounted) return;
      setState(() {
        if (loaded) {
          _visibleCountByTab[tabIndex] = currentVisible + 6;
        }
        _isLoadingMore = false;
      });
    }
  }

  void _onTabChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _brandMarqueeTimer?.cancel();
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    _brandScrollController.dispose();
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

  Widget _buildFlashSaleHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 16, 12, 8),
      child: Row(
        children: [
          const Icon(Icons.local_offer, color: AppColors.dealRed, size: 20),
          const SizedBox(width: 4),
          const Expanded(
            child: Text(
              'XẢ KHO CẬN HẠN · 31–90 NGÀY',
              style: TextStyle(
                fontWeight: FontWeight.w900,
                fontSize: 13.5,
                color: AppColors.dealRed,
                letterSpacing: 0.3,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(width: 8),
          TextButton(
            onPressed: () => widget.onNavigateTab?.call(3),
            style: TextButton.styleFrom(
              padding: EdgeInsets.zero,
              minimumSize: Size.zero,
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
            child: const Text('Xem tất cả', style: TextStyle(fontSize: 12)),
          ),
        ],
      ),
    );
  }

  Widget _buildTrustBadges() {
    return Container(
      margin: const EdgeInsets.fromLTRB(12, 8, 12, 4),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.borderSubtle),
      ),
      child: Row(
        children: [
          _buildTrustItem(Icons.verified_outlined, '100% Chính Hãng'),
          _buildTrustDivider(),
          _buildTrustItem(Icons.local_shipping_outlined, 'Giao Nhanh Freeship'),
          _buildTrustDivider(),
          _buildTrustItem(Icons.spa_outlined, 'Tư Vấn Chuyên Sâu'),
          _buildTrustDivider(),
          _buildTrustItem(Icons.published_with_changes_outlined, 'Đổi Trả Minh Bạch'),
        ],
      ),
    );
  }

  Widget _buildTrustItem(IconData icon, String text) {
    return Expanded(
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 13, color: AppColors.primary),
          const SizedBox(width: 4),
          Flexible(
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 9.5, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTrustDivider() {
    return Container(width: 1, height: 14, color: AppColors.borderSubtle);
  }

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
              color: AppColors.getSurface(context),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppColors.getBorder(context)),
            ),
            child: () {
              final imgUrl = ImageUtils.resolveUrl(url);
              return imgUrl != null && imgUrl.isNotEmpty
                  ? CachedNetworkImage(
                      imageUrl: imgUrl,
                      fit: BoxFit.contain,
                      errorWidget: (_, _, _) =>
                          const Icon(Icons.storefront_outlined),
                    )
                  : const Icon(Icons.spa_outlined);
            }(),
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

  Widget _card(ProductItem product) => AppProductCard(
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
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppTopBar(
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
        backgroundColor: AppColors.primary,
        icon: Stack(
          clipBehavior: Clip.none,
          children: [
            const Icon(Icons.smart_toy_outlined, color: Colors.white, size: 20),
            Positioned(
              right: -2,
              top: -2,
              child: Container(
                width: 7,
                height: 7,
                decoration: const BoxDecoration(
                  color: Color(0xFF22C55E),
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ],
        ),
        label: const Text(
          'Tư vấn AI',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          _visibleCountByTab.clear();
          await provider.loadInitialData();
        },
        child: ListView(
          controller: _scrollController,
          padding: const EdgeInsets.only(bottom: 96),
          children: [
            if (provider.isLoading) const LinearProgressIndicator(),
            const SizedBox(height: 12),
            if (provider.banners.isNotEmpty)
              AppBannerCarousel(
                banners: provider.banners
                    .map((banner) => banner.imageUrl)
                    .toList(),
              ),
            _buildTrustBadges(),
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
                        'includeChildCategories': true,
                      }),
                    );
                  },
                ),
              ),
            ],
            if (provider.dealsError != null) ...[
              _buildFlashSaleHeader(),
              CatalogState(
                message: provider.dealsError!,
                onRetry: provider.fetchFlashSaleProducts,
              ),
            ] else if (provider.flashSaleProducts.isNotEmpty) ...[
              _buildFlashSaleHeader(),
              SizedBox(
                height: 285,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount: provider.flashSaleProducts.take(6).length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (_, index) => SizedBox(
                    width: 152,
                    child: _card(provider.flashSaleProducts[index]),
                  ),
                ),
              ),
            ],
            if (provider.brands.isNotEmpty) ...[
              _title('Thương hiệu đối tác'),
              SizedBox(
                height: 105,
                child: ListView.separated(
                  controller: _brandScrollController,
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
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 250),
                switchInCurve: Curves.easeIn,
                switchOutCurve: Curves.easeOut,
                child: KeyedSubtree(
                  key: ValueKey<int>(_tabs.index),
                  child: Builder(
                    builder: (context) {
                      final currentVisible = _visibleCountByTab[_tabs.index] ?? 6;
                      final visibleProducts = feed.take(currentVisible).toList();
                      final hasMore = currentVisible < feed.length ||
                          (_tabs.index == 2 && provider.hasMoreCatalog);
                      return Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          children: [
                            GridView.builder(
                              shrinkWrap: true,
                              physics: const NeverScrollableScrollPhysics(),
                              itemCount: visibleProducts.length,
                              gridDelegate:
                                  const SliverGridDelegateWithFixedCrossAxisCount(
                                crossAxisCount: 2,
                                crossAxisSpacing: 10,
                                mainAxisSpacing: 10,
                                childAspectRatio: 0.60,
                              ),
                              itemBuilder: (_, index) =>
                                  _card(visibleProducts[index]),
                            ),
                            if (_isLoadingMore)
                              const Padding(
                                padding: EdgeInsets.symmetric(vertical: 16),
                                child: Center(
                                  child: SizedBox(
                                    width: 24,
                                    height: 24,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      color: AppColors.primary,
                                    ),
                                  ),
                                ),
                              )
                            else if (hasMore)
                              Padding(
                                padding: const EdgeInsets.symmetric(vertical: 12),
                                child: OutlinedButton.icon(
                                  onPressed: _loadMore,
                                  icon: const Icon(Icons.expand_more, size: 18),
                                  label: const Text('Xem thêm 6 sản phẩm'),
                                  style: OutlinedButton.styleFrom(
                                    foregroundColor: AppColors.primary,
                                    side: const BorderSide(
                                      color: AppColors.primarySoft,
                                    ),
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(20),
                                    ),
                                  ),
                                ),
                              )
                            else if (feed.isNotEmpty)
                              const Padding(
                                padding: EdgeInsets.symmetric(vertical: 16),
                                child: Center(
                                  child: Text(
                                    'Đã hiển thị tất cả sản phẩm',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: AppColors.textLight,
                                    ),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      );
                    },
                  ),
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
            Icon(icon, color: AppColors.primary),
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
