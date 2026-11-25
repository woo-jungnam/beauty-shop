import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../data/models/shipper_order_model.dart';
import '../../data/models/shipper_return_model.dart';
import '../../providers/auth_provider.dart';
import '../../providers/shipper_provider.dart';
import '../storage/shipper_settings_store.dart';
import '../theme/app_colors.dart';

final NumberFormat _currencyFormatter = NumberFormat.currency(
  locale: 'vi_VN',
  symbol: '₫',
  decimalDigits: 0,
);
final DateFormat _dateTimeFormatter = DateFormat('dd/MM/yyyy HH:mm');
final DateFormat _dateFormatter = DateFormat('dd/MM/yyyy');

final Uri _mapsDirections = _resolveMapsDirections();

Uri _resolveMapsDirections() {
  const configuredUrl = String.fromEnvironment('MAPS_DIRECTIONS_URL');
  final uri = Uri.tryParse(configuredUrl.trim());
  if (uri != null && uri.scheme == 'https' && uri.host.isNotEmpty) {
    return uri;
  }
  return Uri.https('www.google.com', '/maps/dir/');
}

DateTime toVietnamTime(DateTime date) =>
    date.toUtc().add(const Duration(hours: 7));

String formatCurrency(num? amount) => _currencyFormatter.format(amount ?? 0);

String formatVnDateTime(DateTime date) =>
    _dateTimeFormatter.format(toVietnamTime(date));

String formatVnDate(DateTime date) =>
    _dateFormatter.format(toVietnamTime(date));

class ShipperStatusStyle {
  const ShipperStatusStyle({
    required this.label,
    required this.background,
    required this.foreground,
    required this.badgeColor,
    required this.icon,
  });

  final String label;
  final Color background;
  final Color foreground;
  final Color badgeColor;
  final IconData icon;

  factory ShipperStatusStyle.fromOrder(ShipperOrderModel order) =>
      ShipperStatusStyle.of(order.status, order.deliveryStartedAt);

  factory ShipperStatusStyle.fromReturn(ShipperReturnModel ret) =>
      ShipperStatusStyle.of(ret.status, ret.pickupStartedAt);

  factory ShipperStatusStyle.of(String status, [DateTime? deliveryStartedAt]) {
    return switch (status) {
      'DELIVERED' => const ShipperStatusStyle(
        label: 'Đã giao',
        background: AppColors.successLight,
        foreground: AppColors.success,
        badgeColor: AppColors.success,
        icon: Icons.check_circle_outline_rounded,
      ),
      'PICKUP_ASSIGNED' when deliveryStartedAt != null => const ShipperStatusStyle(
        label: 'Đang đi thu hồi',
        background: AppColors.infoLight,
        foreground: AppColors.info,
        badgeColor: AppColors.info,
        icon: Icons.local_shipping_outlined,
      ),
      'PICKUP_ASSIGNED' => const ShipperStatusStyle(
        label: 'Chờ lấy hàng hoàn',
        background: AppColors.primarySurface,
        foreground: AppColors.primaryDark,
        badgeColor: AppColors.primary,
        icon: Icons.assignment_return_outlined,
      ),
      'PICKED_UP' => const ShipperStatusStyle(
        label: 'Đã lấy · Chờ trả kho',
        background: AppColors.warningLight,
        foreground: AppColors.warning,
        badgeColor: AppColors.warning,
        icon: Icons.inventory_outlined,
      ),
      'RECEIVED' => const ShipperStatusStyle(
        label: 'Kho đã kiểm nhận',
        background: AppColors.successLight,
        foreground: AppColors.success,
        badgeColor: AppColors.success,
        icon: Icons.warehouse_outlined,
      ),
      'SHIPPED' when deliveryStartedAt != null => const ShipperStatusStyle(
        label: 'Đang giao',
        background: AppColors.infoLight,
        foreground: AppColors.info,
        badgeColor: AppColors.info,
        icon: Icons.local_shipping_outlined,
      ),
      'SHIPPED' => const ShipperStatusStyle(
        label: 'Cần giao',
        background: AppColors.primarySurface,
        foreground: AppColors.primaryDark,
        badgeColor: AppColors.primary,
        icon: Icons.inventory_2_outlined,
      ),
      'DELIVERY_FAILED' => const ShipperStatusStyle(
        label: 'Giao thất bại',
        background: AppColors.dangerLight,
        foreground: AppColors.danger,
        badgeColor: AppColors.danger,
        icon: Icons.error_outline_rounded,
      ),
      'RETURNING_TO_SENDER' => const ShipperStatusStyle(
        label: 'Đang hoàn về kho',
        background: AppColors.warningLight,
        foreground: AppColors.warning,
        badgeColor: AppColors.warning,
        icon: Icons.assignment_return_outlined,
      ),
      'RETURN_TO_SENDER' => const ShipperStatusStyle(
        label: 'Đã hoàn về kho',
        background: AppColors.warningLight,
        foreground: AppColors.warning,
        badgeColor: AppColors.warning,
        icon: Icons.warehouse_outlined,
      ),
      'RETURNED' => const ShipperStatusStyle(
        label: 'Đã trả hàng',
        background: AppColors.surfaceMuted,
        foreground: AppColors.textMuted,
        badgeColor: AppColors.textMuted,
        icon: Icons.undo_rounded,
      ),
      'CANCELLED' => const ShipperStatusStyle(
        label: 'Đã hủy',
        background: AppColors.dangerLight,
        foreground: AppColors.danger,
        badgeColor: AppColors.danger,
        icon: Icons.cancel_outlined,
      ),
      'REFUNDED' => const ShipperStatusStyle(
        label: 'Đã hoàn tiền',
        background: AppColors.infoLight,
        foreground: AppColors.info,
        badgeColor: AppColors.info,
        icon: Icons.currency_exchange_rounded,
      ),
      _ => ShipperStatusStyle(
        label: status,
        background: AppColors.surfaceMuted,
        foreground: AppColors.textMuted,
        badgeColor: AppColors.textMuted,
        icon: Icons.receipt_long_outlined,
      ),
    };
  }
}

