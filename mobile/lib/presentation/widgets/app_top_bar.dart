import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_typography.dart';
import '../../core/storage/storage_service.dart';
import '../../providers/cart_provider.dart';
import 'delivery_address_dialog.dart';
import '../screens/catalog/image_search_screen.dart';

class AppTopBar extends StatefulWidget implements PreferredSizeWidget {
  final VoidCallback onSearchTap;
  final VoidCallback onCartTap;
  final VoidCallback? onNotificationTap;

  const AppTopBar({
    Key? key,
    required this.onSearchTap,
    required this.onCartTap,
    this.onNotificationTap,
  }) : super(key: key);

  @override
  Size get preferredSize => const Size.fromHeight(104);

  @override
  State<AppTopBar> createState() => _AppTopBarState();
}

class _AppTopBarState extends State<AppTopBar> {
  @override
  Widget build(BuildContext context) {
    final currentAddress = StorageService.getFullDeliveryAddress();

    return Container(
      color: AppColors.primary,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.flash_on,
                    color: AppColors.dealRed,
                    size: 16,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    'Giao đến: ',
                    style: AppTypography.caption.copyWith(
                      color: AppColors.textOnDarkMuted,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Expanded(
                    child: InkWell(
                      onTap: () async {
                        final saved = await showDialog<bool>(
                          context: context,
                          builder: (_) => const DeliveryAddressDialog(),
                        );
                        if (saved == true && mounted) setState(() {});
                      },
                      child: Row(
                        children: [
                          Flexible(
                            child: Text(
                              currentAddress.isEmpty
                                  ? 'Chọn địa chỉ nhận hàng'
                                  : currentAddress,
                              style: AppTypography.caption.copyWith(
                                color: AppColors.textOnDark,
                                fontWeight: FontWeight.w700,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          const Icon(
                            Icons.keyboard_arrow_down,
                            color: AppColors.textOnDark,
                            size: 16,
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  if (widget.onNotificationTap != null)
                    InkWell(
                      onTap: widget.onNotificationTap,
                      borderRadius: BorderRadius.circular(20),
                      child: const Padding(
                        padding: EdgeInsets.all(4),
                        child: Icon(
                          Icons.notifications_none,
                          color: AppColors.textOnDark,
                          size: 22,
                        ),
                      ),
                    ),
                  const SizedBox(width: 4),
                  Consumer<CartProvider>(
                    builder: (context, cart, _) {
                      return InkWell(
                        onTap: widget.onCartTap,
                        borderRadius: BorderRadius.circular(20),
                        child: Stack(
                          clipBehavior: Clip.none,
                          children: [
                            const Padding(
                              padding: EdgeInsets.all(4),
                              child: Icon(
                                Icons.shopping_bag_outlined,
                                color: AppColors.textOnDark,
                                size: 22,
                              ),
                            ),
                            if (cart.totalItemsCount > 0)
                              Positioned(
                                right: -2,
                                top: -2,
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 5,
                                    vertical: 1.5,
                                  ),
                                  decoration: BoxDecoration(
                                    color: AppColors.dealRed,
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(
                                      color: AppColors.primary,
                                      width: 1.5,
                                    ),
                                  ),
                                  constraints: const BoxConstraints(
                                    minWidth: 16,
                                    minHeight: 16,
                                  ),
                                  child: Text(
                                    cart.totalItemsCount > 99
                                        ? '99+'
                                        : '${cart.totalItemsCount}',
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
              InkWell(
                onTap: widget.onSearchTap,
                borderRadius: BorderRadius.circular(24),
                child: Container(
                  height: 40,
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  decoration: BoxDecoration(
                    color: AppColors.getSurface(context),
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
                      const Icon(
                        Icons.search,
                        color: AppColors.primary,
                        size: 20,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Tìm mỹ phẩm, serum B5, kem chống nắng...',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.textLight,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      InkWell(
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const ImageSearchScreen(),
                          ),
                        ),
                        child: const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 4),
                          child: Icon(
                            Icons.camera_alt_outlined,
                            color: AppColors.primary,
                            size: 20,
                          ),
                        ),
                      ),
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
