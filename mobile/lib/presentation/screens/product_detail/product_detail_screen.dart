import '../../widgets/customer_wishlist_button.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import '../../../core/utils/cart_actions.dart';
import '../../widgets/catalog_state.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/providers/product_provider.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';
import 'package:beautyshop_mobile/data/models/review_model.dart';
import 'package:beautyshop_mobile/presentation/screens/cart/cart_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/chatbot/ai_chatbot_screen.dart';
import 'package:beautyshop_mobile/presentation/widgets/app_product_card.dart';

class ProductDetailScreen extends StatefulWidget {
  final int productId;
  final int? initialVariantId;

  const ProductDetailScreen({
    Key? key,
    required this.productId,
    this.initialVariantId,
  }) : super(key: key);

  @override
  State<ProductDetailScreen> createState() => _ProductDetailScreenState();
}

class _ProductDetailScreenState extends State<ProductDetailScreen> {
  int _selectedImageIndex = 0;
  int? _selectedVariantId;

  @override
  void initState() {
    super.initState();
    _selectedVariantId = widget.initialVariantId;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<ProductProvider>().fetchProductDetail(widget.productId);
    });
  }

  void _showAddReviewDialog(BuildContext context) {
    final auth = context.read<AuthProvider>();
    if (!auth.isAuthenticated) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vui lòng đăng nhập để gửi đánh giá sản phẩm'),
          backgroundColor: AppColors.dealRed,
        ),
      );
      return;
    }

    final detail = context.read<ProductProvider>().selectedProductDetail;
    final variantIds = detail != null
        ? detail.variants.map((v) => v.id).toSet()
        : <int>{};
    final orders = context.read<OrderProvider>().orders;
    final deliveredOrder = orders
        .where(
          (o) =>
              o.status == 'DELIVERED' &&
              o.items.any(
                (i) =>
                    (i.variantId != null && variantIds.contains(i.variantId)) ||
                    i.productId == widget.productId,
              ),
        )
        .firstOrNull;
    final hasDelivered = deliveredOrder != null;

    int selectedRating = 5;
    final titleController = TextEditingController();
    final contentController = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) => Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
            left: 16,
            right: 16,
            top: 16,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Đánh giá sản phẩm',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(ctx),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              if (hasDelivered)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  margin: const EdgeInsets.only(bottom: 8),
                  decoration: BoxDecoration(
                    color: AppColors.primaryLight,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.verified, size: 16, color: AppColors.primary),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          'Đã xác nhận mua hàng (${deliveredOrder.orderCode})',
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                            color: AppColors.primary,
                          ),
                        ),
                      ),
                    ],
                  ),
                )
              else
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  margin: const EdgeInsets.only(bottom: 8),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF3F4F6),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.info_outline, size: 16, color: AppColors.textMuted),
                      SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          'Chưa tìm thấy đơn đã giao trên tài khoản. Đánh giá sẽ ghi nhận dưới dạng nhận xét.',
                          style: TextStyle(fontSize: 11, color: AppColors.textMuted),
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(5, (index) {
                  return IconButton(
                    icon: Icon(
                      index < selectedRating ? Icons.star : Icons.star_border,
                      color: AppColors.star,
                      size: 32,
                    ),
                    onPressed: () =>
                        setSheetState(() => selectedRating = index + 1),
                  );
                }),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: titleController,
                decoration: const InputDecoration(
                  labelText: 'Tiêu đề (ví dụ: Rất ưng ý, cấp ẩm tốt)',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: contentController,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Nhận xét chi tiết về hiệu quả trên da...',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                height: 46,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                  ),
                  onPressed: () async {
                    if (contentController.text.trim().isEmpty) return;
                    final eligibleOrderId = deliveredOrder?.id;

                    final req = CreateReviewRequest(
                      productId: widget.productId,
                      orderId: eligibleOrderId,
                      rating: hasDelivered ? selectedRating : null,
                      title: titleController.text.trim().isEmpty
                          ? 'Đánh giá sản phẩm'
                          : titleController.text.trim(),
                      content: contentController.text.trim(),
                    );
                    Navigator.pop(ctx);
                    final ok = await context
                        .read<ProductProvider>()
                        .submitReview(req);
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            ok
                                ? 'Cảm ơn bạn đã gửi đánh giá!'
                                : 'Không thể gửi đánh giá lúc này',
                          ),
                          backgroundColor: ok
                              ? AppColors.primary
                              : AppColors.dealRed,
                        ),
                      );
                    }
                  },
                  child: const Text(
                    'GỬI ĐÁNH GIÁ',
                    style: TextStyle(fontWeight: FontWeight.w800),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<ProductProvider>(
      builder: (context, provider, _) {
        if (provider.isLoadingDetail) {
          return Scaffold(
            backgroundColor: AppColors.getSurface(context),
            appBar: AppBar(
              backgroundColor: AppColors.primary,
              title: const Text('Chi tiết sản phẩm'),
            ),
            body: const Center(
              child: CircularProgressIndicator(color: AppColors.primary),
            ),
          );
        }
        if (provider.selectedProductDetail == null)
          return Scaffold(
            appBar: AppBar(title: const Text('Chi tiết sản phẩm')),
            body: CatalogState(
              message: provider.detailError ?? 'Không tìm thấy sản phẩm',
              onRetry: () => provider.fetchProductDetail(widget.productId),
            ),
          );

        final detail = provider.selectedProductDetail!;
        final images = detail.imageUrls.isNotEmpty
            ? detail.imageUrls
            : [detail.thumbnailUrl ?? ''];

        final activeVariant = detail.variantForSelection(_selectedVariantId);
        final canBuy =
            detail.status == 'ACTIVE' && activeVariant?.isAvailable == true;

        final currentPrice = activeVariant != null
            ? activeVariant.effectivePrice
            : detail.basePrice;
        final basePrice = activeVariant != null
            ? (activeVariant.originalPrice ?? activeVariant.price)
            : detail.basePrice;
        final discountPercent = AppFormatters.calculateDiscountPercent(
          basePrice,
          currentPrice,
        );

        return Scaffold(
          backgroundColor: AppColors.getCanvas(context),
          appBar: AppBar(
            backgroundColor: AppColors.primary,
            title: Text(detail.brandName ?? 'Chi tiết sản phẩm'),
            actions: [
              CustomerWishlistButton(productId: widget.productId),
              Consumer<CartProvider>(
                builder: (context, cart, _) => IconButton(
                  icon: Badge(
                    label: Text('${cart.totalItemsCount}'),
                    isLabelVisible: cart.totalItemsCount > 0,
                    backgroundColor: AppColors.dealRed,
                    child: const Icon(Icons.shopping_bag_outlined),
                  ),
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const CartScreen()),
                    );
                  },
                ),
              ),
            ],
          ),
          body: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  color: AppColors.getSurface(context),
                  child: AspectRatio(
                    aspectRatio: 1.0,
                    child: Stack(
                      children: [
                        PageView.builder(
                          itemCount: images.length,
                          onPageChanged: (idx) =>
                              setState(() => _selectedImageIndex = idx),
                          itemBuilder: (context, idx) {
                            return CachedNetworkImage(
                              imageUrl: images[idx],
                              fit: BoxFit.contain,
                              placeholder: (_, __) =>
                                  Container(color: AppColors.canvas),
                              errorWidget: (_, __, ___) => const Icon(
                                Icons.spa,
                                size: 50,
                                color: AppColors.textLight,
                              ),
                            );
                          },
                        ),
                        Positioned(
                          bottom: 12,
                          right: 12,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 3,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black.withOpacity(0.65),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Text(
                              '${_selectedImageIndex + 1}/${images.length}',
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                Container(
                  color: AppColors.getSurface(context),
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.baseline,
                        textBaseline: TextBaseline.alphabetic,
                        children: [
                          Text(
                            AppFormatters.formatCurrency(currentPrice),
                            style: AppTypography.priceLarge.copyWith(
                              fontSize: 22,
                            ),
                          ),
                          const SizedBox(width: 8),
                          if (discountPercent > 0) ...[
                            Text(
                              AppFormatters.formatCurrency(basePrice),
                              style: AppTypography.priceStrikethrough
                                  .copyWith(fontSize: 14),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 5,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: AppColors.dealRedLight,
                                border: Border.all(
                                  color: AppColors.dealRedBorder,
                                ),
                                borderRadius: BorderRadius.circular(4),
                              ),
                              child: Text(
                                '-$discountPercent%',
                                style: const TextStyle(
                                  color: AppColors.dealRed,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 8),

                      Text(
                        detail.name,
                        style: AppTypography.titleLarge.copyWith(
                          fontSize: 16,
                          height: 1.3,
                        ),
                      ),
                      const SizedBox(height: 8),

                      Wrap(
                        spacing: 6,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          const Icon(
                            Icons.star,
                            color: AppColors.star,
                            size: 16,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            detail.totalReviews > 0
                                ? detail.averageRating.toStringAsFixed(1)
                                : 'Chưa có đánh giá',
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                              fontSize: 13,
                            ),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            '(${detail.totalReviews} đánh giá)',
                            style: AppTypography.caption,
                          ),
                          const SizedBox(width: 12),
                          Text(
                            'Đã bán: ${detail.totalSold}',
                            style: AppTypography.caption,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 8),

                const SizedBox(height: 8),

                if (!canBuy)
                  const Padding(
                    padding: EdgeInsets.all(14),
                    child: Text(
                      'Phân loại sản phẩm này tạm hết hàng. Vui lòng chọn phân loại khác.',
                    ),
                  ),

                if (detail.variants.isNotEmpty)
                  Container(
                    color: AppColors.getSurface(context),
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Chọn phân loại / dung tích:',
                          style: AppTypography.titleMedium.copyWith(
                            fontSize: 13,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: detail.variants.map((variant) {
                            final isSelected =
                                (activeVariant?.id == variant.id);
                            return ChoiceChip(
                              label: Text(
                                '${variant.variantName} (${AppFormatters.formatCurrency(variant.effectivePrice)})',
                              ),
                              selected: isSelected,
                              onSelected: (_) {
                                setState(() => _selectedVariantId = variant.id);
                              },
                              selectedColor: AppColors.primaryLight,
                              backgroundColor: AppColors.getCanvas(context),
                              labelStyle: TextStyle(
                                fontSize: 12,
                                fontWeight: isSelected
                                    ? FontWeight.w800
                                    : FontWeight.w500,
                                color: isSelected
                                    ? AppColors.primary
                                    : AppColors.getTextMain(context),
                              ),
                              side: BorderSide(
                                color: isSelected
                                    ? AppColors.primary
                                    : AppColors.border,
                              ),
                            );
                          }).toList(),
                        ),
                      ],
                    ),
                  ),

                const SizedBox(height: 8),

                Container(
                  color: AppColors.getSurface(context),
                  padding: const EdgeInsets.all(14),
                  child: Material(
                    color: AppColors.getSurface(context),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(
                              Icons.auto_awesome,
                              color: AppColors.primary,
                              size: 20,
                            ),
                            const SizedBox(width: 6),
                            Text(
                              'THÀNH PHẦN NỔI BẬT',
                              style: AppTypography.titleMedium.copyWith(
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        if (detail.skinType != null)
                          _buildInfoRow('Loại da phù hợp:', detail.skinType!),
                        if (detail.originCountry != null)
                          _buildInfoRow(
                            'Xuất xứ sản phẩm:',
                            detail.originCountry!,
                          ),
                        _buildInfoRow(
                          'Cồn khô:',
                          detail.hasAlcohol == null
                              ? 'Chưa công bố'
                              : detail.hasAlcohol!
                              ? 'Có'
                              : 'Không',
                        ),
                        _buildInfoRow(
                          'Hương liệu:',
                          detail.hasFragrance == null
                              ? 'Chưa công bố'
                              : detail.hasFragrance!
                              ? 'Có'
                              : 'Không',
                        ),
                        if (detail.attributeValues.any(
                          (item) =>
                              item.variantId == null ||
                              item.variantId == activeVariant?.id,
                        ))
                          const Padding(
                            padding: EdgeInsets.symmetric(vertical: 8),
                            child: Text(
                              'THUỘC TÍNH ĐẶC TRƯNG',
                              style: TextStyle(fontWeight: FontWeight.w700),
                            ),
                          ),
                        for (final attribute in detail.attributeValues.where(
                          (item) =>
                              item.variantId == null ||
                              item.variantId == activeVariant?.id,
                        ))
                          _buildInfoRow(
                            '${attribute.name}${attribute.variantId == null ? '' : ' (SKU)'}:',
                            attribute.displayValue,
                          ),
                        if (detail.ingredientsList.any(
                          (item) => item.isKeyActive,
                        ))
                          _buildInfoRow(
                            'Hoạt chất nổi bật:',
                            detail.ingredientsList
                                .where((item) => item.isKeyActive)
                                .map(
                                  (item) =>
                                      '${item.name}${item.concentration == null ? '' : ' ${item.concentrationLabel}'}',
                                )
                                .join(', '),
                          )
                        else if (detail.keyActivesSummary?.isNotEmpty == true)
                          _buildInfoRow(
                            'Hoạt chất nổi bật:',
                            detail.keyActivesSummary!,
                          ),
                        for (final ingredient in detail.ingredientsList)
                          ExpansionTile(
                            tilePadding: EdgeInsets.zero,
                            title: Text(
                              ingredient.name,
                              style: AppTypography.bodyMedium,
                            ),
                            subtitle: Text(
                              [
                                if (ingredient.inciName.isNotEmpty)
                                  ingredient.inciName,
                                if (ingredient.concentration != null)
                                  ingredient.concentrationLabel,
                                if (ingredient.isKeyActive) 'Hoạt chất nổi bật',
                              ].join(' • '),
                            ),
                            children: [
                              if (ingredient.functions.isNotEmpty)
                                _buildInfoRow(
                                  'Chức năng:',
                                  ingredient.functions.join(', '),
                                ),
                              if (ingredient.benefits.isNotEmpty)
                                _buildInfoRow(
                                  'Lợi ích:',
                                  ingredient.benefits.join('; '),
                                ),
                              if (ingredient.concerns.isNotEmpty)
                                _buildInfoRow(
                                  'Lưu ý:',
                                  ingredient.concerns.join('; '),
                                ),
                              if (ingredient.functions.isEmpty &&
                                  ingredient.benefits.isEmpty &&
                                  ingredient.concerns.isEmpty)
                                const Padding(
                                  padding: EdgeInsets.only(bottom: 8),
                                  child: Text(
                                    'Thông tin chi tiết chưa cập nhật.',
                                  ),
                                ),
                            ],
                          ),
                        if (detail.ingredients != null &&
                            detail.ingredients!.isNotEmpty) ...[
                          const SizedBox(height: 8),
                          ExpansionTile(
                            tilePadding: EdgeInsets.zero,
                            title: const Text(
                              'Bảng thành phần INCI đầy đủ',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            children: [
                              SelectableText(
                                detail.ingredients!,
                                style: AppTypography.bodySmall,
                              ),
                            ],
                          ),
                        ],
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 8),

                Container(
                  color: AppColors.getSurface(context),
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'MÔ TẢ SẢN PHẨM',
                        style: AppTypography.titleMedium.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        detail.description ??
                            detail.shortDescription ??
                            'Đang cập nhật nội dung...',
                        style: AppTypography.bodyMedium,
                      ),
                      if (detail.howToUse != null) ...[
                        const SizedBox(height: 12),
                        Text(
                          'HƯỚNG DẪN SỬ DỤNG',
                          style: AppTypography.titleMedium.copyWith(
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          detail.howToUse!,
                          style: AppTypography.bodyMedium,
                        ),
                      ],
                    ],
                  ),
                ),

                const SizedBox(height: 8),

                Container(
                  color: AppColors.getSurface(context),
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Wrap(
                        spacing: 12,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(
                                Icons.rate_review_outlined,
                                color: AppColors.primary,
                                size: 20,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                'ĐÁNH GIÁ (${detail.totalReviews})',
                                style: AppTypography.titleMedium.copyWith(
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ],
                          ),
                          TextButton.icon(
                            onPressed: () => _showAddReviewDialog(context),
                            icon: const Icon(
                              Icons.edit,
                              size: 14,
                              color: AppColors.primary,
                            ),
                            label: const Text(
                              'Viết đánh giá',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: AppColors.primary,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),

                      if (provider.reviewsError != null)
                        CatalogState(
                          message: provider.reviewsError!,
                          onRetry: () =>
                              provider.fetchReviewsForProduct(detail.id),
                        )
                      else if (provider.currentProductReviews.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 12),
                          child: Text(
                            'Chưa có đánh giá nào cho sản phẩm này. Hãy là người đầu tiên đánh giá!',
                            style: TextStyle(
                              fontSize: 12,
                              color: AppColors.textMuted,
                            ),
                          ),
                        )
                      else
                        ListView.separated(
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          itemCount: provider.currentProductReviews
                              .take(5)
                              .length,
                          separatorBuilder: (_, __) => const Divider(
                            height: 16,
                            color: AppColors.borderSubtle,
                          ),
                          itemBuilder: (context, idx) {
                            final rev = provider.currentProductReviews[idx];
                            return Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment:
                                      MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text(
                                      rev.authorName,
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 13,
                                      ),
                                    ),
                                    Row(
                                      children: List.generate(
                                        5,
                                        (i) => Icon(
                                          i < rev.rating
                                              ? Icons.star
                                              : Icons.star_border,
                                          size: 14,
                                          color: AppColors.star,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                if (rev.title != null &&
                                    rev.title!.isNotEmpty) ...[
                                  const SizedBox(height: 3),
                                  Text(
                                    rev.title!,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w600,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                                if (rev.content != null &&
                                    rev.content!.isNotEmpty) ...[
                                  const SizedBox(height: 3),
                                  Text(
                                    rev.content!,
                                    style: AppTypography.bodySmall,
                                  ),
                                ],
                              ],
                            );
                          },
                        ),
                    ],
                  ),
                ),

                const SizedBox(height: 8),

                if (provider.similarProducts.isNotEmpty)
                  Container(
                    color: AppColors.getSurface(context),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          child: Text(
                            'SẢN PHẨM TƯƠNG TỰ GỢI Ý',
                            style: AppTypography.titleMedium.copyWith(
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        const SizedBox(height: 10),
                        SizedBox(
                          height: 245,
                          child: ListView.separated(
                            padding: const EdgeInsets.symmetric(horizontal: 14),
                            scrollDirection: Axis.horizontal,
                            itemCount: provider.similarProducts.take(6).length,
                            separatorBuilder: (_, __) =>
                                const SizedBox(width: 8),
                            itemBuilder: (context, index) {
                              final p = provider.similarProducts[index];
                              return SizedBox(
                                width: 145,
                                child: AppProductCard(
                                  product: p,
                                  onTap: () {
                                    Navigator.pushReplacement(
                                      context,
                                      MaterialPageRoute(
                                        builder: (_) => ProductDetailScreen(
                                          productId: p.id,
                                        ),
                                      ),
                                    );
                                  },
                                  onAddToCart: () async {
                                    await addProductToCart(
                                      context,
                                      productId: p.id,
                                      variantId: 0,
                                      productName: p.name,
                                      variantName: 'Tiêu chuẩn',
                                      price: p.displayPrice,
                                      thumbnailUrl: p.thumbnailUrl,
                                    );
                                  },
                                ),
                              );
                            },
                          ),
                        ),
                      ],
                    ),
                  ),

                const SizedBox(height: 80),
              ],
            ),
          ),

          bottomNavigationBar: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: AppColors.getSurface(context),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.08),
                  blurRadius: 8,
                  offset: const Offset(0, -2),
                ),
              ],
            ),
            child: SafeArea(
              child: Row(
                children: [
                  InkWell(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const AiChatbotScreen(),
                        ),
                      );
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 8,
                      ),
                      child: const Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.smart_toy_outlined,
                            color: AppColors.primary,
                            size: 20,
                          ),
                          SizedBox(height: 2),
                          Text(
                            'Tư vấn AI',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                              color: AppColors.primary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(width: 8),

                  Expanded(
                    child: OutlinedButton(
                      onPressed: !canBuy
                          ? null
                          : () async {
                              await addProductToCart(
                                context,
                                productId: detail.id,
                                variantId: activeVariant?.id ?? 0,
                                productName: detail.name,
                                variantName:
                                    activeVariant?.variantName ?? 'Tiêu chuẩn',
                                price: currentPrice,
                                thumbnailUrl: detail.thumbnailUrl,
                              );
                            },
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                      child: const Text('Thêm vào giỏ'),
                    ),
                  ),

                  const SizedBox(width: 8),

                  Expanded(
                    child: ElevatedButton(
                      onPressed: !canBuy
                          ? null
                          : () async {
                              final success = await addProductToCart(
                                context,
                                productId: detail.id,
                                variantId: activeVariant?.id ?? 0,
                                productName: detail.name,
                                variantName:
                                    activeVariant?.variantName ?? 'Tiêu chuẩn',
                                price: currentPrice,
                                thumbnailUrl: detail.thumbnailUrl,
                              );
                              if (!success || !context.mounted) return;
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => const CartScreen(),
                                ),
                              );
                            },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.dealRed,
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                      child: const Text('Mua ngay'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 130,
            child: Text(
              label,
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.textMuted,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: TextStyle(
                fontSize: 12,
                color: AppColors.getTextMain(context),
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
