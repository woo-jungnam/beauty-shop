import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';

class HasakiAppBar extends StatelessWidget implements PreferredSizeWidget {
  final VoidCallback onSearchTap;
  final VoidCallback onCartTap;
  final VoidCallback? onNotificationTap;

  const HasakiAppBar({
    Key? key,
    required this.onSearchTap,
    required this.onCartTap,
    this.onNotificationTap,
  }) : super(key: key);

  @override
  Size get preferredSize => const Size.fromHeight(104);

  @override
  Widget build(BuildContext context) {
    final currentAddress = StorageService.getDeliveryAddress();

    return Container(
      color: HasakiColors.primary,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Row 1: Delivery Location & Quick Icons
              Row(
                children: [
                  const Icon(Icons.flash_on, color: HasakiColors.dealRed, size: 16),
                  const SizedBox(width: 4),
                  Text(
                    'Giao 2H đến: ',
                    style: HasakiTypography.caption.copyWith(
                      color: HasakiColors.textOnDarkMuted,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Expanded(
                    child: InkWell(
                      onTap: () {},
                      child: Row(
                        children: [
                          Flexible(
                            child: Text(
                              currentAddress,
                              style: HasakiTypography.caption.copyWith(
                                color: HasakiColors.textOnDark,
                                fontWeight: FontWeight.w700,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          const Icon(Icons.keyboard_arrow_down, color: HasakiColors.textOnDark, size: 16),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),

                  // Notifications Icon
                  InkWell(
                    onTap: onNotificationTap ?? () {},
                    borderRadius: BorderRadius.circular(20),
                    child: const Padding(
                      padding: EdgeInsets.all(4),
                      child: Icon(Icons.notifications_none, color: HasakiColors.textOnDark, size: 22),
                    ),
                  ),
                  const SizedBox(width: 4),

                  // Cart Icon with Live Badge
                  Consumer<CartProvider>(
                    builder: (context, cart, _) {
                      return InkWell(
                        onTap: onCartTap,
                        borderRadius: BorderRadius.circular(20),
                        child: Stack(
                          clipBehavior: Clip.none,
                          children: [
                            const Padding(
                              padding: EdgeInsets.all(4),
                              child: Icon(Icons.shopping_bag_outlined, color: HasakiColors.textOnDark, size: 22),
                            ),
                            if (cart.totalItemsCount > 0)
                              Positioned(
                                right: -2,
                                top: -2,
                                child: Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1.5),
                                  decoration: BoxDecoration(
                                    color: HasakiColors.dealRed,
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(color: HasakiColors.primary, width: 1.5),
                                  ),
                                  constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                                  child: Text(
                                    cart.totalItemsCount > 99 ? '99+' : '${cart.totalItemsCount}',
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 9.5,
                                      fontWeight: FontWeight.w800,
                                    ),
                                    textAlign: TextAlign.center,
                                  ),
                                ),
                              ),
                          ],
                        ),
                      );
                    },
                  ),
                ],
              ),
              const SizedBox(height: 8),

              // Row 2: Search Pill Bar (Hasaki White Search Bar)
              InkWell(
                onTap: onSearchTap,
                borderRadius: BorderRadius.circular(24),
                child: Container(
                  height: 40,
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  decoration: BoxDecoration(
                    color: HasakiColors.getSurface(context),
                    borderRadius: BorderRadius.circular(24),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.06),
                        blurRadius: 4,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.search, color: HasakiColors.primary, size: 20),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Tìm mỹ phẩm, serum B5, kem chống nắng...',
                          style: HasakiTypography.bodySmall.copyWith(
                            color: HasakiColors.textLight,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Icon(Icons.qr_code_scanner, color: HasakiColors.textMuted, size: 18),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
