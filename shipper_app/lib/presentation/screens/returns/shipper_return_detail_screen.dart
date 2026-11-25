import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/storage/shipper_settings_store.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';
import '../../../data/models/shipper_return_model.dart';
import '../../../providers/shipper_provider.dart';
import 'return_pickup_confirmation_screen.dart';

class ShipperReturnDetailScreen extends StatefulWidget {
  const ShipperReturnDetailScreen({
    super.key,
    required this.returnRequest,
    this.enableActions = true,
  });

  final ShipperReturnModel returnRequest;
  final bool enableActions;

  @override
  State<ShipperReturnDetailScreen> createState() =>
      _ShipperReturnDetailScreenState();
}

class _ShipperReturnDetailScreenState extends State<ShipperReturnDetailScreen> {
  late ShipperReturnModel _ret = widget.returnRequest;
  bool _busy = false;

  Future<void> _reload() async {
    if (!widget.enableActions) return;
    try {
      final latest = await context.read<ShipperProvider>().fetchReturnDetail(
        _ret.id,
      );
      if (mounted) setState(() => _ret = latest);
    } catch (_) {}
  }

  Future<void> _startPickup() async {
    if (_busy || !widget.enableActions) return;
    if (ShipperSettingsStore.instance.confirmBeforeStart) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Bắt đầu thu hồi hàng hoàn'),
          content: Text(
            'Xác nhận bắt đầu đi thu hồi phiếu #${_ret.id} (đơn #${_ret.orderNumber})?',
          ),
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
      await context.read<ShipperProvider>().startReturnPickup(_ret.id);
      await _reload();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã bắt đầu đi thu hồi hàng hoàn')),
        );
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

