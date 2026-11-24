import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_colors.dart';
import '../../../data/models/shipper_order_model.dart';

final _money = NumberFormat.currency(
  locale: 'vi_VN',
  symbol: '₫',
  decimalDigits: 0,
);
final _date = DateFormat('dd/MM/yyyy HH:mm');

class ShipperOrderDetailScreen extends StatelessWidget {
  const ShipperOrderDetailScreen({super.key, required this.order});

  final ShipperOrderModel order;

  @override
  Widget build(BuildContext context) {
    final status = switch (order.status) {
      'SHIPPED' => order.deliveryStartedAt == null ? 'Cần giao' : 'Đang giao',
      'DELIVERED' => 'Đã giao',
      'DELIVERY_FAILED' => 'Giao thất bại',
      'RETURNING_TO_SENDER' => 'Đang hoàn về kho',
      'RETURN_TO_SENDER' => 'Đã hoàn về kho',
      'RETURNED' => 'Đã trả hàng',
      'CANCELLED' => 'Đã hủy',
      'REFUNDED' => 'Đã hoàn tiền',
      _ => order.status,
    };
    final payment = switch (order.paymentStatus) {
      'PAID' => 'Đã thanh toán',
      'PENDING' => 'Chờ thanh toán',
      'FAILED' => 'Thanh toán thất bại',
      'REFUND_PENDING' => 'Chờ hoàn tiền',
      'REFUNDED' => 'Đã hoàn tiền',
      _ => order.paymentStatus,
    };
    final method = switch (order.paymentMethod) {
      'COD' => 'Thu hộ khi giao (COD)',
      'BANK' => 'Chuyển khoản',
      'CASH' => 'Tiền mặt',
      _ => order.paymentMethod,
    };
    return Scaffold(
      appBar: AppBar(title: const Text('Chi tiết đơn giao')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          _DetailSection(
            title: '#${order.orderNumber}',
            children: [
              _fact('Trạng thái', status),
              if (order.hasOrderDetails) ...[
                _fact('Người nhận', order.customerName),
                _fact('Điện thoại', order.customerPhone),
                _fact('Địa chỉ', order.displayShippingAddress),
              ],
              _fact('Đơn vị vận chuyển', order.carrierName),
              _fact('Mã vận đơn', order.trackingCode),
              _fact('Ghi chú đơn', order.notes),
              _fact('Lý do hủy', order.cancelReason),
            ],
          ),
          if (order.hasOrderDetails) ...[
            _DetailSection(
              title: 'Sản phẩm',
              children: [OrderItemsSection(order: order)],
            ),
            _DetailSection(
              title: 'Thanh toán',
              children: [
                _fact('Phương thức', method),
                _fact('Trạng thái', payment),
                _amount('Tiền hàng', order.subTotal),
                _amount('Phí vận chuyển', order.shippingFee),
                _amount('Giảm giá', order.discountAmount),
                _amount('Tổng đơn', order.totalAmount),
                _amount('Đã thanh toán', order.paidAmount),
                if (order.paymentMethod == 'COD') ...[
                  _amount('COD cần thu', order.codAmount),
                  if (order.codCollected > 0) ...[
                    _amount('COD đã thu', order.codCollected),
                    _amount('COD đã bàn giao', order.codRemitted),
                    _amount(
                      'COD chờ bàn giao',
                      order.codCollected - order.codRemitted,
                    ),
                  ],
                ],
              ],
            ),
          ],
          _DetailSection(
            title: 'Mốc giao hàng',
            children: [
              _time(
                order.hasOrderDetails ? 'Tạo đơn' : 'Ghi nhận',
                order.createdAt,
              ),
              _time('Phân công', order.assignedAt),
              _time('Bắt đầu giao', order.deliveryStartedAt),
              _time('Hoàn tất giao', order.deliveredAt),
              _time('Thanh toán', order.paidAt),
            ],
          ),
          if (order.isDelivered ||
              order.deliveryProofUrl != null ||
              (order.deliveredNotes?.trim().isNotEmpty ?? false))
            _DetailSection(
              title: 'Bằng chứng giao hàng',
              children: [
                if (order.deliveryProofUrl case final proof?)
                  _OrderPhoto(
                    url: proof,
                    label: 'Ảnh xác nhận giao hàng',
                    height: 180,
                    fit: BoxFit.contain,
                  )
                else
                  const Text('Chưa có ảnh xác nhận giao hàng.'),
                _fact('Ghi chú giao hàng', order.deliveredNotes),
              ],
            ),
        ],
      ),
    );
  }

  static Widget _fact(String label, String? value) {
    if (value == null || value.trim().isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: '$label: ',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            TextSpan(text: value),
          ],
        ),
      ),
    );
  }

  static Widget _amount(String label, double? value) =>
      _fact(label, value == null ? null : _money.format(value));

  static Widget _time(String label, DateTime? value) =>
      _fact(label, value == null ? null : _date.format(value.toLocal()));
}