String paymentStatusLabel(String status) => switch (status) {
  'PAID' => 'Đã thanh toán',
  'PARTIALLY_REFUNDED' => 'Đã hoàn một phần',
  'PENDING' => 'Chờ thanh toán',
  'FAILED' => 'Thanh toán thất bại',
  'REFUND_PENDING' => 'Chờ hoàn tiền',
  'REFUNDED' => 'Đã hoàn tiền',
  _ => status,
};

String paymentMethodLabel(String method) => switch (method) {
  'COD' => 'Thu hộ khi giao (COD)',
  'BANK' || 'BANK_TRANSFER' => 'Chuyển khoản ngân hàng',
  'CASH' => 'Tiền mặt',
  'VNPAY' => 'VNPay',
  'MOMO' => 'MoMo',
  _ => method,
};

Future<void> launchPhoneCall(BuildContext context, String phone) async {
  final cleanPhone = phone.replaceAll(RegExp(r'[^0-9+]'), '');
  if (cleanPhone.isEmpty) return;
  final uri = Uri(scheme: 'tel', path: cleanPhone);
  try {
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri);
    } else {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  } catch (e) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Không thể gọi đến $cleanPhone: $e')),
    );
  }
}

Future<void> openMapDirections(BuildContext context, String address) async {
  final cleanAddress = address.trim();
  if (cleanAddress.isEmpty) return;

  final mode = ShipperSettingsStore.instance.mapPreference;
  if (mode == ShipperMapPreference.copyOnly) {
    await copyTextWithFeedback(context, cleanAddress, 'địa chỉ');
    return;
  }
  if (mode == ShipperMapPreference.mapsAndCopy) {
    await Clipboard.setData(ClipboardData(text: cleanAddress));
  }

  final url = _mapsDirections.replace(
    queryParameters: {
      'api': '1',
      'destination': cleanAddress,
      'dir_action': 'navigate',
    },
  );
  try {
    if (await launchUrl(url, mode: LaunchMode.externalApplication)) return;
  } on Exception {
    // Device may have no compatible map app installed.
  }
  if (!context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(
    const SnackBar(content: Text('Không mở được bản đồ. Vui lòng thử lại.')),
  );
}

Future<bool> confirmAndLogout(BuildContext context) async {
  final confirm = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: const Text('Đăng xuất'),
      content: const Text(
        'Bạn có chắc muốn đăng xuất khỏi ca làm việc không?',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(ctx, true),
          style: FilledButton.styleFrom(
            backgroundColor: AppColors.danger,
            foregroundColor: Colors.white,
          ),
          child: const Text('Đăng xuất'),
        ),
      ],
    ),
  );

  if (confirm == true && context.mounted) {
    context.read<ShipperProvider>().clearSession();
    await context.read<AuthProvider>().logout();
    return true;
  }
  return false;
}

Future<void> copyTextWithFeedback(
  BuildContext context,
  String value,
  String label,
) async {
  await Clipboard.setData(ClipboardData(text: value));
  if (!context.mounted) return;
  ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text('Đã sao chép $label: $value')));
}

