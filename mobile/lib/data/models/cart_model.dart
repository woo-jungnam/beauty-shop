import '../../core/utils/image_utils.dart';

class CartItem {
  final int id;
  final int productId;
  final int variantId;
  final String productName;
  final String variantName;
  final String? thumbnailUrl;
  final double price;
  final double originalPrice;
  int quantity;
  bool isSelected;

  CartItem({
    required this.id,
    required this.productId,
    required this.variantId,
    required this.productName,
    required this.variantName,
    this.thumbnailUrl,
    required this.price,
    required this.originalPrice,
    required this.quantity,
    this.isSelected = true,
  });

  double get totalPrice => price * quantity;

  factory CartItem.fromJson(Map<String, dynamic> json) {
    return CartItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      productId: (json['productId'] as num?)?.toInt() ?? 0,
      variantId: (json['variantId'] as num?)?.toInt() ?? 0,
      productName:
          json['productName']?.toString() ??
          json['variantName']?.toString() ??
          json['sku']?.toString() ??
          'Sản phẩm',
      variantName:
          json['variantName']?.toString() ??
          json['sku']?.toString() ??
          'Tiêu chuẩn',
      thumbnailUrl: ImageUtils.resolveUrl(
        json['imageUrl']?.toString() ??
            json['thumbnailUrl']?.toString() ??
            json['image']?.toString(),
      ),
      price: (json['price'] as num?)?.toDouble() ?? 0.0,
      originalPrice:
          (json['originalPrice'] as num?)?.toDouble() ??
          (json['price'] as num?)?.toDouble() ??
          0.0,
      quantity: (json['quantity'] as num?)?.toInt() ?? 0,
      isSelected: true,
    );
  }
}

class CartResponse {
  final List<CartItem> items;
  final double subtotal;
  final double shippingFee;
  final double discountAmount;
  final double totalAmount;
  final String? membershipTier;
  final int? membershipDiscountPercentage;
  final double estimatedMembershipDiscount;

  CartResponse({
    required this.items,
    required this.subtotal,
    this.shippingFee = 0.0,
    this.discountAmount = 0.0,
    required this.totalAmount,
    this.membershipTier,
    this.membershipDiscountPercentage,
    this.estimatedMembershipDiscount = 0.0,
  });

  factory CartResponse.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'] as List<dynamic>? ?? [];
    final parsedItems = rawItems
        .map((i) => CartItem.fromJson(i as Map<String, dynamic>))
        .toList();

    double calculatedSubtotal = 0;
    for (final item in parsedItems) {
      calculatedSubtotal += item.totalPrice;
    }

    final sub = (json['subtotal'] as num?)?.toDouble() ??
        (json['totalPrice'] as num?)?.toDouble() ??
        calculatedSubtotal;
    final shipping = (json['shippingFee'] as num?)?.toDouble() ??
        (json['estimatedShippingFee'] as num?)?.toDouble() ??
        0.0;
    final disc = (json['discountAmount'] as num?)?.toDouble() ?? 0.0;
    final memTier = json['membershipTier']?.toString();
    final memPercent = (json['membershipDiscountPercentage'] as num?)?.toInt();
    final memDisc = (json['estimatedMembershipDiscount'] as num?)?.toDouble() ?? 0.0;
    final tot = (json['totalAmount'] as num?)?.toDouble() ??
        (json['totalPrice'] as num?)?.toDouble() ??
        (sub + shipping - disc - memDisc);

    return CartResponse(
      items: parsedItems,
      subtotal: sub,
      shippingFee: shipping,
      discountAmount: disc,
      totalAmount: tot,
      membershipTier: memTier,
      membershipDiscountPercentage: memPercent,
      estimatedMembershipDiscount: memDisc,
    );
  }

  factory CartResponse.empty() {
    return CartResponse(items: [], subtotal: 0.0, totalAmount: 0.0);
  }
}
