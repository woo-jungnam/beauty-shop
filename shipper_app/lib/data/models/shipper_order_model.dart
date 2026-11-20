import '../../core/constants/api_constants.dart';

class ShipperOrderModel {
  final int id;
  final String orderNumber;
  final String customerName;
  final String customerPhone;
  final String shippingAddress;
  final double totalAmount;
  final double codAmount;
  final String paymentMethod;
  final String paymentStatus;
  final String status;
  final String? deliveryProofUrl;
  final String? deliveredNotes;
  final String? notes;
  final DateTime? createdAt;
  final DateTime? deliveredAt;
  final DateTime? assignedAt;
  final List<String> itemsSummary;

  ShipperOrderModel({
    required this.id,
    required this.orderNumber,
    required this.customerName,
    required this.customerPhone,
    required this.shippingAddress,
    required this.totalAmount,
    required this.codAmount,
    required this.paymentMethod,
    required this.paymentStatus,
    required this.status,
    this.deliveryProofUrl,
    this.deliveredNotes,
    this.notes,
    this.createdAt,
    this.deliveredAt,
    this.assignedAt,
    this.itemsSummary = const [],
  });

  static String? _resolveMediaUrl(String? url) {
    if (url == null || url.isEmpty) return null;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    final base = ApiConstants.baseUrl.replaceAll(RegExp(r'/+$'), '');
    final path = url.startsWith('/') ? url : '/$url';
    return '$base$path';
  }

  factory ShipperOrderModel.fromJson(Map<String, dynamic> json) {
    return ShipperOrderModel(
      id: json['id'] as int? ?? 0,
      orderNumber: json['orderNumber']?.toString() ?? '',
      customerName: json['customerName']?.toString() ?? 'Khách hàng',
      customerPhone: json['customerPhone']?.toString() ?? '',
      shippingAddress: json['shippingAddress']?.toString() ?? 'Chưa có địa chỉ',
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      codAmount: (json['codAmount'] as num?)?.toDouble() ?? 0.0,
      paymentMethod: json['paymentMethod']?.toString() ?? 'COD',
      paymentStatus: json['paymentStatus']?.toString() ?? 'PENDING',
      status: json['status']?.toString() ?? 'SHIPPED',
      deliveryProofUrl: _resolveMediaUrl(json['deliveryProofUrl']?.toString()),
      deliveredNotes: json['deliveredNotes']?.toString(),
      notes: json['notes']?.toString(),
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
    );
  }

  bool get isDelivered => status == 'DELIVERED';
  bool get isShipped => status == 'SHIPPED';
  bool get isCod => codAmount > 0;
}
