import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/utils/cart_actions.dart';
import '../../../data/models/product_model.dart';
import '../../../providers/product_provider.dart';
import '../../widgets/app_product_card.dart';
import '../../widgets/catalog_state.dart';
import '../product_detail/product_detail_screen.dart';

class ProductCollectionScreen extends StatefulWidget {
  final String title;
  final Map<String, dynamic> filters;

  const ProductCollectionScreen({
    super.key,
    required this.title,
    required this.filters,
  });

  @override
  State<ProductCollectionScreen> createState() =>
      _ProductCollectionScreenState();
}

class _ProductCollectionScreenState extends State<ProductCollectionScreen> {
  late Future<List<ProductItem>> _products;
  late Map<String, dynamic> _activeFilters;

  @override
  void initState() {
    super.initState();
    _activeFilters = Map<String, dynamic>.from(widget.filters);
    _load();
  }

  void _load() {
    _products = context.read<ProductProvider>().fetchCatalogProducts(
      filters: _activeFilters,
    );
  }

  void _openFilterBottomSheet() {
    String currentSort = _activeFilters['sort']?.toString() ?? 'createdAt,desc';
    String? currentSkinType = _activeFilters['skinType']?.toString();
    bool onSaleOnly = _activeFilters['onSale'] == true;
    bool inStockOnly = _activeFilters['inStock'] == true;
    String? priceRange = _getPriceRangeTag();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
            left: 16,
            right: 16,
            top: 16,
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Bộ Lọc & Sắp Xếp', style: AppTypography.titleMedium),
                    TextButton(
                      onPressed: () {
                        setSheetState(() {
                          currentSort = 'createdAt,desc';
                          currentSkinType = null;
                          onSaleOnly = false;
                          inStockOnly = false;
                          priceRange = null;
                        });
                      },
                      child: const Text('Thiết lập lại'),
                    ),
                  ],
                ),
                const Divider(),
                const SizedBox(height: 6),
                const Text('Sắp xếp theo:', style: TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _chip('Mới nhất', 'createdAt,desc', currentSort, (val) => setSheetState(() => currentSort = val)),
                    _chip('Bán chạy nhất', 'totalSold,desc', currentSort, (val) => setSheetState(() => currentSort = val)),
                    _chip('Giá tăng dần', 'price,asc', currentSort, (val) => setSheetState(() => currentSort = val)),
                    _chip('Giá giảm dần', 'price,desc', currentSort, (val) => setSheetState(() => currentSort = val)),
                  ],
                ),
                const SizedBox(height: 14),
                const Text('Khoảng giá:', style: TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _chip('Dưới 200.000đ', 'under200', priceRange, (val) => setSheetState(() => priceRange = priceRange == val ? null : val)),
                    _chip('200.000đ - 500.000đ', '200to500', priceRange, (val) => setSheetState(() => priceRange = priceRange == val ? null : val)),
                    _chip('Trên 500.000đ', 'over500', priceRange, (val) => setSheetState(() => priceRange = priceRange == val ? null : val)),
                  ],
                ),
                const SizedBox(height: 14),
                const Text('Loại da phù hợp:', style: TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _chip('Da dầu', 'OILY', currentSkinType, (val) => setSheetState(() => currentSkinType = currentSkinType == val ? null : val)),
                    _chip('Da khô', 'DRY', currentSkinType, (val) => setSheetState(() => currentSkinType = currentSkinType == val ? null : val)),
                    _chip('Da nhạy cảm', 'SENSITIVE', currentSkinType, (val) => setSheetState(() => currentSkinType = currentSkinType == val ? null : val)),
                    _chip('Da hỗn hợp', 'COMBINATION', currentSkinType, (val) => setSheetState(() => currentSkinType = currentSkinType == val ? null : val)),
                  ],
                ),
                const SizedBox(height: 14),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Chỉ hiển thị sản phẩm đang giảm giá'),
                  value: onSaleOnly,
                  activeColor: AppColors.primary,
                  onChanged: (val) => setSheetState(() => onSaleOnly = val),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Chỉ hiển thị sản phẩm còn hàng'),
                  value: inStockOnly,
                  activeColor: AppColors.primary,
                  onChanged: (val) => setSheetState(() => inStockOnly = val),
                ),
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
                    onPressed: () {
                      Navigator.pop(ctx);
                      final updated = Map<String, dynamic>.from(widget.filters);
                      updated['sort'] = currentSort;
                      if (currentSkinType != null) {
                        updated['skinType'] = currentSkinType;
                      } else {
                        updated.remove('skinType');
                      }
                      if (onSaleOnly) {
                        updated['onSale'] = true;
                      } else {
                        updated.remove('onSale');
                      }
                      if (inStockOnly) {
                        updated['inStock'] = true;
                      } else {
                        updated.remove('inStock');
                      }

                      updated.remove('minPrice');
                      updated.remove('maxPrice');
                      if (priceRange == 'under200') {
                        updated['maxPrice'] = 200000;
                      } else if (priceRange == '200to500') {
                        updated['minPrice'] = 200000;
                        updated['maxPrice'] = 500000;
                      } else if (priceRange == 'over500') {
                        updated['minPrice'] = 500000;
                      }

                      setState(() {
                        _activeFilters = updated;
                        _load();
                      });
                    },
                    child: const Text('ÁP DỤNG BỘ LỌC', style: TextStyle(fontWeight: FontWeight.w800)),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String? _getPriceRangeTag() {
    final min = _activeFilters['minPrice'];
    final max = _activeFilters['maxPrice'];
    if (max == 200000 && min == null) return 'under200';
    if (min == 200000 && max == 500000) return '200to500';
    if (min == 500000 && max == null) return 'over500';
    return null;
  }

  Widget _chip(String label, String value, String? selectedValue, Function(String) onSelect) {
    final isSelected = selectedValue == value;
    return ChoiceChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (_) => onSelect(value),
      selectedColor: AppColors.primaryLight,
      backgroundColor: AppColors.getSurface(context),
      labelStyle: TextStyle(
        fontSize: 12,
        fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
        color: isSelected ? AppColors.primary : AppColors.getTextMain(context),
      ),
      side: BorderSide(
        color: isSelected ? AppColors.primary : AppColors.border,
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(widget.title),
      backgroundColor: AppColors.primary,
      actions: [
        IconButton(
          icon: const Icon(Icons.tune),
          tooltip: 'Bộ lọc & Sắp xếp',
          onPressed: _openFilterBottomSheet,
        ),
      ],
    ),
    body: FutureBuilder<List<ProductItem>>(
      future: _products,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return CatalogState(
            message: 'Không thể tải sản phẩm.',
            onRetry: () => setState(_load),
          );
        }
        final products = snapshot.data ?? [];
        if (products.isEmpty) {
          return CatalogState(
            message: 'Chưa có sản phẩm phù hợp với bộ lọc hiện tại',
            onRetry: () {
              setState(() {
                _activeFilters = Map<String, dynamic>.from(widget.filters);
                _load();
              });
            },
          );
        }
        return GridView.builder(
          padding: const EdgeInsets.all(12),
          itemCount: products.length,
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            crossAxisSpacing: 10,
            mainAxisSpacing: 10,
            childAspectRatio: 0.65,
          ),
          itemBuilder: (context, index) {
            final product = products[index];
            return AppProductCard(
              product: product,
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => ProductDetailScreen(productId: product.id),
                ),
              ),
              onAddToCart: () => addProductToCart(
                context,
                productId: product.id,
                productName: product.name,
                price: product.displayPrice,
                thumbnailUrl: product.thumbnailUrl,
              ),
            );
          },
        );
      },
    ),
  );
}
