import '../../core/utils/idempotency_key.dart';

class PaymentInstruction {
  final String? method;
  final String? instructionMessage;
  final String? bankName;
  final String? bankAccountName;
  final String? bankAccountNumber;
  final String? transferSyntax;
  final String? qrCodeUrl;

  PaymentInstruction({
    this.method,
    this.instructionMessage,
    this.bankName,
    this.bankAccountName,
    this.bankAccountNumber,
    this.transferSyntax,
    this.qrCodeUrl,
  });

  factory PaymentInstruction.fromJson(Map<String, dynamic> json) {
    return PaymentInstruction(
      method: json['method']?.toString(),
      instructionMessage: json['instructionMessage']?.toString(),
      bankName: json['bankName']?.toString(),
      bankAccountName: json['bankAccountName']?.toString(),
      bankAccountNumber: json['bankAccountNumber']?.toString(),
      transferSyntax: json['transferSyntax']?.toString(),
      qrCodeUrl: json['qrCodeUrl']?.toString(),
    );
  }
}

class OrderItem {
  final int id;
  final int? productId;
  final int? variantId;
  final String productName;
  final String variantName;
  final String? thumbnailUrl;
  final double price;
  final int quantity;

  OrderItem({
    required this.id,
    this.productId,
    this.variantId,
    required this.productName,
    this.variantName = 'Tiêu chuẩn',
    this.thumbnailUrl,
    required this.price,
    required this.quantity,
  });

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    return OrderItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      productId: (json['productId'] as num?)?.toInt(),
      variantId: (json['variantId'] as num?)?.toInt(),
      productName: json['productName']?.toString() ?? json['name']?.toString() ?? 'Sản phẩm',
      variantName: json['variantName']?.toString() ?? 'Tiêu chuẩn',
      thumbnailUrl: json['thumbnailUrl']?.toString() ?? json['image']?.toString(),
      price: (json['price'] as num?)?.toDouble() ?? 0.0,
      quantity: (json['quantity'] as num?)?.toInt() ?? 1,
    );
  }
}

class OrderResponse {
  final int id;
  final String orderCode;
  final String status;
  final double totalAmount;
  final double subTotal;
  final double shippingFee;
  final double discountAmount;
  final String? customerName;
  final String? customerPhone;
  final String? shippingAddress;
  final String? paymentMethod;
  final String? paymentStatus;
  final String? paymentDeadline;
  final DateTime? createdAt;
  final PaymentInstruction? paymentInstruction;
  final List<OrderItem> items;

  OrderResponse({
    required this.id,
    required this.orderCode,
    required this.status,
    required this.totalAmount,
    this.subTotal = 0.0,
    this.shippingFee = 0.0,
    this.discountAmount = 0.0,
    this.customerName,
    this.customerPhone,
    this.shippingAddress,
    this.paymentMethod,
    this.paymentStatus,
    this.paymentDeadline,
    this.createdAt,
    this.paymentInstruction,
    this.items = const [],
  });

  bool get isPaid => paymentStatus == 'PAID';
  bool get isCancelled => status == 'CANCELLED';

  String get statusLabel => switch (status) {
    'PENDING' => 'Chờ xử lý',
    'CONFIRMED' => 'Đã xác nhận',
    'PROCESSING' => 'Đang đóng gói',
    'SHIPPED' => 'Đang giao hàng',
    'DELIVERED' => 'Đã giao hàng',
    'CANCELLED' => 'Đã hủy',
    'RETURNED' => 'Đã trả hàng',
    _ => status,
  };

  factory OrderResponse.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'] as List<dynamic>? ?? [];
    return OrderResponse(
      id: ((json['id'] ?? json['orderId']) as num?)?.toInt() ?? 0,
      orderCode: json['orderCode']?.toString() ?? json['orderNumber']?.toString() ?? 'ORD-${json['id']}',
      status: json['status']?.toString() ?? 'PENDING',
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      subTotal: (json['subTotal'] as num?)?.toDouble() ?? 0.0,
      shippingFee: (json['shippingFee'] as num?)?.toDouble() ?? 0.0,
      discountAmount: (json['discountAmount'] as num?)?.toDouble() ?? 0.0,
      customerName: json['customerName']?.toString(),
      customerPhone: json['customerPhone']?.toString(),
      shippingAddress: json['shippingAddress']?.toString(),
      paymentMethod: json['paymentMethod']?.toString() ?? 'COD',
      paymentStatus: json['paymentStatus']?.toString() ?? 'PENDING',
      paymentDeadline: json['paymentDeadline']?.toString(),
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
      paymentInstruction: json['paymentInstruction'] != null
          ? PaymentInstruction.fromJson(json['paymentInstruction'] as Map<String, dynamic>)
          : null,
      items: rawItems.map((i) => OrderItem.fromJson(i as Map<String, dynamic>)).toList(),
    );
  }
}

class CheckoutRequest {
  final String idempotencyKey;
  final String? sessionId;
  final String customerName;
  final String customerPhone;
  final String shippingAddress;
  final String ward;
  final String district;
  final String city;
  final String paymentMethod; // COD or BANK
  final String? notes;
  final String? voucherCode;

  CheckoutRequest({
    String? idempotencyKey,
    this.sessionId,
    required this.customerName,
    required this.customerPhone,
    required this.shippingAddress,
    this.ward = 'Phường Bến Nghé',
    this.district = 'Quận 1',
    this.city = 'TP. Hồ Chí Minh',
    this.paymentMethod = 'BANK',
    this.notes,
    this.voucherCode,
  }) : idempotencyKey = idempotencyKey ?? generateIdempotencyKey();

  Map<String, dynamic> toJson() => {
    'customerName': customerName,
    'customerPhone': customerPhone,
    'shippingAddress': shippingAddress,
    'ward': ward,
    'district': district,
    'city': city,
    'paymentMethod': paymentMethod,
    if (notes != null && notes!.isNotEmpty) 'notes': notes,
    if (voucherCode != null && voucherCode!.isNotEmpty) 'voucherCode': voucherCode,
    if (sessionId != null) 'sessionId': sessionId,
  };
}
