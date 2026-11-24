import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:beautyshop_shipper/core/theme/app_colors.dart';
import 'package:beautyshop_shipper/data/models/shipper_order_model.dart';
import 'package:beautyshop_shipper/providers/auth_provider.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';
import 'package:beautyshop_shipper/presentation/screens/delivery/delivery_confirmation_screen.dart';

import '../delivery/delivery_history_screen.dart';
import '../delivery/shipper_stats_screen.dart';
import '../notification/notification_screen.dart';

class ShipperDashboardScreen extends StatefulWidget {
  const ShipperDashboardScreen({super.key});

  @override
  State<ShipperDashboardScreen> createState() => _ShipperDashboardScreenState();
}

class _ShipperDashboardScreenState extends State<ShipperDashboardScreen>
    with WidgetsBindingObserver {
  static final Uri _mapsDirections = _resolveMapsDirections();

  static Uri _resolveMapsDirections() {
    const configuredUrl = String.fromEnvironment('MAPS_DIRECTIONS_URL');
    final uri = Uri.tryParse(configuredUrl.trim());
    if (uri != null && uri.scheme == 'https' && uri.host.isNotEmpty) {
      return uri;
    }
    return Uri.https('www.google.com', '/maps/dir/');
  }

  Timer? _pollTimer;
  int _tab = 0;

  void _startPolling() {
    _pollTimer?.cancel();
    if (!mounted) return;
    final shipper = context.read<ShipperProvider>();
    unawaited(shipper.refreshUnreadCount());
    _pollTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      unawaited(shipper.refreshUnreadCount());
      if (_tab == 0) unawaited(shipper.fetchOrders(background: true));
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _startPolling();
    } else {
      _pollTimer?.cancel();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pollTimer?.cancel();
    super.dispose();
  }

  final NumberFormat _currencyFormat = NumberFormat.currency(
    locale: 'vi_VN',
    symbol: 'đ',
  );
  final DateFormat _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      context.read<ShipperProvider>().fetchOrders();
      _startPolling();
    });
  }

  Future<void> _makePhoneCall(String phone) async {
    final cleanPhone = phone.replaceAll(RegExp(r'[^0-9+]'), '');
    final uri = Uri(scheme: 'tel', path: cleanPhone);
    try {
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri);
      } else {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Không thể gọi đến $cleanPhone: $e')),
      );
    }
  }

  Future<void> _openDirections(String address) async {
    final url = _mapsDirections.replace(
      queryParameters: {
        'api': '1',
        'destination': address,
        'dir_action': 'navigate',
      },
    );
    try {
      if (await launchUrl(url, mode: LaunchMode.externalApplication)) return;
    } on Exception {
      // The device may have no app available to open the link.
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Không mở được bản đồ. Vui lòng thử lại.')),
    );
  }

  void _showProofDialog(ShipperOrderModel order) {
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        'Bằng chứng đơn #${order.orderNumber}',
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () => Navigator.pop(ctx),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                if (order.deliveryProofUrl != null) ...[
                  ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: CachedNetworkImage(
                      imageUrl: order.deliveryProofUrl!,
                      height: 260,
                      fit: BoxFit.contain,
                      placeholder: (context, url) => Container(
                        height: 260,
                        color: Colors.grey.shade200,
                        child: const Center(child: CircularProgressIndicator()),
                      ),
                      errorWidget: (context, url, error) => Container(
                        height: 260,
                        color: Colors.grey.shade200,
                        child: const Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.broken_image,
                              size: 48,
                              color: Colors.grey,
                            ),
                            SizedBox(height: 8),
                            Text('Không thể tải hình ảnh'),
                          ],
                        ),
                      ),
                    ),
                  ),
                ] else ...[
                  Container(
                    height: 120,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: Colors.grey.shade100,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Text('Đơn hàng không có ảnh đính kèm'),
                  ),
                ],
                const SizedBox(height: 12),
                if (order.deliveredAt != null) ...[
                  Text(
                    'Thời gian giao: ${_dateFormat.format(order.deliveredAt!)}',
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppColors.textMuted,
                    ),
                  ),
                  const SizedBox(height: 4),
                ],
                if (order.deliveredNotes != null &&
                    order.deliveredNotes!.isNotEmpty) ...[
                  Text(
                    'Ghi chú: ${order.deliveredNotes}',
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
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

  Future<void> _handleLogout() async {
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
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.danger),
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );

    if (confirm == true && mounted) {
      _pollTimer?.cancel();
      context.read<ShipperProvider>().clearSession();
      await context.read<AuthProvider>().logout();
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    if (!auth.isAuthenticated) return const SizedBox.shrink();
    final shipper = context.watch<ShipperProvider>();

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        elevation: 0,
        title: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'BeautyShop Shipper',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
            ),
            Text(
              'Tài xế: ${auth.currentFullName ?? auth.currentUsername ?? "Bưu tá"}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                color: Colors.white.withValues(alpha: 0.85),
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Thông báo',
            icon: Badge.count(
              count: shipper.unreadCount,
              isLabelVisible: shipper.unreadCount > 0,
              child: const Icon(Icons.notifications_outlined),
            ),
            onPressed: () async {
              await Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const NotificationScreen()),
              );
              if (mounted) await shipper.fetchOrders();
            },
          ),
          if (_tab == 0)
            IconButton(
              icon: const Icon(Icons.refresh_rounded),
              tooltip: 'Làm mới danh sách',
              onPressed: () => shipper.fetchOrders(),
            ),
          IconButton(
            icon: const Icon(Icons.logout_rounded),
            tooltip: 'Đăng xuất',
            onPressed: _handleLogout,
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (index) {
          setState(() => _tab = index);
          if (index == 0) shipper.fetchOrders();
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.local_shipping_outlined),
            label: 'Đơn giao',
          ),
          NavigationDestination(icon: Icon(Icons.history), label: 'Lịch sử'),
          NavigationDestination(icon: Icon(Icons.bar_chart), label: 'Thống kê'),
        ],
      ),
      body: SafeArea(
        top: false,
        bottom: false,
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 720),
            child: switch (_tab) {
              1 => const DeliveryHistoryScreen(),
              2 => const ShipperStatsScreen(),
              _ => _buildDashboard(shipper),
            },
          ),
        ),
      ),
    );
  }

  VoidCallback? _shipperStartAction(ShipperOrderModel order) {
    if (context.watch<ShipperProvider>().isLoading) return null;
    return () async {
      try {
        await context.read<ShipperProvider>().startDelivery(order.id);
      } catch (error) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('$error')));
        }
      }
    };
  }

  Widget _buildDashboard(ShipperProvider shipper) {
    const filters = {
      'ALL': 'Tất cả',
      'SHIPPED': 'Cần giao',
      'DELIVERED': 'Đã giao',
      'DELIVERY_FAILED': 'Giao thất bại',
      'RETURNING_TO_SENDER': 'Cần hoàn kho',
    };
    return RefreshIndicator(
      onRefresh: shipper.fetchOrders,
      child: ListView.builder(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
        itemCount: shipper.orders.length + 2,
        itemBuilder: (context, index) {
          if (index == 0) {
            return Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Wrap(
                spacing: 6,
                runSpacing: 4,
                children: [
                  for (final filter in filters.entries)
                    ChoiceChip(
                      label: Text(filter.value),
                      selected: shipper.currentFilter == filter.key,
                      onSelected: (_) => shipper.setFilter(filter.key),
                    ),
                ],
              ),
            );
          }
          if (index <= shipper.orders.length) {
            return _buildOrderCard(shipper.orders[index - 1]);
          }
          if (shipper.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          if (shipper.ordersError case final error?) {
            return Column(
              children: [
                Text(error),
                TextButton(
                  onPressed: () =>
                      shipper.fetchOrders(loadMore: shipper.hasMore),
                  child: const Text('Thử lại'),
                ),
              ],
            );
          }
          if (shipper.hasMore) {
            return TextButton(
              onPressed: () => shipper.fetchOrders(loadMore: true),
              child: const Text('Tải thêm'),
            );
          }
          return shipper.orders.isEmpty
              ? const Center(child: Text('Không có đơn hàng trong mục này'))
              : const SizedBox.shrink();
        },
      ),
    );
  }

  Future<void> _reportFailure(ShipperOrderModel order) async {
    final reason = await showDialog<String>(
      context: context,
      builder: (_) => const _DeliveryFailureDialog(),
    );
    if (!mounted || reason == null) return;
    try {
      await context.read<ShipperProvider>().reportFailure(order.id, reason);
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
    }
  }

  Widget _buildOrderCard(ShipperOrderModel order) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header: Order ID & Status Badge
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: order.isDelivered
                  ? AppColors.successLight
                  : AppColors.primarySurface,
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(13),
              ),
            ),
            child: Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              runSpacing: 6,
              children: [
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      order.isDelivered
                          ? Icons.check_circle
                          : Icons.local_shipping,
                      size: 18,
                      color: order.isDelivered
                          ? AppColors.success
                          : AppColors.primary,
                    ),
                    const SizedBox(width: 6),
                    Flexible(
                      child: Text(
                        '#${order.orderNumber}',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                          color: order.isDelivered
                              ? AppColors.success
                              : AppColors.primary,
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
                    color: order.isDelivered
                        ? AppColors.success
                        : AppColors.warning,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    switch (order.status) {
                      'DELIVERED' => 'ĐÃ GIAO THÀNH CÔNG',
                      'SHIPPED' =>
                        order.deliveryStartedAt == null
                            ? 'CẦN GIAO HÀNG'
                            : 'ĐANG GIAO',
                      'DELIVERY_FAILED' => 'GIAO THẤT BẠI',
                      'RETURNING_TO_SENDER' => 'CẦN HOÀN KHO',
                      _ => order.status,
                    },
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

          // Body Details
          Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Customer Name & Phone with Call Action
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.person,
                      size: 18,
                      color: AppColors.textMuted,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            order.customerName,
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 15,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            order.customerPhone.isNotEmpty
                                ? order.customerPhone
                                : 'Không có SĐT',
                            style: const TextStyle(
                              color: AppColors.textMuted,
                              fontSize: 13,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (order.customerPhone.isNotEmpty)
                      ElevatedButton.icon(
                        onPressed: () => _makePhoneCall(order.customerPhone),
                        icon: const Icon(
                          Icons.phone,
                          size: 15,
                          color: Colors.white,
                        ),
                        label: const Text(
                          'Gọi ngay',
                          style: TextStyle(fontSize: 12),
                        ),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.success,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          minimumSize: Size.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 10),

                // Address
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(
                      Icons.location_on,
                      size: 18,
                      color: AppColors.danger,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            order.displayShippingAddress,
                            style: const TextStyle(
                              fontSize: 13,
                              color: AppColors.textMain,
                            ),
                          ),
                          if (order.isShipped &&
                              order.shippingAddress.isNotEmpty)
                            TextButton.icon(
                              onPressed: () =>
                                  _openDirections(order.shippingAddress),
                              icon: const Icon(
                                Icons.directions_outlined,
                                size: 18,
                              ),
                              label: const Text('Chỉ đường'),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),

                // Items Summary
                if (order.itemsSummary.isNotEmpty) ...[
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(
                        Icons.inventory_2_outlined,
                        size: 18,
                        color: AppColors.textMuted,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          order.itemsSummary.join(' • '),
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.textMuted,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                ],

                // COD / Payment Info Box
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: order.isCod
                        ? AppColors.warningLight
                        : AppColors.successLight,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Wrap(
                    alignment: WrapAlignment.spaceBetween,
                    spacing: 12,
                    runSpacing: 6,
                    children: [
                      Text(
                        order.isCod ? 'Thu tiền COD:' : 'Thanh toán:',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          color: order.isCod
                              ? AppColors.warning
                              : AppColors.success,
                        ),
                      ),
                      Text(
                        _currencyFormat.format(
                          order.isCod ? order.codAmount : order.totalAmount,
                        ),
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                          color: order.isCod
                              ? AppColors.warning
                              : AppColors.success,
                        ),
                      ),
                    ],
                  ),
                ),

                // Action Buttons
                const SizedBox(height: 12),
                if (order.codCollected > 0) ...[
                  const SizedBox(height: 8),
                  Text(
                    'COD đã thu: ${_currencyFormat.format(order.codCollected)}',
                  ),
                  Text(
                    'Đã bàn giao: ${_currencyFormat.format(order.codRemitted)}',
                  ),
                  Text(
                    'Chờ bàn giao: ${_currencyFormat.format(order.codCollected - order.codRemitted)}',
                  ),
                ],
                if (order.isShipped && order.deliveryStartedAt == null)
                  OutlinedButton.icon(
                    icon: const Icon(Icons.local_shipping_outlined),
                    label: const Text('Bắt đầu giao'),
                    onPressed: _shipperStartAction(order),
                  ),
                if (order.isShipped && order.deliveryStartedAt != null) ...[
                  OutlinedButton.icon(
                    onPressed: () => _reportFailure(order),
                    icon: const Icon(Icons.report_problem_outlined),
                    label: const Text('Báo giao thất bại'),
                  ),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: () async {
                        final res = await Navigator.push<bool>(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                DeliveryConfirmationScreen(order: order),
                          ),
                        );
                        if (res == true && mounted) {
                          context.read<ShipperProvider>().fetchOrders();
                        }
                      },
                      icon: const Icon(
                        Icons.camera_alt,
                        color: Colors.white,
                        size: 18,
                      ),
                      label: const Text(
                        'Chụp ảnh xác nhận giao hàng',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                        elevation: 1,
                      ),
                    ),
                  ),
                ] else if (order.isDelivered)
                  OutlinedButton.icon(
                    onPressed: () => _showProofDialog(order),
                    icon: const Icon(Icons.photo_outlined, size: 16),
                    label: const Text('Xem bằng chứng giao hàng'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.success,
                      side: const BorderSide(color: AppColors.success),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _DeliveryFailureDialog extends StatefulWidget {
  const _DeliveryFailureDialog();

  @override
  State<_DeliveryFailureDialog> createState() => _DeliveryFailureDialogState();
}

class _DeliveryFailureDialogState extends State<_DeliveryFailureDialog> {
  final _form = GlobalKey<FormState>();
  String _reason = '';

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Báo giao thất bại'),
      content: Form(
        key: _form,
        child: TextFormField(
          autofocus: true,
          maxLength: 500,
          maxLines: 3,
          onChanged: (value) => _reason = value.trim(),
          validator: (_) => _reason.isEmpty ? 'Nhập lý do giao thất bại' : null,
          decoration: const InputDecoration(labelText: 'Lý do'),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: () {
            if (_form.currentState!.validate()) Navigator.pop(context, _reason);
          },
          child: const Text('Gửi báo cáo'),
        ),
      ],
    );
  }
}
