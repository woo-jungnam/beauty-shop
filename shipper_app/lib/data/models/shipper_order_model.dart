import '../../core/constants/api_constants.dart';

class ShipperOrderModel {
  final int id;
  final String orderNumber;
  final String customerName;
  final String customerPhone;
  final String shippingAddress;
  final double totalAmount;
  final double? subTotal;
  final double? shippingFee;
  final double? discountAmount;
  final double? paidAmount;
  final bool hasOrderDetails;
  final double codAmount;
  final double codCollected;
  final double codRemitted;
  final DateTime? deliveryStartedAt;
  final String paymentMethod;
  final String paymentStatus;
  final String status;
  final String? deliveryProofUrl;
  final String? deliveredNotes;
  final String? notes;
  final String? carrierName;
  final String? trackingCode;
  final String? cancelReason;
  final DateTime? paidAt;
  final DateTime? createdAt;
  final DateTime? deliveredAt;
  final DateTime? assignedAt;
  final List<String> itemsSummary;
  final List<ShipperOrderItem> items;

  ShipperOrderModel({
    required this.id,
    required this.orderNumber,
    required this.customerName,
    required this.customerPhone,
    required this.shippingAddress,
    required this.totalAmount,
    this.subTotal,
    this.shippingFee,
    this.discountAmount,
    this.paidAmount,
    this.hasOrderDetails = true,
    required this.codAmount,
    this.codCollected = 0,
    this.codRemitted = 0,
    this.deliveryStartedAt,
    required this.paymentMethod,
    required this.paymentStatus,
    required this.status,
    this.deliveryProofUrl,
    this.deliveredNotes,
    this.notes,
    this.carrierName,
    this.trackingCode,
    this.cancelReason,
    this.paidAt,
    this.createdAt,
    this.deliveredAt,
    this.assignedAt,
    this.itemsSummary = const [],
    this.items = const [],
  });

  static String? _resolveMediaUrl(String? url) {
    final value = url?.trim();
    if (value == null || value.isEmpty) return null;
    final uri = Uri.tryParse(value);
    if (uri == null || uri.userInfo.isNotEmpty) return null;
    if (uri.hasScheme) {
      return {'http', 'https'}.contains(uri.scheme) && uri.host.isNotEmpty
          ? uri.toString()
          : null;
    }
    if (uri.hasAuthority) return null;
    return Uri.parse('${ApiConstants.baseUrl}/').resolveUri(uri).toString();
  }

  factory ShipperOrderModel.fromJson(Map<String, dynamic> json) {
    return ShipperOrderModel(
      id: json['id'] as int? ?? 0,
      orderNumber: json['orderNumber']?.toString() ?? '',
      customerName: json['customerName']?.toString() ?? 'Khách hàng',
      customerPhone: json['customerPhone']?.toString() ?? '',
      shippingAddress: json['shippingAddress']?.toString().trim() ?? '',
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      subTotal: (json['subTotal'] as num?)?.toDouble(),
      shippingFee: (json['shippingFee'] as num?)?.toDouble(),
      discountAmount: (json['discountAmount'] as num?)?.toDouble(),
      paidAmount: (json['paidAmount'] as num?)?.toDouble(),
      hasOrderDetails: json['totalAmount'] != null,
      codAmount: (json['codAmount'] as num?)?.toDouble() ?? 0.0,
      codCollected: (json['codCollected'] as num?)?.toDouble() ?? 0,
      codRemitted: (json['codRemitted'] as num?)?.toDouble() ?? 0,
      deliveryStartedAt: DateTime.tryParse(
        json['deliveryStartedAt']?.toString() ?? '',
      ),
      paymentMethod: json['paymentMethod']?.toString() ?? 'COD',
      paymentStatus: json['paymentStatus']?.toString() ?? 'PENDING',
      status: json['status']?.toString() ?? 'SHIPPED',
      deliveryProofUrl: _resolveMediaUrl(json['deliveryProofUrl']?.toString()),
      deliveredNotes: json['deliveredNotes']?.toString(),
      notes: json['notes']?.toString(),
      carrierName: json['carrierName']?.toString(),
      trackingCode: json['trackingCode']?.toString(),
      cancelReason: json['cancelReason']?.toString(),
      paidAt: DateTime.tryParse(json['paidAt']?.toString() ?? ''),
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString())
          : null,
      deliveredAt: json['deliveredAt'] != null
          ? DateTime.tryParse(json['deliveredAt'].toString())
          : null,
      assignedAt: DateTime.tryParse(json['assignedAt']?.toString() ?? ''),
      itemsSummary:
          (json['itemsSummary'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      items: switch (json['items']) {
        List<dynamic> items =>
          items
              .whereType<Map<String, dynamic>>()
              .map(ShipperOrderItem.fromJson)
              .toList(),
        _ => const [],
      },
    );
  }

  bool get isDelivered => status == 'DELIVERED';

  String get displayShippingAddress =>
      shippingAddress.isEmpty ? 'Chưa có địa chỉ' : shippingAddress;
  bool get isShipped => status == 'SHIPPED';
  bool get isCod => codAmount > 0;
}

class ShipperOrderItem {
  final String productName;
  final String? variantName;
  final String? sku;
  final String? imageUrl;
  final int? quantity;
  final double? price;
  final double? discount;

  const ShipperOrderItem({
    required this.productName,
    this.variantName,
    this.sku,
    this.imageUrl,
    this.quantity,
    this.price,
    this.discount,
  });

  factory ShipperOrderItem.fromJson(Map<String, dynamic> json) {
    return ShipperOrderItem(
      productName: json['productName']?.toString() ?? 'Sản phẩm',
      variantName: json['variantName']?.toString(),
      sku: json['sku']?.toString(),
      imageUrl: ShipperOrderModel._resolveMediaUrl(
        json['imageUrl']?.toString(),
      ),
      quantity: (json['quantity'] as num?)?.toInt(),
      price: (json['price'] as num?)?.toDouble(),
      discount: (json['discount'] as num?)?.toDouble(),
    );
  }
}
