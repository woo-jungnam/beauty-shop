import 'package:flutter/material.dart';
import '../../data/models/product_model.dart';
import 'hasaki_product_card.dart';

class HasakiFlashDealCard extends StatelessWidget {
  final ProductItem product;
  final VoidCallback onTap;
  const HasakiFlashDealCard({
    super.key,
    required this.product,
    required this.onTap,
  });
  @override
  Widget build(BuildContext context) => SizedBox(
    width: 145,
    child: HasakiProductCard(product: product, onTap: onTap),
  );
}
