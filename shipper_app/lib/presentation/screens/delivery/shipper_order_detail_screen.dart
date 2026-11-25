import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/storage/shipper_settings_store.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';
import '../../../data/models/shipper_order_model.dart';
import '../../../providers/shipper_provider.dart';
import 'delivery_confirmation_screen.dart';

class ShipperOrderDetailScreen extends StatefulWidget {
  const ShipperOrderDetailScreen({
    super.key,
    required this.order,
    this.enableDeliveryActions = false,
  });

  final ShipperOrderModel order;
  final bool enableDeliveryActions;

  @override
  State<ShipperOrderDetailScreen> createState() =>
      _ShipperOrderDetailScreenState();
}

class _ShipperOrderDetailScreenState extends State<ShipperOrderDetailScreen> {
  late ShipperOrderModel _order = widget.order;
  bool _busy = false;

  Future<void> _reloadOrder() async {
    if (!widget.enableDeliveryActions) return;
    try {
      final latest = await context.read<ShipperProvider>().fetchOrderDetail(
        _order.id,
      );
      if (mounted) {
        setState(() => _order = latest);
      }
    } catch (_) {
      // Keep current snapshot if background detail reload fails.
    }
  }

  Future<void> _startDelivery() async {
    if (_busy || !widget.enableDeliveryActions) return;
    if (ShipperSettingsStore.instance.confirmBeforeStart) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Bắt đầu giao hàng'),
          content: Text('Xác nhận bắt đầu đi giao đơn #${_order.orderNumber}?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Bắt đầu'),
            ),
          ],
        ),
      );
      if (ok != true || !mounted) return;
    }
    setState(() => _busy = true);
    try {
      await context.read<ShipperProvider>().startDelivery(_order.id);
      await _reloadOrder();
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Đã bắt đầu giao hàng')));
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reportFailure() async {
    if (_busy || !widget.enableDeliveryActions) return;
    final reason = await showDeliveryFailureDialog(context);
    if (!mounted || reason == null) return;
    setState(() => _busy = true);
    try {
      await context.read<ShipperProvider>().reportFailure(_order.id, reason);
      await _reloadOrder();
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Đã báo giao thất bại')));
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openConfirmation() async {
    if (_busy || !widget.enableDeliveryActions) return;
    final completed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => DeliveryConfirmationScreen(order: _order),
      ),
    );
    if (completed == true && mounted) {
      await _reloadOrder();
    }
  }

  @override
  Widget build(BuildContext context) {
    final style = ShipperStatusStyle.fromOrder(_order);
    final showActionFooter =
        widget.enableDeliveryActions && _order.isShipped;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Chi tiết đơn giao',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          if (widget.enableDeliveryActions)
            IconButton(
              tooltip: 'Làm mới',
              onPressed: _busy ? null : _reloadOrder,
              icon: const Icon(Icons.refresh_rounded),
            ),
        ],
      ),
      bottomNavigationBar: showActionFooter ? _buildBottomActions() : null,
      body: ShipperPageContainer(
        child: ListView(
          padding: ShipperLayout.pagePadding,
          children: [
            _buildStatusBanner(style),
            if (_order.hasOrderDetails) ...[
              _buildRecipientCard(),
              _DetailSection(
                title: 'Sản phẩm (${_order.items.isNotEmpty ? _order.items.length : _order.itemsSummary.length})',
                children: [OrderItemsSection(order: _order)],
              ),
              _buildPaymentSection(),
            ] else
              _DetailSection(
                title: 'Thông tin đơn hàng',
                children: [
                  _fact(context, 'Đơn vị vận chuyển', _order.carrierName),
                  _fact(context, 'Mã vận đơn', _order.trackingCode),
                  _fact(context, 'Ghi chú đơn', _order.notes),
                  _fact(context, 'Lý do hủy', _order.cancelReason),
                ],
              ),
            _DetailSection(
              title: 'Mốc giao hàng',
              children: [
                _time(
                  context,
                  _order.hasOrderDetails ? 'Tạo đơn' : 'Ghi nhận',
                  _order.createdAt,
                ),
                _time(context, 'Phân công', _order.assignedAt),
                _time(context, 'Bắt đầu giao', _order.deliveryStartedAt),
                _time(context, 'Hoàn tất giao', _order.deliveredAt),
                _time(context, 'Thanh toán', _order.paidAt),
              ],
            ),
            if (_order.isDelivered ||
                _order.deliveryProofUrl != null ||
                (_order.deliveredNotes?.trim().isNotEmpty ?? false))
              _DetailSection(
                title: 'Bằng chứng giao hàng',
                children: [
                  const SizedBox(height: 10),
                  if (_order.deliveryProofUrl case final proof?) ...[
                    _OrderPhoto(
                      url: proof,
                      label: 'Ảnh xác nhận giao hàng',
                      height: 220,
                      fit: BoxFit.contain,
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Nhấn vào ảnh để phóng to',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.textMuted,
                      ),
                    ),
                  ] else
                    const Text(
                      'Chưa có ảnh xác nhận giao hàng.',
                      style: TextStyle(color: AppColors.textMuted),
                    ),
                  _fact(context, 'Ghi chú giao hàng', _order.deliveredNotes),
                ],
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusBanner(ShipperStatusStyle style) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            color: style.background,
            child: Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              runSpacing: 6,
              children: [
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(style.icon, size: 20, color: style.foreground),
                    const SizedBox(width: 8),
                    Flexible(
                      child: InkWell(
                        onTap: () => copyTextWithFeedback(
                          context,
                          _order.orderNumber,
                          'mã đơn',
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Flexible(
                              child: Text(
                                '#${_order.orderNumber}',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                  color: style.foreground,
                                ),
                              ),
                            ),
                            const SizedBox(width: 4),
                            Icon(
                              Icons.copy_rounded,
                              size: 14,
                              color: style.foreground.withValues(alpha: 0.75),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: style.badgeColor,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    style.label,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (_order.hasOrderDetails)
            Padding(
              padding: const EdgeInsets.all(14),
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: _order.isCod
                      ? AppColors.warningLight
                      : AppColors.successLight,
                  borderRadius: BorderRadius.circular(
                    ShipperLayout.innerRadius,
                  ),
                ),
                child: Wrap(
                  alignment: WrapAlignment.spaceBetween,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: 10,
                  runSpacing: 6,
                  children: [
                    Text(
                      _order.isCod
                          ? 'Tiền mặt cần thu (COD)'
                          : 'Tổng giá trị đơn (${paymentStatusLabel(_order.paymentStatus)})',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                        color: _order.isCod
                            ? AppColors.warning
                            : AppColors.success,
                      ),
                    ),
                    Text(
                      formatCurrency(
                        _order.isCod ? _order.codAmount : _order.totalAmount,
                      ),
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                        color: _order.isCod
                            ? AppColors.warning
                            : AppColors.success,
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildRecipientCard() {
    return _DetailSection(
      title: 'Thông tin giao nhận',
      children: [
        _fact(context, 'Người nhận', _order.customerName),
        if (_order.customerPhone.trim().isNotEmpty)
          _fact(
            context,
            'Điện thoại',
            _order.customerPhone,
            trailing: IconButton(
              visualDensity: VisualDensity.compact,
              tooltip: 'Sao chép SĐT',
              onPressed: () => copyTextWithFeedback(
                context,
                _order.customerPhone,
                'số điện thoại',
              ),
              icon: const Icon(Icons.copy_rounded, size: 16),
            ),
          ),
        _fact(
          context,
          'Địa chỉ',
          _order.displayShippingAddress,
          trailing: _order.shippingAddress.isNotEmpty
              ? IconButton(
                  visualDensity: VisualDensity.compact,
                  tooltip: 'Sao chép địa chỉ',
                  onPressed: () => copyTextWithFeedback(
                    context,
                    _order.shippingAddress,
                    'địa chỉ',
                  ),
                  icon: const Icon(Icons.copy_rounded, size: 16),
                )
              : null,
        ),
        _fact(context, 'Đơn vị vận chuyển', _order.carrierName),
        _fact(
          context,
          'Mã vận đơn',
          _order.trackingCode,
          trailing: (_order.trackingCode?.trim().isNotEmpty ?? false)
              ? IconButton(
                  visualDensity: VisualDensity.compact,
                  tooltip: 'Sao chép mã vận đơn',
                  onPressed: () => copyTextWithFeedback(
                    context,
                    _order.trackingCode!.trim(),
                    'mã vận đơn',
                  ),
                  icon: const Icon(Icons.copy_rounded, size: 16),
                )
              : null,
        ),
        _fact(context, 'Ghi chú đơn', _order.notes),
        _fact(context, 'Lý do hủy', _order.cancelReason),
        if (_order.customerPhone.trim().isNotEmpty ||
            (_order.isShipped && _order.shippingAddress.isNotEmpty)) ...[
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (_order.customerPhone.trim().isNotEmpty)
                FilledButton.icon(
                  onPressed: () =>
                      launchPhoneCall(context, _order.customerPhone),
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.success,
                    foregroundColor: Colors.white,
                  ),
                  icon: const Icon(Icons.phone_rounded, size: 18),
                  label: const Text('Gọi ngay'),
                ),
              if (_order.isShipped && _order.shippingAddress.isNotEmpty)
                OutlinedButton.icon(
                  onPressed: () =>
                      openMapDirections(context, _order.shippingAddress),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.primary,
                    side: const BorderSide(color: AppColors.primary),
                  ),
                  icon: const Icon(Icons.directions_outlined, size: 18),
                  label: const Text('Chỉ đường'),
                ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _buildPaymentSection() {
    return _DetailSection(
      title: 'Thanh toán & đối soát',
      children: [
        _fact(context, 'Phương thức', paymentMethodLabel(_order.paymentMethod)),
        _fact(context, 'Trạng thái', paymentStatusLabel(_order.paymentStatus)),
        _amount(context, 'Tiền hàng', _order.subTotal),
        _amount(context, 'Phí vận chuyển', _order.shippingFee),
        _amount(context, 'Giảm giá', _order.discountAmount),
        _amount(context, 'Tổng đơn', _order.totalAmount),
        _amount(context, 'Đã thanh toán', _order.paidAmount),
        if (_order.paymentMethod == 'COD') ...[
          _amount(context, 'COD cần thu', _order.codAmount),
          if (_order.codCollected > 0) ...[
            _amount(context, 'COD đã thu', _order.codCollected),
            _amount(context, 'COD đã bàn giao', _order.codRemitted),
            _amount(
              context,
              'COD chờ bàn giao',
              _order.codCollected - _order.codRemitted,
            ),
          ],
        ],
      ],
    );
  }

  Widget _buildBottomActions() {
    return ColoredBox(
      color: AppColors.card,
      child: SafeArea(
        top: false,
        minimum: const EdgeInsets.fromLTRB(16, 10, 16, 12),
        child: Center(
          heightFactor: 1,
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: ShipperLayout.maxContentWidth,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_order.deliveryStartedAt == null)
                  FilledButton.icon(
                    onPressed: _busy ? null : _startDelivery,
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      foregroundColor: Colors.white,
                    ),
                    icon: const Icon(Icons.local_shipping_outlined),
                    label: Text(_busy ? 'Đang cập nhật…' : 'Bắt đầu giao'),
                  )
                else ...[
                  FilledButton.icon(
                    onPressed: _busy ? null : _openConfirmation,
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      foregroundColor: Colors.white,
                    ),
                    icon: const Icon(Icons.camera_alt_outlined),
                    label: const Text('Chụp ảnh xác nhận giao hàng'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _reportFailure,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.danger,
                      side: BorderSide(
                        color: AppColors.danger.withValues(alpha: 0.45),
                      ),
                    ),
                    icon: const Icon(Icons.report_problem_outlined),
                    label: const Text('Báo giao thất bại'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  static Widget _fact(
    BuildContext context,
    String label,
    String? value, {
    Widget? trailing,
  }) {
    if (value == null || value.trim().isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: AppColors.textMuted,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value.trim(),
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textMain,
                  ),
                ),
              ],
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }

  static Widget _amount(BuildContext context, String label, double? value) =>
      _fact(context, label, value == null ? null : formatCurrency(value));

  static Widget _time(BuildContext context, String label, DateTime? value) =>
      _fact(context, label, value == null ? null : formatVnDateTime(value));
}

class _DetailSection extends StatelessWidget {
  const _DetailSection({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final hasVisibleContent = children.any((w) => w is! SizedBox);
    if (!hasVisibleContent) return const SizedBox.shrink();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
                color: AppColors.textMain,
              ),
            ),
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
            const Padding(
              padding: EdgeInsets.only(top: 6),
              child: Text(
                'Chưa có thông tin dòng hàng.',
                style: TextStyle(color: AppColors.textMuted),
              ),
            ),
          for (final item
              in preview ? order.itemsSummary.take(2) : order.itemsSummary)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(
                '• $item',
                style: const TextStyle(color: AppColors.textMain, fontSize: 13),
              ),
            ),
          if (preview && order.itemsSummary.length > 2)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(
                'Còn ${order.itemsSummary.length - 2} sản phẩm khác',
                style: const TextStyle(
                  fontSize: 12,
                  color: AppColors.textMuted,
                ),
              ),
            ),
        ],
      );
    }

    final visibleItems = preview ? order.items.take(2) : order.items;
    final photoSize = preview ? 46.0 : 56.0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final item in visibleItems)
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _OrderPhoto(
                  url: item.imageUrl,
                  label: item.productName,
                  width: photoSize,
                  height: photoSize,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        item.productName,
                        maxLines: preview ? 2 : 4,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          color: AppColors.textMain,
                        ),
                      ),
                      if (item.variantName?.trim().isNotEmpty ?? false)
                        Text(
                          item.variantName!.trim(),
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.textMuted,
                          ),
                        ),
                      if (item.quantity case final quantity?)
                        Text(
                          'Số lượng: $quantity',
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.textMain,
                          ),
                        ),
                      if (!preview) ...[
                        if (item.sku?.trim().isNotEmpty ?? false)
                          Text(
                            'SKU: ${item.sku}',
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.textMuted,
                            ),
                          ),
                        if (item.price case final price?)
                          Text(
                            'Đơn giá: ${formatCurrency(price)}',
                            style: const TextStyle(fontSize: 13),
                          ),
                        if ((item.discount ?? 0) > 0)
                          Text(
                            'Giảm mỗi sản phẩm: ${formatCurrency(item.discount!)}',
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.success,
                            ),
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
            child: Text(
              'Còn ${order.items.length - 2} dòng hàng',
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppColors.primary,
              ),
            ),
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
        borderRadius: BorderRadius.circular(10),
        child: Material(
          color: AppColors.primarySurface,
          child: photo == null
              ? const Center(
                  child: Icon(
                    Icons.image_not_supported_outlined,
                    color: AppColors.primaryDark,
                  ),
                )
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
