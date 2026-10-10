import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/providers/product_provider.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:beautyshop_mobile/presentation/screens/product_detail/product_detail_screen.dart';

class CategoryScreen extends StatefulWidget {
  const CategoryScreen({Key? key}) : super(key: key);

  @override
  State<CategoryScreen> createState() => _CategoryScreenState();
}

class _CategoryScreenState extends State<CategoryScreen> {
  int _selectedCategoryIndex = 0;
  int? _lastFetchedCategoryId;
  final TextEditingController _searchController = TextEditingController();
  List<ProductItem>? _searchResults;
  bool _isSearching = false;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _onSearch(String query) async {
    final cleanQuery = query.trim();
    if (cleanQuery.isEmpty) {
      setState(() {
        _searchResults = null;
        _isSearching = false;
      });
      return;
    }

    setState(() => _isSearching = true);
    final results = await context.read<ProductProvider>().searchProductsApi(cleanQuery);
    if (mounted) {
      setState(() {
        _searchResults = results;
        _isSearching = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.getCanvas(context),
      appBar: AppBar(
        title: Container(
          height: 38,
          decoration: BoxDecoration(
            color: HasakiColors.getSurface(context),
            borderRadius: BorderRadius.circular(20),
          ),
          child: TextField(
            controller: _searchController,
            textInputAction: TextInputAction.search,
            onSubmitted: _onSearch,
            decoration: InputDecoration(
              hintText: 'Tìm kiếm mỹ phẩm, thương hiệu...',
              hintStyle: const TextStyle(fontSize: 12.5, color: HasakiColors.textLight),
              prefixIcon: const Icon(Icons.search, size: 18, color: HasakiColors.primary),
              suffixIcon: _searchController.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear, size: 16, color: HasakiColors.textMuted),
                      onPressed: () {
                        _searchController.clear();
                        setState(() => _searchResults = null);
                      },
                    )
                  : null,
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(vertical: 8),
            ),
          ),
        ),
        backgroundColor: HasakiColors.primary,
        elevation: 0,
      ),
      body: Consumer<ProductProvider>(
        builder: (context, provider, _) {
          // If searching with active query, display search results
          if (_searchResults != null) {
            if (_isSearching) {
              return const Center(child: CircularProgressIndicator(color: HasakiColors.primary));
            }
            if (_searchResults!.isEmpty) {
              return Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.search_off, size: 54, color: HasakiColors.textLight),
                    const SizedBox(height: 12),
                    Text('Không tìm thấy sản phẩm phù hợp', style: HasakiTypography.titleMedium),
                    const SizedBox(height: 6),
                    Text('Thử tìm kiếm với từ khóa khác', style: HasakiTypography.bodySmall),
                  ],
                ),
              );
            }
            return GridView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: _searchResults!.length,
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 10,
                mainAxisSpacing: 10,
                childAspectRatio: 0.65,
              ),
              itemBuilder: (context, idx) {
                final p = _searchResults![idx];
                return _buildProductGridCard(context, p);
              },
            );
          }

          final categories = provider.categories;
          if (categories.isEmpty) {
            return const Center(child: CircularProgressIndicator(color: HasakiColors.primary));
          }

          final activeCategory = categories[_selectedCategoryIndex.clamp(0, categories.length - 1)];

          // Trigger fetch once if category products not cached yet
          final cachedCategoryProducts = provider.categoryProductsMap[activeCategory.id];
          if (_lastFetchedCategoryId != activeCategory.id) {
            _lastFetchedCategoryId = activeCategory.id;
            if (cachedCategoryProducts == null && !provider.isLoadingCategoryProducts) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (mounted) provider.fetchProductsByCategory(activeCategory.id);
              });
            }
          }

          final categoryProducts = cachedCategoryProducts ??
              provider.products.where((p) => p.name.toLowerCase().contains(activeCategory.name.toLowerCase())).toList();

          final displayList = categoryProducts.isNotEmpty ? categoryProducts : provider.products;

          return Row(
            children: [
              // Left Column: Root Categories List (Hasaki Style)
              Container(
                width: 95,
                color: HasakiColors.getCanvas(context),
                child: ListView.builder(
                  itemCount: categories.length,
                  itemBuilder: (context, index) {
                    final cat = categories[index];
                    final isSelected = _selectedCategoryIndex == index;

                    return InkWell(
                      onTap: () {
                        setState(() => _selectedCategoryIndex = index);
                        provider.fetchProductsByCategory(cat.id);
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
                        decoration: BoxDecoration(
                          color: isSelected ? HasakiColors.getSurface(context) : HasakiColors.getCanvas(context),
                          border: Border(
                            left: BorderSide(
                              color: isSelected ? HasakiColors.primary : Colors.transparent,
                              width: 3.5,
                            ),
                            bottom: BorderSide(color: HasakiColors.getBorder(context), width: 0.5),
                          ),
                        ),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 32,
                              height: 32,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: isSelected ? HasakiColors.getPrimaryLight(context) : HasakiColors.getSurface(context),
                                border: Border.all(
                                  color: isSelected ? HasakiColors.primary : HasakiColors.getBorder(context),
                                  width: 0.8,
                                ),
                              ),
                              child: ClipOval(
                                child: (cat.displayImage != null && cat.displayImage!.isNotEmpty)
                                    ? CachedNetworkImage(
                                        imageUrl: cat.displayImage!,
                                        fit: BoxFit.cover,
                                        placeholder: (_, __) => Container(color: Colors.transparent),
                                        errorWidget: (_, __, ___) => Icon(
                                          Icons.spa_outlined,
                                          size: 16,
                                          color: isSelected ? HasakiColors.primary : HasakiColors.textLight,
                                        ),
                                      )
                                    : Icon(
                                        Icons.spa_outlined,
                                        size: 16,
                                        color: isSelected ? HasakiColors.primary : HasakiColors.textLight,
                                      ),
                              ),
                            ),
                            const SizedBox(height: 5),
                            Text(
                              cat.name,
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
                                color: isSelected ? HasakiColors.primary : HasakiColors.getTextMain(context),
                                height: 1.2,
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

              // Right Column: Products in Category
              Expanded(
                child: Container(
                  color: HasakiColors.getSurface(context),
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Category Title
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              activeCategory.name.toUpperCase(),
                              style: HasakiTypography.titleMedium.copyWith(
                                color: HasakiColors.primary,
                                fontWeight: FontWeight.w800,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          Text(
                            '${displayList.length} SP',
                            style: HasakiTypography.caption.copyWith(color: HasakiColors.textMuted),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),

                      // Products Grid
                      Expanded(
                        child: provider.isLoadingCategoryProducts && cachedCategoryProducts == null
                            ? const Center(child: CircularProgressIndicator(color: HasakiColors.primary))
                            : GridView.builder(
                                itemCount: displayList.length,
                                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                                  crossAxisCount: 2,
                                  crossAxisSpacing: 8,
                                  mainAxisSpacing: 8,
                                  childAspectRatio: 0.68,
                                ),
                                itemBuilder: (context, idx) {
                                  final p = displayList[idx];
                                  return _buildProductGridCard(context, p);
                                },
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

  Widget _buildProductGridCard(BuildContext context, ProductItem p) {
    return InkWell(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => ProductDetailScreen(productId: p.id),
          ),
        );
      },
      child: Container(
        padding: const EdgeInsets.all(6),
        decoration: BoxDecoration(
          color: HasakiColors.getSurface(context),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: HasakiColors.getBorder(context)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: p.thumbnailUrl != null && p.thumbnailUrl!.isNotEmpty
                    ? CachedNetworkImage(
                        imageUrl: p.thumbnailUrl!,
                        fit: BoxFit.cover,
                        width: double.infinity,
                        placeholder: (_, __) => Container(color: HasakiColors.canvas),
                        errorWidget: (_, __, ___) => Container(
                          color: HasakiColors.canvas,
                          child: const Icon(Icons.spa_outlined, color: HasakiColors.textLight, size: 24),
                        ),
                      )
                    : Container(
                        color: HasakiColors.canvas,
                        child: const Icon(Icons.spa_outlined, color: HasakiColors.textLight, size: 24),
                      ),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              p.name,
              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 3),
            Row(
              children: [
                Text(
                  '${(p.displayPrice / 1000).toStringAsFixed(0)}K',
                  style: const TextStyle(
                    color: HasakiColors.dealRed,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (p.discountPercent > 0) ...[
                  const SizedBox(width: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 1),
                    decoration: BoxDecoration(
                      color: HasakiColors.dealRedLight,
                      borderRadius: BorderRadius.circular(2),
                    ),
                    child: Text(
                      '-${p.discountPercent}%',
                      style: const TextStyle(
                        color: HasakiColors.dealRed,
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}