  Future<void> _openPickupConfirmation() async {
    if (_busy || !widget.enableActions) return;
    final completed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => ReturnPickupConfirmationScreen(returnRequest: _ret),
      ),
    );
    if (completed == true && mounted) {
      await _reload();
    }
  }

  Future<void> _reportFailure() async {
    if (_busy || !widget.enableActions) return;
    final reason = await showReturnPickupFailureDialog(context);
    if (!mounted || reason == null) return;
    setState(() => _busy = true);
    try {
      await context.read<ShipperProvider>().reportReturnPickupFailure(
        _ret.id,
        reason,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã ghi nhận thu hồi thất bại')),
        );
        Navigator.pop(context, true);
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

  Future<void> _requestHandover() async {
    if (_busy || !widget.enableActions) return;
    final controller = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Báo đã bàn giao cho kho'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Xác nhận bạn đã nộp kiện hàng hoàn phiếu #${_ret.id} về kho BeautyShop để kiểm nhận?',
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              maxLength: 500,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Ghi chú bàn giao (tùy chọn)',
                hintText: 'Ví dụ: Đã giao cho thủ kho ca chiều',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Xác nhận bàn giao'),
          ),
        ],
      ),
    );
    final notes = controller.text;
    controller.dispose();
    if (confirmed != true || !mounted) return;
    setState(() => _busy = true);
    try {
      await context.read<ShipperProvider>().handoverReturnToWarehouse(
        _ret.id,
        notes: notes,
      );
      await _reload();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã gửi thông báo bàn giao hàng hoàn cho kho'),
          ),
        );
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

  @override
  Widget build(BuildContext context) {
    final style = ShipperStatusStyle.fromReturn(_ret);
    final showFooter =
        widget.enableActions &&
        (_ret.isPickupAssigned ||
            (_ret.isPickedUp && !_ret.isHandoverRequested));

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Chi tiết thu hồi hoàn',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          if (widget.enableActions)
            IconButton(
              tooltip: 'Làm mới',
              onPressed: _busy ? null : _reload,
              icon: const Icon(Icons.refresh_rounded),
            ),
        ],
      ),
      bottomNavigationBar: showFooter ? _buildBottomActions() : null,
      body: ShipperPageContainer(
        child: ListView(
          padding: ShipperLayout.pagePadding,
          children: [
            _buildBanner(style),
            if (_ret.isPickedUp)
              Card(
                color: _ret.isHandoverRequested
                    ? AppColors.infoLight
                    : AppColors.warningLight,
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        _ret.isHandoverRequested
                            ? Icons.inventory_2_outlined
                            : Icons.warehouse_outlined,
                        size: 20,
                        color: _ret.isHandoverRequested
                            ? AppColors.info
                            : AppColors.warning,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _ret.isHandoverRequested
                              ? 'Bạn đã báo bàn giao kiện hàng hoàn về kho. Đang chờ thủ kho kiểm nhận.'
                              : 'Bạn đang giữ kiện hàng hoàn này. Vui lòng mang về kho BeautyShop và bấm "Báo đã bàn giao cho kho".',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w500,
                            color: _ret.isHandoverRequested
                                ? AppColors.info
                                : AppColors.warning,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            _buildCustomerSection(),
            _buildItemsSection(),
            _buildEvidenceAndProofSection(),
            _buildTimelineSection(),
          ],
        ),
      ),
    );
  }

  Widget _buildBanner(ShipperStatusStyle style) {
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
                      child: Text(
                        'Phiếu #${_ret.id} · #${_ret.orderNumber}',
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                          color: style.foreground,
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
          Padding(
            padding: const EdgeInsets.all(14),
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.infoLight,
                borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
              ),
              child: Wrap(
                alignment: WrapAlignment.spaceBetween,
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                runSpacing: 4,
                children: [
                  const Text(
                    'Giá trị hàng hoàn (Không hoàn tiền mặt):',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: AppColors.info,
                    ),
                  ),
                  Text(
                    formatCurrency(_ret.refundAmount),
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: AppColors.info,
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

  Widget _buildCustomerSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Địa chỉ & Khách thu hồi',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              _ret.customerName,
              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
            ),
            if (_ret.customerPhone.isNotEmpty) ...[
              const SizedBox(height: 2),
              Text(
                _ret.customerPhone,
                style: const TextStyle(color: AppColors.textMuted),
              ),
            ],
            const SizedBox(height: 6),
            Text(
              _ret.displayPickupAddress,
              style: const TextStyle(fontSize: 13.5),
            ),
            const SizedBox(height: 8),
            Text(
              'Lý do trả hàng: ${_ret.reason}',
              style: const TextStyle(fontSize: 13, color: AppColors.textMuted),
            ),
            if (_ret.decisionReason?.isNotEmpty == true) ...[
              const SizedBox(height: 4),
              Text(
                'Ghi chú duyệt: ${_ret.decisionReason}',
                style: const TextStyle(
                  fontSize: 12.5,
                  color: AppColors.textMuted,
                ),
              ),
            ],
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                if (_ret.customerPhone.trim().isNotEmpty)
                  FilledButton.icon(
                    onPressed: () =>
                        launchPhoneCall(context, _ret.customerPhone),
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.success,
                      foregroundColor: Colors.white,
                    ),
                    icon: const Icon(Icons.phone_rounded, size: 18),
                    label: const Text('Gọi khách'),
                  ),
                if (_ret.pickupAddress.isNotEmpty)
                  OutlinedButton.icon(
                    onPressed: () =>
                        openMapDirections(context, _ret.pickupAddress),
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
        ),
      ),
    );
  }

  Widget _buildItemsSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Sản phẩm cần thu hồi (${_ret.items.isNotEmpty ? _ret.items.length : _ret.itemsSummary.length})',
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            if (_ret.items.isNotEmpty)
              for (final item in _ret.items)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Container(
                          width: 48,
                          height: 48,
                          color: AppColors.primarySurface,
                          child: item.imageUrl != null
                              ? CachedNetworkImage(
                                  imageUrl: item.imageUrl!,
                                  fit: BoxFit.cover,
                                )
                              : const Icon(
                                  Icons.inventory_2_outlined,
                                  color: AppColors.primaryDark,
                                ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              item.productName,
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            if (item.variantName?.isNotEmpty == true)
                              Text(
                                item.variantName!,
                                style: const TextStyle(
                                  fontSize: 12,
                                  color: AppColors.textMuted,
                                ),
                              ),
                            Text(
                              'Số lượng thu hồi: ${item.returnQuantity}/${item.purchasedQuantity}',
                              style: const TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: AppColors.primary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                )
            else
              for (final line in _ret.itemsSummary)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text('• $line'),
                ),
          ],
        ),
      ),
    );
  }

  Widget _buildEvidenceAndProofSection() {
    if (_ret.evidenceUrls.isEmpty && _ret.pickupProofUrl == null) {
      return const SizedBox.shrink();
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (_ret.evidenceUrls.isNotEmpty) ...[
              const Text(
                'Ảnh khách hàng đính kèm',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final url in _ret.evidenceUrls)
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: CachedNetworkImage(
                        imageUrl: url,
                        width: 68,
                        height: 68,
                        fit: BoxFit.cover,
                      ),
                    ),
                ],
              ),
            ],
            if (_ret.pickupProofUrl != null) ...[
              if (_ret.evidenceUrls.isNotEmpty) const Divider(height: 20),
              const Text(
                'Ảnh xác nhận tài xế đã lấy hàng hoàn',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: CachedNetworkImage(
                  imageUrl: _ret.pickupProofUrl!,
                  height: 200,
                  width: double.infinity,
                  fit: BoxFit.contain,
                ),
              ),
              if (_ret.pickupNotes?.isNotEmpty == true) ...[
                const SizedBox(height: 6),
                Text(
                  'Ghi chú thu hồi: ${_ret.pickupNotes}',
                  style: const TextStyle(fontSize: 13),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildTimelineSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Mốc đối soát thu hồi',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 6),
            _timeRow('Phân công thu hồi', _ret.pickupAssignedAt),
            _timeRow('Bắt đầu đi lấy', _ret.pickupStartedAt),
            _timeRow('Đã lấy hàng hoàn', _ret.pickedUpAt),
            _timeRow('Báo bàn giao kho', _ret.handoverRequestedAt),
            _timeRow('Kho đã kiểm nhận', _ret.receivedAt),
          ],
        ),
      ),
    );
  }

  Widget _timeRow(String label, DateTime? value) {
    if (value == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              label,
              style: const TextStyle(fontSize: 12.5, color: AppColors.textMuted),
            ),
          ),
          const SizedBox(width: 8),
          Text(
            formatVnDateTime(value),
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
          ),
        ],
      ),
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
                if (_ret.isPickupAssigned && !_ret.isStarted)
                  FilledButton.icon(
                    onPressed: _busy ? null : _startPickup,
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      foregroundColor: Colors.white,
                    ),
                    icon: const Icon(Icons.assignment_return_outlined),
                    label: Text(
                      _busy ? 'Đang cập nhật…' : 'Bắt đầu đi lấy hàng hoàn',
                    ),
                  )
                else if (_ret.isPickupAssigned && _ret.isStarted) ...[
                  FilledButton.icon(
                    onPressed: _busy ? null : _openPickupConfirmation,
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.primary,
                      foregroundColor: Colors.white,
                    ),
                    icon: const Icon(Icons.camera_alt_outlined),
                    label: const Text('Chụp ảnh xác nhận lấy hàng'),
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
                    label: const Text('Báo thu hồi thất bại'),
                  ),
                ] else if (_ret.isPickedUp && !_ret.isHandoverRequested)
                  FilledButton.icon(
                    onPressed: _busy ? null : _requestHandover,
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.warning,
                      foregroundColor: Colors.white,
                    ),
                    icon: const Icon(Icons.warehouse_outlined),
                    label: Text(
                      _busy ? 'Đang gửi…' : 'Báo đã bàn giao cho kho',
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

