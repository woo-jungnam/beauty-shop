import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';

class HasakiProductCard extends StatelessWidget {
  final ProductItem product;
  final VoidCallback onTap;
  final VoidCallback? onAddToCart;

  const HasakiProductCard({
    Key? key,
    required this.product,
    required this.onTap,
    this.onAddToCart,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final discount = product.discountPercent;
    final hasDiscount = discount > 0;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        decoration: BoxDecoration(
          color: HasakiColors.getSurface(context),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: HasakiColors.getBorder(context)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.03),
              blurRadius: 4,
              offset: const Offset(0, 1),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 1. Image Thumbnail with Discount Badge
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
                              child: const Icon(Icons.spa_outlined, color: HasakiColors.textLight, size: 36),
                            ),
                          )
                        : Container(
                            color: HasakiColors.canvas,
                            child: const Icon(Icons.spa_outlined, color: HasakiColors.textLight, size: 36),
                          ),
                  ),
                ),

                // Discount Badge (Hasaki Deal Red)
                if (hasDiscount)
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

                // Official Store Badge
                if (product.isFeatured)
                  Positioned(
                    bottom: 4,
                    left: 4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                      decoration: BoxDecoration(
                        color: HasakiColors.primary,
                        borderRadius: BorderRadius.circular(3),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.verified, color: Colors.white, size: 8),
                          SizedBox(width: 2),
                          Text(
                            'Chính Hãng',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 8,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),

            // 2. Product Details Body
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(6, 4, 6, 5),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Brand Name
                        if (product.brandName != null && product.brandName!.isNotEmpty)
                          Text(
                            product.brandName!.toUpperCase(),
                            style: HasakiTypography.caption.copyWith(
                              color: HasakiColors.textMuted,
                              fontWeight: FontWeight.w700,
                              fontSize: 8.5,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),

                        // Product Name (2 lines)
                        Text(
                          product.name,
                          style: HasakiTypography.bodySmall.copyWith(
                            color: HasakiColors.getTextMain(context),
                            fontWeight: FontWeight.w600,
                            fontSize: 10.5,
                            height: 1.15,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),

                        const SizedBox(height: 1),

                        // Rating & Sold
                        Row(
                          children: [
                            const Icon(Icons.star, color: HasakiColors.star, size: 9.5),
                            const SizedBox(width: 2),
                            Text(
                              product.averageRating.toStringAsFixed(1),
                              style: TextStyle(
                                color: HasakiColors.getTextMain(context),
                                fontWeight: FontWeight.w700,
                                fontSize: 9,
                              ),
                            ),
                            const SizedBox(width: 3),
                            Text(
                              '(${product.totalSold > 0 ? product.totalSold : 12})',
                              style: const TextStyle(
                                color: HasakiColors.textLight,
                                fontSize: 8.5,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),

                    // Price & Add to Cart Action Row
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                HasakiFormatters.formatCurrency(product.displayPrice),
                                style: HasakiTypography.priceMedium.copyWith(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w800,
                                ),
                                maxLines: 1,
                              ),
                              if (hasDiscount)
                                Text(
                                  HasakiFormatters.formatCurrency(product.basePrice),
                                  style: HasakiTypography.priceStrikethrough.copyWith(
                                    fontSize: 9,
                                  ),
                                  maxLines: 1,
                                ),
                            ],
                          ),
                        ),

                        // Add to Cart Button (Small Hasaki Green Circle)
                        InkWell(
                          onTap: onAddToCart ?? onTap,
                          borderRadius: BorderRadius.circular(11),
                          child: Container(
                            width: 22,
                            height: 22,
                            decoration: BoxDecoration(
                              color: HasakiColors.primaryLight,
                              borderRadius: BorderRadius.circular(11),
                              border: Border.all(color: HasakiColors.primarySoft),
                            ),
                            child: const Icon(
                              Icons.add_shopping_cart,
                              color: HasakiColors.primary,
                              size: 12,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
