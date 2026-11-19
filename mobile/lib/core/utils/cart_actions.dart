import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/cart_provider.dart';
import '../../data/models/order_model.dart';

Future<bool> addProductToCart(
  BuildContext context, {
  required int productId,
  int variantId = 0,
  required String productName,
  String variantName = '',
  required double price,
  String? thumbnailUrl,
}) async {
  final cart = context.read<CartProvider>();
  final success = await cart.addToCart(
    productId: productId,
    variantId: variantId,
    productName: productName,
    variantName: variantName,
    price: price,
    thumbnailUrl: thumbnailUrl,
  );
  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          success
              ? 'Đã thêm sản phẩm vào giỏ hàng'
              : cart.errorMessage ?? 'Không thể thêm sản phẩm',
        ),
      ),
    );
  }
  return success;
}

// Reorder the exact purchased variants. The server applies current prices.
Future<void> reorderProducts(
  BuildContext context,
  List<OrderItem> items,
) async {
  final cart = context.read<CartProvider>();
  final failed = <String>[];
  var added = 0;
  for (final item in items) {
    final variantId = item.variantId;
    if (variantId == null ||
        variantId <= 0 ||
        item.quantity <= 0 ||
        !await cart.addToCart(
          productId: item.productId ?? 0,
          variantId: variantId,
          productName: item.productName,
          variantName: item.variantName,
          price: item.effectiveUnitPrice,
          quantity: item.quantity,
          thumbnailUrl: item.thumbnailUrl,
        )) {
      failed.add(item.productName);
    } else {
      added++;
    }
  }
  if (!context.mounted) return;
  final saved = 'Đã thêm $added mặt hàng theo giá hiện tại.';
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(
        failed.isEmpty ? saved : '$saved Không thể thêm: ${failed.join(', ')}.',
      ),
    ),
  );
}
