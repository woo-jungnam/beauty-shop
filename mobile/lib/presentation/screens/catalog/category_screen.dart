import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/providers/product_provider.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:beautyshop_mobile/presentation/screens/product_detail/product_detail_screen.dart';
import '../../widgets/catalog_state.dart';
import 'image_search_screen.dart';

class CategoryScreen extends StatefulWidget {
  const CategoryScreen({Key? key}) : super(key: key);

  @override
  State<CategoryScreen> createState() => _CategoryScreenState();
}

class _CategoryScreenState extends State<CategoryScreen> {
  int _selectedCategoryIndex = 0;
  final TextEditingController _searchController = TextEditingController();
  List<ProductItem>? _searchResults;
  bool _isSearching = false;
  String? _searchError;
  int _searchRevision = 0;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _onSearch(String query) async {
    final revision = ++_searchRevision;
    final cleanQuery = query.trim();
    if (cleanQuery.isEmpty) {
      setState(() {
        _searchResults = null;
        _isSearching = false;
        _searchError = null;
      });
      return;
    }

    setState(() {
      _isSearching = true;
      _searchError = null;
    });
    try {
      final results = await context.read<ProductProvider>().searchProductsApi(
        cleanQuery,
      );
      if (mounted && revision == _searchRevision)
        setState(() => _searchResults = results);
    } catch (_) {
      if (mounted && revision == _searchRevision)
        setState(() => _searchError = 'Không thể tìm kiếm sản phẩm.');
    } finally {
      if (mounted && revision == _searchRevision)
        setState(() => _isSearching = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: Container(
          height: 38,
          decoration: BoxDecoration(
            color: AppColors.getSurface(context),
            borderRadius: BorderRadius.circular(20),
          ),
          child: TextField(
            controller: _searchController,
            textInputAction: TextInputAction.search,
            onSubmitted: _onSearch,
            decoration: InputDecoration(
              hintText: 'Tìm kiếm mỹ phẩm, thương hiệu...',
              hintStyle: const TextStyle(
                fontSize: 12.5,
                color: AppColors.textLight,
              ),
              prefixIcon: const Icon(
                Icons.search,
                size: 18,
                color: AppColors.primary,
              ),
              suffixIcon: _searchController.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(
                        Icons.clear,
                        size: 16,
                        color: AppColors.textMuted,
                      ),
                      onPressed: () {
                        _searchController.clear();
                        _onSearch('');
                      },
                    )
                  : null,
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(vertical: 8),
            ),
          ),
        ),
        backgroundColor: AppColors.primary,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.camera_alt_outlined, color: Colors.white),
            tooltip: 'Tìm kiếm bằng ảnh',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ImageSearchScreen()),
            ),
          ),
        ],
      ),
      body: Consumer<ProductProvider>(
        builder: (context, provider, _) {
          if (_isSearching)
            return const Center(child: CircularProgressIndicator());
          if (_searchError != null)
            return CatalogState(
              message: _searchError!,
              onRetry: () => _onSearch(_searchController.text),
            );
          if (_searchResults != null) {
            if (_isSearching) {
              return const Center(
                child: CircularProgressIndicator(color: AppColors.primary),
              );
            }
            if (_searchResults!.isEmpty) {
              return Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(
                      Icons.search_off,
                      size: 54,
                      color: AppColors.textLight,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'Không tìm thấy sản phẩm phù hợp',
                      style: AppTypography.titleMedium,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Thử tìm kiếm với từ khóa khác',
                      style: AppTypography.bodySmall,
                    ),
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
            if (provider.isLoading)
              return const Center(child: CircularProgressIndicator());
            return CatalogState(
              message: provider.categoriesError ?? 'Chưa có danh mục',
              onRetry: provider.categoriesError != null
                  ? provider.loadInitialData
                  : null,
            );
          }

          final activeCategory =
              categories[_selectedCategoryIndex.clamp(
                0,
                categories.length - 1,
              )];

          final cachedCategoryProducts =
              provider.categoryProductsMap[activeCategory.id];
          if (cachedCategoryProducts == null &&
              !provider.isLoading &&
              !provider.isLoadingCategory(activeCategory.id) &&
              provider.categoryError(activeCategory.id) == null) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (mounted &&
                  !provider.isLoadingCategory(activeCategory.id) &&
                  !provider.categoryProductsMap.containsKey(
                    activeCategory.id,
                  )) {
                provider.fetchProductsByCategory(activeCategory.id);
              }
            });
          }

          final displayList = cachedCategoryProducts ?? <ProductItem>[];

          return Row(
            children: [
              Container(
                width: 95,
                color: AppColors.getCanvas(context),
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
                        padding: const EdgeInsets.symmetric(
                          vertical: 10,
                          horizontal: 6,
                        ),
                        decoration: BoxDecoration(
                          color: isSelected
                              ? AppColors.getSurface(context)
                              : AppColors.getCanvas(context),
                          border: Border(
                            left: BorderSide(
                              color: isSelected
                                  ? AppColors.primary
                                  : Colors.transparent,
                              width: 3.5,
                            ),
                            bottom: BorderSide(
                              color: AppColors.getBorder(context),
                              width: 0.5,
                            ),
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
                                color: isSelected
                                    ? AppColors.getPrimaryLight(context)
                                    : AppColors.getSurface(context),
                                border: Border.all(
                                  color: isSelected
                                      ? AppColors.primary
                                      : AppColors.getBorder(context),
                                  width: 0.8,
                                ),
                              ),
                              child: ClipOval(
                                child:
                                    (cat.displayImage != null &&
                                        cat.displayImage!.isNotEmpty)
                                    ? CachedNetworkImage(
                                        imageUrl: cat.displayImage!,
                                        fit: BoxFit.cover,
                                        placeholder: (_, __) => Container(
                                          color: Colors.transparent,
                                        ),
                                        errorWidget: (_, __, ___) => Icon(
                                          Icons.spa_outlined,
                                          size: 16,
                                          color: isSelected
                                              ? AppColors.primary
                                              : AppColors.textLight,
                                        ),
                                      )
                                    : Icon(
                                        Icons.spa_outlined,
                                        size: 16,
                                        color: isSelected
                                            ? AppColors.primary
                                            : AppColors.textLight,
                                      ),
                              ),
                            ),
                            const SizedBox(height: 5),
                            Text(
                              cat.name,
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: isSelected
                                    ? FontWeight.w800
                                    : FontWeight.w500,
                                color: isSelected
                                    ? AppColors.primary
                                    : AppColors.getTextMain(context),
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

              Expanded(
                child: Container(
                  color: AppColors.getSurface(context),
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              activeCategory.name.toUpperCase(),
                              style: AppTypography.titleMedium.copyWith(
                                color: AppColors.primary,
                                fontWeight: FontWeight.w800,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          Text(
                            '${displayList.length} SP',
                            style: AppTypography.caption.copyWith(
                              color: AppColors.textMuted,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),

                      Expanded(
                        child: provider.isLoadingCategory(activeCategory.id)
                            ? const Center(
                                child: CircularProgressIndicator(
                                  color: AppColors.primary,
                                ),
                              )
                            : provider.categoryError(activeCategory.id) != null
                            ? CatalogState(
                                message: provider.categoryError(
                                  activeCategory.id,
                                )!,
                                onRetry: () => provider.fetchProductsByCategory(
                                  activeCategory.id,
                                  refresh: true,
                                ),
                              )
                            : displayList.isEmpty
                            ? const CatalogState(
                                message: 'Danh mục này chưa có sản phẩm',
                              )
                            : GridView.builder(
                                itemCount: displayList.length,
                                gridDelegate:
                                    const SliverGridDelegateWithFixedCrossAxisCount(
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
          color: AppColors.getSurface(context),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.getBorder(context)),
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
                        placeholder: (_, __) =>
                            Container(color: AppColors.canvas),
                        errorWidget: (_, __, ___) => Container(
                          color: AppColors.canvas,
                          child: const Icon(
                            Icons.spa_outlined,
                            color: AppColors.textLight,
                            size: 24,
                          ),
                        ),
                      )
                    : Container(
                        color: AppColors.canvas,
                        child: const Icon(
                          Icons.spa_outlined,
                          color: AppColors.textLight,
                          size: 24,
                        ),
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
                    color: AppColors.dealRed,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                if (p.discountPercent > 0) ...[
                  const SizedBox(width: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 3,
                      vertical: 1,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.dealRedLight,
                      borderRadius: BorderRadius.circular(2),
                    ),
                    child: Text(
                      '-${p.discountPercent}%',
                      style: const TextStyle(
                        color: AppColors.dealRed,
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
