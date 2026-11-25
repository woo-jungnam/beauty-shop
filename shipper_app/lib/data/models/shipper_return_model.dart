import '../../core/constants/api_constants.dart';

class ShipperReturnModel {
  final int id;
  final int orderId;
  final String orderNumber;
  final String status;
  final String returnMethod;
  final String reason;
  final String? decisionReason;
  final double refundAmount;
  final String customerName;
  final String customerPhone;
  final String pickupAddress;
  final String paymentMethod;
  final int? pickupShipperId;
  final String? pickupShipperName;
  final String? pickupShipperPhone;
  final DateTime? pickupAssignedAt;
  final DateTime? pickupStartedAt;
  final DateTime? pickedUpAt;
  final String? pickupProofUrl;
  final String? pickupNotes;
  final String? pickupFailureReason;
  final int pickupAttemptCount;
  final DateTime? handoverRequestedAt;
  final String? handoverNotes;
  final DateTime? receivedAt;
  final DateTime? createdAt;
  final List<String> evidenceUrls;
  final List<String> itemsSummary;
  final List<ShipperReturnItem> items;

  const ShipperReturnModel({
    required this.id,
    required this.orderId,
    required this.orderNumber,
    required this.status,
    this.returnMethod = 'SHIPPER_PICKUP',
    required this.reason,
    this.decisionReason,
    required this.refundAmount,
    required this.customerName,
    required this.customerPhone,
    required this.pickupAddress,
    this.paymentMethod = 'COD',
    this.pickupShipperId,
    this.pickupShipperName,
    this.pickupShipperPhone,
    this.pickupAssignedAt,
    this.pickupStartedAt,
    this.pickedUpAt,
    this.pickupProofUrl,
    this.pickupNotes,
    this.pickupFailureReason,
    this.pickupAttemptCount = 0,
    this.handoverRequestedAt,
    this.handoverNotes,
    this.receivedAt,
    this.createdAt,
    this.evidenceUrls = const [],
    this.itemsSummary = const [],
    this.items = const [],
  });

  factory ShipperReturnModel.fromJson(Map<String, dynamic> json) {
    return ShipperReturnModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      orderId: (json['orderId'] as num?)?.toInt() ?? 0,
      orderNumber: json['orderNumber']?.toString() ?? '',
      status: json['status']?.toString() ?? 'PICKUP_ASSIGNED',
      returnMethod: json['returnMethod']?.toString() ?? 'SHIPPER_PICKUP',
      reason: json['reason']?.toString() ?? '',
      decisionReason: json['decisionReason']?.toString(),
      refundAmount: (json['refundAmount'] as num?)?.toDouble() ?? 0.0,
      customerName: json['customerName']?.toString() ?? 'Khách hàng',
      customerPhone: json['customerPhone']?.toString() ?? '',
      pickupAddress: json['pickupAddress']?.toString().trim() ?? '',
      paymentMethod: json['paymentMethod']?.toString() ?? 'COD',
      pickupShipperId: (json['pickupShipperId'] as num?)?.toInt(),
      pickupShipperName: json['pickupShipperName']?.toString(),
      pickupShipperPhone: json['pickupShipperPhone']?.toString(),
      pickupAssignedAt: DateTime.tryParse(
        json['pickupAssignedAt']?.toString() ?? '',
      ),
      pickupStartedAt: DateTime.tryParse(
        json['pickupStartedAt']?.toString() ?? '',
      ),
      pickedUpAt: DateTime.tryParse(json['pickedUpAt']?.toString() ?? ''),
      pickupProofUrl: ApiConstants.resolveMediaUrl(
        json['pickupProofUrl']?.toString(),
      ),
      pickupNotes: json['pickupNotes']?.toString(),
      pickupFailureReason: json['pickupFailureReason']?.toString(),
      pickupAttemptCount: (json['pickupAttemptCount'] as num?)?.toInt() ?? 0,
      handoverRequestedAt: DateTime.tryParse(
        json['handoverRequestedAt']?.toString() ?? '',
      ),
      handoverNotes: json['handoverNotes']?.toString(),
      receivedAt: DateTime.tryParse(json['receivedAt']?.toString() ?? ''),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? ''),
      evidenceUrls:
          (json['evidenceUrls'] as List<dynamic>?)
              ?.map((e) => ApiConstants.resolveMediaUrl(e?.toString()))
              .whereType<String>()
              .toList() ??
          const [],
      itemsSummary:
          (json['itemsSummary'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      items: switch (json['items']) {
        List<dynamic> rows =>
          rows
              .whereType<Map<String, dynamic>>()
              .map(ShipperReturnItem.fromJson)
              .toList(),
        _ => const [],
      },
    );
  }

  bool get isPickupAssigned => status == 'PICKUP_ASSIGNED';
  bool get isPickedUp => status == 'PICKED_UP';
  bool get isReceived => status == 'RECEIVED';
  bool get isRefunded => status == 'REFUNDED';
  bool get isStarted => pickupStartedAt != null;
  bool get isHandoverRequested => handoverRequestedAt != null;

  String get displayPickupAddress =>
      pickupAddress.isEmpty ? 'Chưa có địa chỉ thu hồi' : pickupAddress;
}

class ShipperReturnItem {
  final int orderItemId;
  final int? productId;
  final int? variantId;
  final String? sku;
  final String productName;
  final String? variantName;
  final String? imageUrl;
  final int returnQuantity;
  final int purchasedQuantity;
  final double? unitPrice;

  const ShipperReturnItem({
    required this.orderItemId,
    this.productId,
    this.variantId,
    this.sku,
    required this.productName,
    this.variantName,
    this.imageUrl,
    required this.returnQuantity,
    required this.purchasedQuantity,
    this.unitPrice,
  });

  factory ShipperReturnItem.fromJson(Map<String, dynamic> json) {
    return ShipperReturnItem(
      orderItemId: (json['orderItemId'] as num?)?.toInt() ?? 0,
      productId: (json['productId'] as num?)?.toInt(),
      variantId: (json['variantId'] as num?)?.toInt(),
      sku: json['sku']?.toString(),
      productName: json['productName']?.toString() ?? 'Sản phẩm',
      variantName: json['variantName']?.toString(),
      imageUrl: ApiConstants.resolveMediaUrl(json['imageUrl']?.toString()),
      returnQuantity: (json['returnQuantity'] as num?)?.toInt() ?? 1,
      purchasedQuantity: (json['purchasedQuantity'] as num?)?.toInt() ?? 1,
      unitPrice: (json['unitPrice'] as num?)?.toDouble(),
    );
  }
}