class _DetailSection extends StatelessWidget {
  const _DetailSection({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            ...children,
          ],
        ),
      ),
    );
  }
}

class OrderItemsSection extends StatelessWidget {
  const OrderItemsSection({
    super.key,
    required this.order,
    this.preview = false,
  });

  final ShipperOrderModel order;
  final bool preview;

  @override
  Widget build(BuildContext context) {
    if (order.items.isEmpty) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (order.itemsSummary.isEmpty && !preview)
            const Text('Chưa có thông tin dòng hàng.'),
          for (final item
              in preview ? order.itemsSummary.take(2) : order.itemsSummary)
            Padding(padding: const EdgeInsets.only(top: 6), child: Text(item)),
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final item in preview ? order.items.take(2) : order.items)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _OrderPhoto(
                  url: item.imageUrl,
                  label: item.productName,
                  width: 56,
                  height: 56,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        item.productName,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      if (item.variantName?.trim().isNotEmpty ?? false)
                        Text(item.variantName!),
                      if (item.quantity case final quantity?)
                        Text('Số lượng: $quantity'),
                      if (!preview) ...[
                        if (item.sku?.trim().isNotEmpty ?? false)
                          Text('SKU: ${item.sku}'),
                        if (item.price case final price?)
                          Text('Đơn giá: ${_money.format(price)}'),
                        if ((item.discount ?? 0) > 0)
                          Text(
                            'Giảm mỗi sản phẩm: ${_money.format(item.discount!)}',
                          ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        if (preview && order.items.length > 2)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text('Còn ${order.items.length - 2} dòng hàng'),
          ),
      ],
    );
  }
}

class _OrderPhoto extends StatelessWidget {
  const _OrderPhoto({
    required this.url,
    required this.label,
    required this.height,
    this.width = double.infinity,
    this.fit = BoxFit.cover,
  });

  final String? url;
  final String label;
  final double height;
  final double width;
  final BoxFit fit;

  Widget _image(String url, BoxFit fit) => CachedNetworkImage(
    imageUrl: url,
    fit: fit,
    placeholder: (_, _) => const Center(child: Icon(Icons.image_outlined)),
    errorWidget: (_, _, _) =>
        const Center(child: Icon(Icons.broken_image_outlined)),
  );

  @override
  Widget build(BuildContext context) {
    final photo = url;
    return SizedBox(
      width: width,
      height: height,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Material(
          color: AppColors.primarySurface,
          child: photo == null
              ? const Center(child: Icon(Icons.image_not_supported_outlined))
              : Semantics(
                  label: 'Mở ảnh $label',
                  button: true,
                  child: InkWell(
                    onTap: () => showDialog<void>(
                      context: context,
                      builder: (context) => Dialog(
                        child: SizedBox(
                          height: MediaQuery.sizeOf(context).height * .72,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              ListTile(
                                title: Text(label),
                                trailing: IconButton(
                                  tooltip: 'Đóng',
                                  onPressed: () => Navigator.pop(context),
                                  icon: const Icon(Icons.close),
                                ),
                              ),
                              Expanded(
                                child: InteractiveViewer(
                                  child: Center(
                                    child: _image(photo, BoxFit.contain),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    child: _image(photo, fit),
                  ),
                ),
        ),
      ),
    );
  }
}
