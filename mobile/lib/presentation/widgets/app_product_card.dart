import 'customer_wishlist_button.dart';
import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_typography.dart';
import '../../core/utils/formatters.dart';
import '../../data/models/product_model.dart';

class AppProductCard extends StatelessWidget {
  final ProductItem product;
  final VoidCallback onTap;
  final VoidCallback? onAddToCart;

  const AppProductCard({
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
          color: AppColors.getSurface(context),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.getBorder(context)),
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
            Stack(
              children: [
                ClipRRect(
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(8),
                  ),
                  child: AspectRatio(
                    aspectRatio: 1.0,
                    child:
                        product.thumbnailUrl != null &&
                            product.thumbnailUrl!.isNotEmpty
                        ? CachedNetworkImage(
                            imageUrl: product.thumbnailUrl!,
                            fit: BoxFit.cover,
                            placeholder: (_, __) =>
                                Container(color: AppColors.canvas),
                            errorWidget: (_, __, ___) => Container(
                              color: AppColors.canvas,
                              child: const Icon(
                                Icons.spa_outlined,
                                color: AppColors.textLight,
                                size: 36,
                              ),
                            ),
                          )
                        : Container(
                            color: AppColors.canvas,
                            child: const Icon(
                              Icons.spa_outlined,
                              color: AppColors.textLight,
                              size: 36,
                            ),
                          ),
                  ),
                ),
                Positioned(
                  top: 2,
                  right: 2,
                  child: CustomerWishlistButton(productId: product.id),
                ),
                if (hasDiscount)
                  Positioned(
                    top: 4,
                    left: 4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 4,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.dealRed,
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
                if (product.isFeatured)
                  Positioned(
                    bottom: 4,
                    left: 4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 4,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.primary,
                        borderRadius: BorderRadius.circular(3),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.verified, color: Colors.white, size: 8),
                          SizedBox(width: 2),
                          Text(
                            'Nổi bật',
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
                        if (product.brandName != null &&
                            product.brandName!.isNotEmpty)
                          Text(
                            product.brandName!.toUpperCase(),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 8.5,
                              fontWeight: FontWeight.w700,
                              color: AppColors.primary,
                              letterSpacing: 0.3,
                            ),
                          ),
                        const SizedBox(height: 1),
                        Text(
                          product.name,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: AppTypography.bodySmall.copyWith(
                            fontSize: 10.5,
                            fontWeight: FontWeight.w600,
                            color: AppColors.getTextMain(context),
                            height: 1.25,
                          ),
                        ),
                        const SizedBox(height: 1),
                        if (product.clearanceVariant != null)
                          Text(
                            'HSD ${product.earliestExpirationDate?.toIso8601String().split('T').first ?? 'chưa rõ'} · còn ${product.clearanceStock ?? 0}',
                            maxLines: 2,
                            style: const TextStyle(
                              fontSize: 9,
                              color: AppColors.textMuted,
                            ),
                          ),
                        Wrap(
                          spacing: 3,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            if (product.totalReviews > 0)
                              const Icon(
                                Icons.star,
                                color: AppColors.star,
                                size: 9.5,
                              ),
                            const SizedBox(width: 2),
                            Text(
                              product.totalReviews > 0
                                  ? product.averageRating.toStringAsFixed(1)
                                  : 'Chưa có đánh giá',
                              style: TextStyle(
                                color: AppColors.getTextMain(context),
                                fontWeight: FontWeight.w700,
                                fontSize: 9,
                              ),
                            ),
                            const SizedBox(width: 3),
                            Text(
                              product.totalSold > 0
                                  ? 'Đã bán ${product.totalSold}'
                                  : '',
                              style: const TextStyle(
                                color: AppColors.textLight,
                                fontSize: 8.5,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                AppFormatters.formatCurrency(
                                  product.displayPrice,
                                ),
                                style: AppTypography.priceMedium.copyWith(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w800,
                                ),
                                maxLines: 1,
                              ),
                              if (hasDiscount)
                                Text(
                                  AppFormatters.formatCurrency(
                                    product.basePrice,
                                  ),
                                  style: AppTypography.priceStrikethrough
                                      .copyWith(fontSize: 9),
                                  maxLines: 1,
                                ),
                            ],
                          ),
                        ),
                        InkWell(
                          onTap: onAddToCart ?? onTap,
                          borderRadius: BorderRadius.circular(11),
                          child: Container(
                            width: 22,
                            height: 22,
                            decoration: BoxDecoration(
                              color: AppColors.primaryLight,
                              borderRadius: BorderRadius.circular(11),
                              border: Border.all(
                                color: AppColors.primarySoft,
                              ),
                            ),
                            child: const Icon(
                              Icons.add_shopping_cart,
                              color: AppColors.primary,
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
