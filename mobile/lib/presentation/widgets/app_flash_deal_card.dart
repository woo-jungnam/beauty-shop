import 'package:flutter/material.dart';
import '../../data/models/product_model.dart';
import 'app_product_card.dart';

class AppFlashDealCard extends StatelessWidget {
  final ProductItem product;
  final VoidCallback onTap;
  const AppFlashDealCard({
    super.key,
    required this.product,
    required this.onTap,
  });
  @override
  Widget build(BuildContext context) => SizedBox(
    width: 145,
    child: AppProductCard(product: product, onTap: onTap),
  );
}
