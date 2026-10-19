import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/cart_provider.dart';

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
