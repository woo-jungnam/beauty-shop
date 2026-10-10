import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/providers/product_provider.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/presentation/widgets/hasaki_banner_carousel.dart';
import 'package:beautyshop_mobile/presentation/widgets/hasaki_product_card.dart';
import 'package:beautyshop_mobile/presentation/widgets/hasaki_flash_deal_card.dart';
import 'package:beautyshop_mobile/presentation/widgets/hasaki_app_bar.dart';
import 'package:beautyshop_mobile/presentation/screens/product_detail/product_detail_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/deals/flash_deals_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/cart/cart_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/chatbot/ai_chatbot_screen.dart';

class HomeScreen extends StatefulWidget {
  final Function(int tabIndex)? onNavigateTab;

  const HomeScreen({Key? key, this.onNavigateTab}) : super(key: key);

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  String _selectedIngredient = 'Niacinamide';

  final List<String> _ingredients = const [
    'Niacinamide',
    'B5 Panthenol',
    'Retinol',
    'Salicylic Acid',
    'Vitamin C',
    'Hyaluronic Acid',
  ];

  final List<Map<String, dynamic>> _quickActions = const [
    {'title': 'Deal Giờ Vàng', 'icon': Icons.flash_on, 'color': HasakiColors.dealRed},
    {'title': 'Spa & Làm Đẹp', 'icon': Icons.spa, 'color': HasakiColors.primary},
    {'title': 'Dưỡng Sáng Da', 'icon': Icons.face_retouching_natural_outlined, 'color': Color(0xFF0284C7)},
    {'title': 'Bán Chạy', 'icon': Icons.local_fire_department, 'color': Color(0xFFEA580C)},
    {'title': 'Chính Hãng', 'icon': Icons.verified, 'color': HasakiColors.gold},
    {'title': 'Thành Phần Hot', 'icon': Icons.auto_awesome, 'color': Color(0xFF7C3AED)},
    {'title': 'Thương Hiệu', 'icon': Icons.storefront, 'color': HasakiColors.primary},
    {'title': 'Voucher 100K', 'icon': Icons.confirmation_number, 'color': HasakiColors.dealRed},
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Widget _getCategoryFallbackIcon(String name) {
    final lower = name.toLowerCase();
    IconData icon = Icons.spa_outlined;
    if (lower.contains('rửa mặt') || lower.contains('làm sạch')) {
      icon = Icons.clean_hands_outlined;
    } else if (lower.contains('chống nắng')) {
      icon = Icons.wb_sunny_outlined;
    } else if (lower.contains('dưỡng') || lower.contains('kem')) {
      icon = Icons.spa_outlined;
    } else if (lower.contains('son') || lower.contains('môi')) {
      icon = Icons.favorite_border;
    } else if (lower.contains('mắt') || lower.contains('mi')) {
      icon = Icons.visibility_outlined;
    } else if (lower.contains('tẩy trang')) {
      icon = Icons.water_drop_outlined;
    } else if (lower.contains('tóc') || lower.contains('gội')) {
      icon = Icons.content_cut_outlined;
    } else if (lower.contains('nước hoa')) {
      icon = Icons.bubble_chart_outlined;
    } else if (lower.contains('serum') || lower.contains('tinh chất')) {
      icon = Icons.science_outlined;
    }
    return Container(
      color: HasakiColors.primaryLight,
      child: Center(
        child: Icon(icon, size: 22, color: HasakiColors.primary),
      ),
    );
  }

  void _showVoucherDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.confirmation_number, color: HasakiColors.dealRed),
            SizedBox(width: 8),
            Text('Voucher Giảm 100K', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Nhập mã voucher sau tại giỏ hàng để được giảm 100.000đ cho đơn hàng từ 500.000đ:'),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: HasakiColors.dealRedLight,
                border: Border.all(color: HasakiColors.dealRedBorder),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('HASAKI100', style: TextStyle(fontWeight: FontWeight.w900, color: HasakiColors.dealRed, fontSize: 16)),
                  Text('HSD: 30 ngày', style: TextStyle(fontSize: 11, color: HasakiColors.textMuted)),
                ],
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              context.read<CartProvider>().applyVoucher('HASAKI100');
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Đã lưu mã voucher HASAKI100 vào giỏ hàng'),
                  backgroundColor: HasakiColors.primary,
                ),
              );
            },
            child: const Text('ÁP DỤNG NGAY', style: TextStyle(fontWeight: FontWeight.w800, color: HasakiColors.primary)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.canvas,
      appBar: HasakiAppBar(
        onSearchTap: () => widget.onNavigateTab?.call(1),
        onCartTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const CartScreen()),
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const AiChatbotScreen()),
          );
        },
        backgroundColor: HasakiColors.primary,
        icon: const Icon(Icons.smart_toy, color: Colors.white, size: 20),
        label: const Text(
          'Hỏi AI Da Liễu',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12),
        ),
      ),
      body: Consumer<ProductProvider>(
        builder: (context, productProvider, child) {
        if (productProvider.isLoading && productProvider.products.isEmpty) {
          return const Center(child: CircularProgressIndicator(color: HasakiColors.primary));
        }

        final bannerUrls = productProvider.banners.map((b) => b.imageUrl).toList();
        final ingredientProducts = productProvider.filterByActiveIngredient(_selectedIngredient);

        return RefreshIndicator(
          color: HasakiColors.primary,
          onRefresh: () => productProvider.loadInitialData(),
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 10),

                // 1. Hero Banner Carousel
                HasakiBannerCarousel(banners: bannerUrls),

                const SizedBox(height: 14),

                // 2. Hasaki Quick Circular Icon Tiles (2 rows x 4 items)
                Container(
                  color: HasakiColors.getSurface(context),
                  padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 8),
                  child: GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: _quickActions.length,
                    gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 4,
                      mainAxisSpacing: 12,
                      crossAxisSpacing: 8,
                      childAspectRatio: 0.95,
                    ),
                    itemBuilder: (context, index) {
                      final item = _quickActions[index];
                      return InkWell(
                        onTap: () {
                          if (index == 0) {
                            Navigator.push(
                              context,
                              MaterialPageRoute(builder: (_) => const FlashDealsScreen()),
                            );
                          } else if (index == 1) {
                            widget.onNavigateTab?.call(2); // Spa Tab
                          } else if (index == 7) {
                            _showVoucherDialog(context);
                          } else {
                            widget.onNavigateTab?.call(1); // Category Tab
                          }
                        },
                        borderRadius: BorderRadius.circular(10),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Container(
                              width: 44,
                              height: 44,
                              decoration: BoxDecoration(
                                color: (item['color'] as Color).withOpacity(0.12),
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                item['icon'] as IconData,
                                color: item['color'] as Color,
                                size: 22,
                              ),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              item['title'] as String,
                              style: HasakiTypography.caption.copyWith(
                                color: HasakiColors.getTextMain(context),
                                fontWeight: FontWeight.w600,
                                fontSize: 11,
                              ),
                              textAlign: TextAlign.center,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ),

                const SizedBox(height: 10),

                // 2b. Featured Categories with Image / Logo
                if (productProvider.categories.isNotEmpty)
                  Container(
                    color: HasakiColors.getSurface(context),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                'DANH MỤC NỔI BẬT',
                                style: HasakiTypography.titleMedium.copyWith(
                                  fontWeight: FontWeight.w800,
                                  color: HasakiColors.getTextMain(context),
                                ),
                              ),
                              InkWell(
                                onTap: () => widget.onNavigateTab?.call(1),
                                child: Row(
                                  children: [
                                    Text(
                                      'Xem tất cả',
                                      style: HasakiTypography.caption.copyWith(
                                        color: HasakiColors.primary,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                    const Icon(Icons.chevron_right, size: 14, color: HasakiColors.primary),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 10),
                        SizedBox(
                          height: 84,
                          child: ListView.separated(
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            scrollDirection: Axis.horizontal,
                            itemCount: productProvider.categories.take(10).length,
                            separatorBuilder: (_, __) => const SizedBox(width: 10),
                            itemBuilder: (context, index) {
                              final cat = productProvider.categories[index];
                              return InkWell(
                                onTap: () {
                                  context.read<ProductProvider>().fetchProductsByCategory(cat.id);
                                  widget.onNavigateTab?.call(1);
                                },
                                child: SizedBox(
                                  width: 62,
                                  child: Column(
                                    children: [
                                      Container(
                                        width: 48,
                                        height: 48,
                                        decoration: BoxDecoration(
                                          shape: BoxShape.circle,
                                          color: HasakiColors.canvas,
                                          border: Border.all(color: HasakiColors.borderSubtle),
                                        ),
                                        child: ClipOval(
                                          child: cat.displayImage != null && cat.displayImage!.isNotEmpty
                                              ? CachedNetworkImage(
                                                  imageUrl: cat.displayImage!,
                                                  fit: BoxFit.cover,
                                                  placeholder: (_, __) => Container(color: HasakiColors.canvas),
                                                  errorWidget: (_, __, ___) => _getCategoryFallbackIcon(cat.name),
                                                )
                                              : _getCategoryFallbackIcon(cat.name),
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        cat.name,
                                        style: TextStyle(
                                          fontSize: 10,
                                          fontWeight: FontWeight.w600,
                                          color: HasakiColors.getTextMain(context),
                                          height: 1.15,
                                        ),
                                        textAlign: TextAlign.center,
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ],
                                  ),
                                ),
                              );
                            },
                          ),
                        ),
                      ],
                    ),
                  ),

                const SizedBox(height: 10),

                // 3. Flash Sale Deal Giờ Vàng (Hasaki Scarcity Module)
                Container(
                  color: HasakiColors.getSurface(context),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Row(
                          children: [
                            const Icon(Icons.local_fire_department, color: HasakiColors.dealRed, size: 20),
                            const SizedBox(width: 4),
                            Text(
                              'FLASH DEALS',
                              style: HasakiTypography.titleMedium.copyWith(
                                color: HasakiColors.dealRed,
                                fontWeight: FontWeight.w900,
                                fontSize: 13,
                              ),
                            ),
                            const SizedBox(width: 8),

                            // Isolated 1-second Countdown Widget (prevents full screen rebuild)
                            const FlashSaleCountdownWidget(),

                            const Spacer(),

                            InkWell(
                              onTap: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(builder: (_) => const FlashDealsScreen()),
                                );
                              },
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    'Tất cả',
                                    style: HasakiTypography.caption.copyWith(
                                      color: HasakiColors.primary,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  const Icon(Icons.chevron_right, size: 14, color: HasakiColors.primary),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 10),

                      SizedBox(
                        height: 200,
                        child: ListView.separated(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          scrollDirection: Axis.horizontal,
                          itemCount: (productProvider.flashSaleProducts.isNotEmpty
                                  ? productProvider.flashSaleProducts
                                  : productProvider.products)
                              .take(6)
                              .length,
                          separatorBuilder: (_, __) => const SizedBox(width: 8),
                          itemBuilder: (context, index) {
                            final deals = productProvider.flashSaleProducts.isNotEmpty
                                ? productProvider.flashSaleProducts
                                : productProvider.products;
                            final product = deals[index];
                            return HasakiFlashDealCard(
                              product: product,
                              onTap: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) => ProductDetailScreen(productId: product.id),
                                  ),
                                );
                              },
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 10),

                // 4. Official Brand Mall (Thương Hiệu Chính Hãng)
                if (productProvider.brands.isNotEmpty)
                  Container(
                    color: HasakiColors.getSurface(context),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                'THƯƠNG HIỆU CHÍNH HÃNG',
                                style: HasakiTypography.titleMedium.copyWith(
                                  fontWeight: FontWeight.w800,
                                  color: HasakiColors.getTextMain(context),
                                ),
                              ),
                              Text(
                                '100% Chính Hãng',
                                style: HasakiTypography.caption.copyWith(
                                  color: HasakiColors.primary,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 10),
                        SizedBox(
                          height: 84,
                          child: ListView.separated(
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            scrollDirection: Axis.horizontal,
                            itemCount: productProvider.brands.take(10).length,
                            separatorBuilder: (_, __) => const SizedBox(width: 8),
                            itemBuilder: (context, index) {
                              final brand = productProvider.brands[index];
                              return InkWell(
                                onTap: () => widget.onNavigateTab?.call(1),
                                borderRadius: BorderRadius.circular(8),
                                child: Container(
                                  width: 84,
                                  padding: const EdgeInsets.all(6),
                                  decoration: BoxDecoration(
                                    color: HasakiColors.getSurface(context),
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(color: HasakiColors.getBorder(context)),
                                  ),
                                  child: Column(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      Expanded(
                                        child: (brand.logoUrl != null && brand.logoUrl!.isNotEmpty)
                                            ? CachedNetworkImage(
                                                imageUrl: brand.logoUrl!,
                                                fit: BoxFit.contain,
                                                placeholder: (_, __) => Container(
                                                  color: HasakiColors.canvas,
                                                  child: const Icon(Icons.storefront, size: 20, color: HasakiColors.textLight),
                                                ),
                                                errorWidget: (_, __, ___) => Container(
                                                  decoration: BoxDecoration(
                                                    color: HasakiColors.primaryLight,
                                                    borderRadius: BorderRadius.circular(4),
                                                  ),
                                                  alignment: Alignment.center,
                                                  child: Text(
                                                    brand.name.isNotEmpty ? brand.name[0].toUpperCase() : 'B',
                                                    style: const TextStyle(
                                                      color: HasakiColors.primary,
                                                      fontWeight: FontWeight.w800,
                                                      fontSize: 15,
                                                    ),
                                                  ),
                                                ),
                                              )
                                            : Container(
                                                decoration: BoxDecoration(
                                                  color: HasakiColors.primaryLight,
                                                  borderRadius: BorderRadius.circular(4),
                                                ),
                                                alignment: Alignment.center,
                                                child: Text(
                                                  brand.name.isNotEmpty ? brand.name[0].toUpperCase() : 'B',
                                                  style: const TextStyle(
                                                    color: HasakiColors.primary,
                                                    fontWeight: FontWeight.w800,
                                                    fontSize: 15,
                                                  ),
                                                ),
                                              ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        brand.name,
                                        style: TextStyle(
                                          fontSize: 9.5,
                                          fontWeight: FontWeight.w700,
                                          color: HasakiColors.getTextMain(context),
                                        ),
                                        textAlign: TextAlign.center,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ],
                                  ),
                                ),
                              );
                            },
                          ),
                        ),
                      ],
                    ),
                  ),

                const SizedBox(height: 10),

                // 5. Active Ingredients Spotlight (Thành Phần Dưỡng Da Nổi Bật)
                Container(
                  color: HasakiColors.getSurface(context),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Row(
                          children: [
                            const Icon(Icons.auto_awesome, color: HasakiColors.primary, size: 20),
                            const SizedBox(width: 6),
                            Text(
                              'THÀNH PHẦN DƯỠNG DA NỔI BẬT',
                              style: HasakiTypography.titleMedium.copyWith(
                                fontWeight: FontWeight.w800,
                                color: HasakiColors.getTextMain(context),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 8),
                      SizedBox(
                        height: 40,
                        child: ListView.separated(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          scrollDirection: Axis.horizontal,
                          itemCount: _ingredients.length,
                          separatorBuilder: (_, __) => const SizedBox(width: 8),
                          itemBuilder: (context, index) {
                            final ing = _ingredients[index];
                            final isSel = ing == _selectedIngredient;
                            return ChoiceChip(
                              label: Text(ing),
                              selected: isSel,
                              onSelected: (_) => setState(() => _selectedIngredient = ing),
                              selectedColor: const Color(0xFFEDE9FE),
                              backgroundColor: HasakiColors.canvas,
                              labelStyle: TextStyle(
                                fontSize: 11.5,
                                fontWeight: isSel ? FontWeight.w800 : FontWeight.w500,
                                color: isSel ? const Color(0xFF7C3AED) : HasakiColors.getTextMain(context),
                              ),
                              side: BorderSide(
                                color: isSel ? const Color(0xFF7C3AED) : HasakiColors.border,
                              ),
                            );
                          },
                        ),
                      ),
                      const SizedBox(height: 8),
                      if (ingredientProducts.isNotEmpty)
                        SizedBox(
                          height: 215,
                          child: ListView.separated(
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            scrollDirection: Axis.horizontal,
                            itemCount: ingredientProducts.take(6).length,
                            separatorBuilder: (_, __) => const SizedBox(width: 8),
                            itemBuilder: (context, index) {
                              final p = ingredientProducts[index];
                              return SizedBox(
                                width: 125,
                                child: HasakiProductCard(
                                  product: p,
                                  onTap: () {
                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(builder: (_) => ProductDetailScreen(productId: p.id)),
                                    );
                                  },
                                  onAddToCart: () {
                                    context.read<CartProvider>().addToCart(
                                      productId: p.id,
                                      variantId: 0,
                                      productName: p.name,
                                      variantName: 'Tiêu chuẩn',
                                      price: p.displayPrice,
                                      thumbnailUrl: p.thumbnailUrl,
                                    );
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(
                                        content: Text('Đã thêm "${p.name}" vào giỏ hàng'),
                                        duration: const Duration(seconds: 2),
                                        backgroundColor: HasakiColors.primary,
                                      ),
                                    );
                                  },
                                ),
                              );
                            },
                          ),
                        ),
                    ],
                  ),
                ),

                const SizedBox(height: 10),

                // 6. BeautyShop Spa & Relaxation Highlights Card
                Container(
                  margin: const EdgeInsets.symmetric(horizontal: 10),
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [HasakiColors.primaryDark, HasakiColors.primary],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      const CircleAvatar(
                        radius: 26,
                        backgroundColor: Colors.white24,
                        child: Icon(Icons.spa_outlined, color: Colors.white, size: 28),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'BeautyShop Spa & Thư Giãn',
                              style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 14),
                            ),
                            const SizedBox(height: 3),
                            Text(
                              'Trải nghiệm chăm sóc da & thư giãn chuyên nghiệp',
                              style: TextStyle(color: Colors.white.withOpacity(0.9), fontSize: 11),
                            ),
                          ],
                        ),
                      ),
                      ElevatedButton(
                        onPressed: () => widget.onNavigateTab?.call(2), // Spa tab
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.white,
                          foregroundColor: HasakiColors.primary,
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                          textStyle: const TextStyle(fontWeight: FontWeight.w800, fontSize: 11),
                        ),
                        child: const Text('ĐẶT LỊCH'),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 10),

                // 7. Multi-Tab Product Discovery Feed
                Container(
                  color: HasakiColors.getSurface(context),
                  child: Column(
                    children: [
                      TabBar(
                        controller: _tabController,
                        labelColor: HasakiColors.primary,
                        unselectedLabelColor: HasakiColors.textMuted,
                        indicatorColor: HasakiColors.primary,
                        indicatorWeight: 3,
                        labelStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w800),
                        unselectedLabelStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                        tabs: const [
                          Tab(text: 'Gợi Ý Cho Bạn'),
                          Tab(text: 'Bán Chạy Nhất'),
                          Tab(text: 'Sản Phẩm Mới'),
                        ],
                      ),
                      const Divider(height: 1, color: HasakiColors.border),
                    ],
                  ),
                ),

                // 8. Staggered 2-Column Product Grid
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Column(
                    children: [
                      GridView.builder(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: productProvider.products.take(10).length,
                        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          crossAxisSpacing: 8,
                          mainAxisSpacing: 8,
                          childAspectRatio: 0.62,
                        ),
                        itemBuilder: (context, index) {
                          final product = productProvider.products[index];
                          return HasakiProductCard(
                            product: product,
                            onTap: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => ProductDetailScreen(productId: product.id),
                                ),
                              );
                            },
                            onAddToCart: () {
                              context.read<CartProvider>().addToCart(
                                    productId: product.id,
                                    variantId: 0,
                                    productName: product.name,
                                    variantName: 'Tiêu chuẩn',
                                    price: product.displayPrice,
                                    thumbnailUrl: product.thumbnailUrl,
                                  );
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text('Đã thêm "${product.name}" vào giỏ hàng'),
                                  duration: const Duration(seconds: 2),
                                  backgroundColor: HasakiColors.primary,
                                ),
                              );
                            },
                          );
                        },
                      ),
                      const SizedBox(height: 12),
                      OutlinedButton(
                        onPressed: () => widget.onNavigateTab?.call(1),
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: HasakiColors.primary),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              'Xem thêm sản phẩm',
                              style: TextStyle(color: HasakiColors.primary, fontWeight: FontWeight.w700, fontSize: 12.5),
                            ),
                            SizedBox(width: 4),
                            Icon(Icons.arrow_forward_ios, size: 11, color: HasakiColors.primary),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
              ],
            ),
          ),
        );
      },
    ),
  );
  }
}

class FlashSaleCountdownWidget extends StatefulWidget {
  const FlashSaleCountdownWidget({Key? key}) : super(key: key);

  @override
  State<FlashSaleCountdownWidget> createState() => _FlashSaleCountdownWidgetState();
}

class _FlashSaleCountdownWidgetState extends State<FlashSaleCountdownWidget> {
  late Timer _timer;
  Duration _timeLeft = const Duration(hours: 4, minutes: 28, seconds: 45);

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_timeLeft.inSeconds > 0 && mounted) {
        setState(() => _timeLeft -= const Duration(seconds: 1));
      }
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  String _formatTime(int n) => n.toString().padLeft(2, '0');

  Widget _buildBox(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      decoration: BoxDecoration(
        color: Colors.black,
        borderRadius: BorderRadius.circular(3),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 10,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _buildBox(_formatTime(_timeLeft.inHours)),
        const Text(':', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 11)),
        _buildBox(_formatTime(_timeLeft.inMinutes % 60)),
        const Text(':', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 11)),
        _buildBox(_formatTime(_timeLeft.inSeconds % 60)),
      ],
    );
  }
}
