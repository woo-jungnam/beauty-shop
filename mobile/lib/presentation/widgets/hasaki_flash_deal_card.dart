import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';

class HasakiFlashDealCard extends StatelessWidget {
  final ProductItem product;
  final VoidCallback onTap;

  const HasakiFlashDealCard({
    Key? key,
    required this.product,
    required this.onTap,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final discount = product.discountPercent > 0 ? product.discountPercent : 35;
    final soldRatio = ((product.id * 17) % 65 + 30).clamp(25, 95); // Dynamic realistic progress

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        width: 125,
        decoration: BoxDecoration(
          color: HasakiColors.getSurface(context),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: HasakiColors.getBorder(context)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 4,
              offset: const Offset(0, 1),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Product Image & Discount Tag
            Stack(
              children: [
                ClipRRect(
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
                  child: AspectRatio(
                    aspectRatio: 1.0,
                    child: product.thumbnailUrl != null && product.thumbnailUrl!.isNotEmpty
                        ? CachedNetworkImage(
                            imageUrl: product.thumbnailUrl!,
                            fit: BoxFit.cover,
                            placeholder: (_, __) => Container(color: HasakiColors.canvas),
                            errorWidget: (_, __, ___) => Container(
                              color: HasakiColors.canvas,
                              child: const Icon(Icons.spa_outlined, color: HasakiColors.textLight),
                            ),
                          )
                        : Container(
                            color: HasakiColors.canvas,
                            child: const Icon(Icons.spa_outlined, color: HasakiColors.textLight),
                          ),
                  ),
                ),
                Positioned(
                  top: 4,
                  left: 4,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                    decoration: BoxDecoration(
                      color: HasakiColors.dealRed,
                      borderRadius: BorderRadius.circular(3),
                    ),
                    child: Text(
                      '-$discount%',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 9,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ),
              ],
            ),

            // Pricing & Deal Bar
            Padding(
              padding: const EdgeInsets.all(6.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    HasakiFormatters.formatCurrency(product.displayPrice),
                    style: HasakiTypography.priceMedium.copyWith(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w800,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 1),
                  Text(
                    HasakiFormatters.formatCurrency(product.basePrice),
                    style: HasakiTypography.priceStrikethrough.copyWith(
                      fontSize: 9.5,
                    ),
                    maxLines: 1,
                  ),
                  const SizedBox(height: 5),

                  // Hasaki Fire Sold Progress Bar
                  Stack(
                    children: [
                      Container(
                        height: 12,
                        decoration: BoxDecoration(
                          color: HasakiColors.dealRedLight,
                          borderRadius: BorderRadius.circular(6),
                        ),
                      ),
                      FractionallySizedBox(
                        widthFactor: soldRatio / 100,
                        child: Container(
                          height: 12,
                          decoration: BoxDecoration(
                            gradient: const LinearGradient(
                              colors: [HasakiColors.dealRed, Color(0xFFF43F5E)],
                            ),
                            borderRadius: BorderRadius.circular(6),
                          ),
                        ),
                      ),
                      Center(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.local_fire_department, color: Colors.white, size: 9),
                              const SizedBox(width: 2),
                              Text(
                                'ĐÃ BÁN $soldRatio%',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 7.5,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
