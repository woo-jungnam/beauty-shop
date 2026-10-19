import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/product_provider.dart';
import '../../../data/models/product_model.dart';
import '../../../core/utils/cart_actions.dart';
import '../../widgets/hasaki_product_card.dart';
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
  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _products = context.read<ProductProvider>().fetchCatalogProducts(
      filters: widget.filters,
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.title)),
    body: FutureBuilder<List<ProductItem>>(
      future: _products,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done)
          return const Center(child: CircularProgressIndicator());
        if (snapshot.hasError)
          return CatalogState(
            message: 'Không thể tải sản phẩm.',
            onRetry: () => setState(_load),
          );
        final products = snapshot.data ?? [];
        if (products.isEmpty)
          return const CatalogState(message: 'Chưa có sản phẩm trong mục này');
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
            return HasakiProductCard(
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