Future<String?> showDeliveryFailureDialog(BuildContext context) {
  return showDialog<String>(
    context: context,
    builder: (_) => const _DeliveryFailureDialog(
      title: 'Báo giao thất bại',
      quickReasons: [
        'Khách không nghe máy',
        'Khách hẹn giao lại sau',
        'Sai hoặc thiếu địa chỉ',
        'Khách từ chối nhận hàng',
      ],
      emptyError: 'Nhập lý do giao thất bại',
      hintText: 'Nhập lý do không thể giao đơn hàng...',
    ),
  );
}

Future<String?> showReturnPickupFailureDialog(BuildContext context) {
  return showDialog<String>(
    context: context,
    builder: (_) => const _DeliveryFailureDialog(
      title: 'Báo thu hồi thất bại',
      quickReasons: [
        'Khách không nghe máy',
        'Khách hẹn lấy hàng lại sau',
        'Hàng không đúng hoặc thiếu sản phẩm',
        'Khách đổi ý không trả hàng nữa',
      ],
      emptyError: 'Nhập lý do thu hồi thất bại',
      hintText: 'Nhập lý do không thể lấy hàng hoàn...',
    ),
  );
}

void showDeliveryProofDialog(BuildContext context, ShipperOrderModel order) {
  showDialog<void>(
    context: context,
    builder: (ctx) => Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'Bằng chứng đơn #${order.orderNumber}',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                        color: AppColors.textMain,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Đóng',
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(ctx),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              if (order.deliveryProofUrl != null)
                ClipRRect(
                  borderRadius: BorderRadius.circular(
                    ShipperLayout.innerRadius,
                  ),
                  child: Container(
                    color: AppColors.surfaceMuted,
                    height: 260,
                    child: InteractiveViewer(
                      child: CachedNetworkImage(
                        imageUrl: order.deliveryProofUrl!,
                        fit: BoxFit.contain,
                        placeholder: (_, _) =>
                            const Center(child: CircularProgressIndicator()),
                        errorWidget: (_, _, _) => const Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.broken_image_outlined,
                                size: 44,
                                color: AppColors.textMuted,
                              ),
                              SizedBox(height: 8),
                              Text('Không thể tải hình ảnh'),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                )
              else
                Container(
                  height: 120,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: AppColors.surfaceMuted,
                    borderRadius: BorderRadius.circular(
                      ShipperLayout.innerRadius,
                    ),
                  ),
                  child: const Text(
                    'Đơn hàng không có ảnh đính kèm',
                    style: TextStyle(color: AppColors.textMuted),
                  ),
                ),
              const SizedBox(height: 12),
              if (order.deliveredAt != null)
                Text(
                  'Thời gian giao: ${formatVnDateTime(order.deliveredAt!)}',
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.textMuted,
                  ),
                ),
              if (order.deliveredNotes != null &&
                  order.deliveredNotes!.trim().isNotEmpty) ...[
                const SizedBox(height: 6),
                Text(
                  'Ghi chú: ${order.deliveredNotes!.trim()}',
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                    color: AppColors.textMain,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    ),
  );
}

class _DeliveryFailureDialog extends StatefulWidget {
  const _DeliveryFailureDialog({
    required this.title,
    required this.quickReasons,
    required this.emptyError,
    required this.hintText,
  });

  final String title;
  final List<String> quickReasons;
  final String emptyError;
  final String hintText;

  @override
  State<_DeliveryFailureDialog> createState() => _DeliveryFailureDialogState();
}

class _DeliveryFailureDialogState extends State<_DeliveryFailureDialog> {
  final _form = GlobalKey<FormState>();
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: SingleChildScrollView(
        child: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  for (final preset in widget.quickReasons)
                    ActionChip(
                      label: Text(preset, style: const TextStyle(fontSize: 12)),
                      onPressed: () {
                        _controller.text = preset;
                        _controller.selection = TextSelection.fromPosition(
                          TextPosition(offset: preset.length),
                        );
                      },
                    ),
                ],
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _controller,
                autofocus: true,
                maxLength: 500,
                maxLines: 3,
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? widget.emptyError : null,
                decoration: InputDecoration(
                  labelText: 'Lý do chi tiết',
                  hintText: widget.hintText,
                ),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Hủy'),
        ),
        FilledButton(
          style: FilledButton.styleFrom(
            backgroundColor: AppColors.danger,
            foregroundColor: Colors.white,
          ),
          onPressed: () {
            if (_form.currentState!.validate()) {
              Navigator.pop(context, _controller.text.trim());
            }
          },
          child: const Text('Gửi báo cáo'),
        ),
      ],
    );
  }
}

class ShipperPageContainer extends StatelessWidget {
  const ShipperPageContainer({
    super.key,
    required this.child,
    this.maxWidth = ShipperLayout.maxContentWidth,
  });

  final Widget child;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: child,
      ),
    );
  }
}

