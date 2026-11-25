import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';
import '../../../providers/shipper_provider.dart';
import '../delivery/shipper_order_detail_screen.dart';
import '../returns/shipper_return_detail_screen.dart';

class NotificationScreen extends StatefulWidget {
  const NotificationScreen({super.key});

  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen> {
  static final RegExp _orderTargetPattern = RegExp(
    r'^/api/v1/shipper/orders/(\d+)$',
  );
  static final RegExp _returnTargetPattern = RegExp(
    r'^/api/v1/shipper/returns/(\d+)$',
  );

  int _page = 0;
  int? _openingId;
  bool _markingAll = false;
  bool _loading = true;
  bool _isLast = true;
  String? _error;
  List<Map<String, dynamic>> _rows = const [];

  @override
  void initState() {
    super.initState();
    _load(0);
  }

  Future<void> _load(int page) async {
    final provider = context.read<ShipperProvider>();
    setState(() {
      _page = page;
      _loading = true;
      _error = null;
    });
    try {
      final data = await provider.fetchNotifications(page);
      if (!mounted) return;
      final content = (data['content'] as List?) ?? const [];
      setState(() {
        _rows = content
            .whereType<Map<String, dynamic>>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
        _isLast = data['last'] != false;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
    if (mounted) await provider.refreshUnreadCount();
  }

  Future<void> _markReadInMemory(int id) async {
    final provider = context.read<ShipperProvider>();
    await provider.markRead(id);
    if (!mounted) return;
    setState(() {
      for (final row in _rows) {
        if (row['id'] == id) {
          row['readAt'] = DateTime.now().toUtc().toIso8601String();
        }
      }
    });
  }

  Future<void> _markAllPageRead() async {
    final unreadIds = _rows
        .where((row) => row['readAt'] == null && row['id'] is int)
        .map((row) => row['id'] as int)
        .toList();
    if (unreadIds.isEmpty || _markingAll) return;

    setState(() => _markingAll = true);
    try {
      for (final id in unreadIds) {
        await _markReadInMemory(id);
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    } finally {
      if (mounted) setState(() => _markingAll = false);
    }
  }

  Future<void> _openNotification(Map<String, dynamic> notification) async {
    final id = notification['id'] as int?;
    if (id == null || _openingId != null) return;

    final provider = context.read<ShipperProvider>();
    setState(() => _openingId = id);
    try {
      if (notification['readAt'] == null) {
        await _markReadInMemory(id);
      }
      final target = notification['target']?.toString().trim() ?? '';
      final orderMatch = _orderTargetPattern.firstMatch(target);
      final orderId = orderMatch != null ? int.tryParse(orderMatch.group(1)!) : null;
      final returnMatch = _returnTargetPattern.firstMatch(target);
      final returnId = returnMatch != null ? int.tryParse(returnMatch.group(1)!) : null;
      if (orderId != null && mounted) {
        final order = await provider.fetchOrderDetail(orderId);
        if (!mounted) return;
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => ShipperOrderDetailScreen(
              order: order,
              enableDeliveryActions: true,
            ),
          ),
        );
      } else if (returnId != null && mounted) {
        final ret = await provider.fetchReturnDetail(returnId);
        if (!mounted) return;
        await Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => ShipperReturnDetailScreen(
              returnRequest: ret,
              enableActions: true,
            ),
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
      if (mounted) setState(() => _openingId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final hasUnreadOnPage = _rows.any((row) => row['readAt'] == null);

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Thông báo',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        actions: [
          if (hasUnreadOnPage)
            TextButton.icon(
              onPressed: _markingAll ? null : _markAllPageRead,
              icon: _markingAll
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.done_all_rounded, size: 18),
              label: const Text('Đọc hết'),
            ),
        ],
      ),
      body: ShipperPageContainer(
        child: RefreshIndicator(
          onRefresh: () => _load(0),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: ShipperLayout.pagePadding,
            children: [
              if (_loading)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 48),
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (_error != null)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      children: [
                        const Icon(
                          Icons.cloud_off_outlined,
                          size: 36,
                          color: AppColors.textMuted,
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          'Không tải được thông báo.',
                          style: TextStyle(fontWeight: FontWeight.w600),
                        ),
                        const SizedBox(height: 8),
                        FilledButton(
                          onPressed: () => _load(_page),
                          child: const Text('Thử lại'),
                        ),
                      ],
                    ),
                  ),
                )
              else if (_rows.isEmpty)
                const Card(
                  child: Padding(
                    padding: EdgeInsets.symmetric(vertical: 28, horizontal: 16),
                    child: Column(
                      children: [
                        Icon(
                          Icons.notifications_none_rounded,
                          size: 40,
                          color: AppColors.textMuted,
                        ),
                        SizedBox(height: 8),
                        Text(
                          'Chưa có thông báo mới',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            color: AppColors.textMain,
                          ),
                        ),
                      ],
                    ),
                  ),
                )
              else ...[
                for (final row in _rows) _buildNotificationCard(row),
                if (_page > 0 || !_isLast)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        OutlinedButton.icon(
                          onPressed: _page > 0 ? () => _load(_page - 1) : null,
                          icon: const Icon(Icons.chevron_left, size: 18),
                          label: const Text('Trước'),
                        ),
                        Text(
                          'Trang ${_page + 1}',
                          style: const TextStyle(
                            fontWeight: FontWeight.w600,
                            color: AppColors.textMuted,
                          ),
                        ),
                        OutlinedButton.icon(
                          onPressed: !_isLast ? () => _load(_page + 1) : null,
                          icon: const Icon(Icons.chevron_right, size: 18),
                          label: const Text('Sau'),
                        ),
                      ],
                    ),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNotificationCard(Map<String, dynamic> row) {
    final unread = row['readAt'] == null;
    final id = row['id'] as int?;
    final target = row['target']?.toString().trim() ?? '';
    final hasOrderLink =
        _orderTargetPattern.hasMatch(target) ||
        _returnTargetPattern.hasMatch(target);
    final createdAt = DateTime.tryParse('${row['createdAt']}');
    final iconData = switch (row['type']) {
      'SHIPPER_ASSIGNMENT' => Icons.local_shipping_outlined,
      'SHIPPER_RETURN_REQUEST' ||
      'SHIPPER_RETURN_PICKUP' => Icons.assignment_return_outlined,
      _ => Icons.notifications_outlined,
    };

    return Card(
      color: unread ? AppColors.primarySurface.withValues(alpha: 0.6) : null,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => _openNotification(row),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: unread ? AppColors.primary : AppColors.surfaceMuted,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  iconData,
                  size: 20,
                  color: unread ? Colors.white : AppColors.primary,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Text(
                            row['title']?.toString() ?? '',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight:
                                  unread ? FontWeight.bold : FontWeight.w600,
                              color: AppColors.textMain,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        if (_openingId == id)
                          const SizedBox.square(
                            dimension: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        else if (unread)
                          const Icon(
                            Icons.circle,
                            size: 8,
                            color: AppColors.primary,
                            semanticLabel: 'Chưa đọc',
                          ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      row['body']?.toString() ?? '',
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textMain,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        if (createdAt != null)
                          Text(
                            formatVnDateTime(createdAt),
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.textMuted,
                            ),
                          ),
                        if (hasOrderLink)
                          const Text(
                            '• Nhấn để xem đơn',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: AppColors.primary,
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
