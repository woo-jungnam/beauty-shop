import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/storage/shipper_settings_store.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';
import '../../../data/models/shipper_order_model.dart';
import '../../../data/models/shipper_return_model.dart';
import '../../../providers/shipper_provider.dart';
import '../delivery/shipper_order_detail_screen.dart';
import 'return_pickup_confirmation_screen.dart';
import 'shipper_return_detail_screen.dart';

class ShipperReturnTab extends StatefulWidget {
  const ShipperReturnTab({super.key});

  @override
  State<ShipperReturnTab> createState() => _ShipperReturnTabState();
}

class _ShipperReturnTabState extends State<ShipperReturnTab> {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _listController = ScrollController();
  Timer? _searchDebounce;
  bool _searchOpen = false;
  String _searchQuery = '';

  static const _filters = {
    'ALL': 'Tất cả thu hồi hoàn',
    'PICKUP_ASSIGNED': 'Cần lấy hàng hoàn',
    'PICKED_UP': 'Đã lấy · Chờ trả kho',
    'COMPLETED': 'Kho đã kiểm nhận',
    'RETURNING_TO_SENDER': 'Đơn giao thất bại hoàn kho',
  };

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      context.read<ShipperProvider>().fetchReturnPickups();
    });
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _searchController.dispose();
    _listController.dispose();
    super.dispose();
  }

  void _onSearchChanged(String val) {
    setState(() => _searchQuery = val);
    _searchDebounce?.cancel();
    final shipper = context.read<ShipperProvider>();
    _searchDebounce = Timer(const Duration(milliseconds: 350), () {
      if (!mounted) return;
      unawaited(shipper.setReturnSearchKeyword(val));
    });
  }

  void _clearSearch({bool close = false}) {
    _searchDebounce?.cancel();
    _searchController.clear();
    setState(() {
      _searchQuery = '';
      if (close) _searchOpen = false;
    });
    unawaited(context.read<ShipperProvider>().setReturnSearchKeyword(''));
  }

  Future<void> _refreshAll(ShipperProvider shipper) async {
    await Future.wait([
      shipper.fetchReturnPickups(),
      shipper.fetchOrders(background: true),
    ]);
  }

  Future<void> _startPickup(ShipperReturnModel ret) async {
    if (ShipperSettingsStore.instance.confirmBeforeStart) {
      final confirm = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Bắt đầu thu hồi hàng hoàn'),
          content: Text(
            'Xác nhận bắt đầu đi thu hồi phiếu #${ret.id} (đơn #${ret.orderNumber})?',
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
      if (confirm != true || !mounted) return;
    }
    try {
      await context.read<ShipperProvider>().startReturnPickup(ret.id);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _reportFailure(ShipperReturnModel ret) async {
    final reason = await showReturnPickupFailureDialog(context);
    if (!mounted || reason == null) return;
    try {
      await context.read<ShipperProvider>().reportReturnPickupFailure(
        ret.id,
        reason,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã ghi nhận thu hồi thất bại')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _handoverToWarehouse(ShipperReturnModel ret) async {
    final controller = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Báo đã bàn giao cho kho'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Xác nhận đã nộp kiện hàng hoàn phiếu #${ret.id} về kho BeautyShop?',
            ),
            const SizedBox(height: 10),
            TextField(
              controller: controller,
              maxLength: 500,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: 'Ghi chú bàn giao (tùy chọn)',
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
            child: const Text('Xác nhận'),
          ),
        ],
      ),
    );
    final notes = controller.text;
    controller.dispose();
    if (ok != true || !mounted) return;
    try {
      await context.read<ShipperProvider>().handoverReturnToWarehouse(
        ret.id,
        notes: notes,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã báo bàn giao hàng hoàn về kho')),
        );
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  List<ShipperReturnModel> _filterReturns(
    List<ShipperReturnModel> rows,
    String filter,
  ) {
    if (filter == 'RETURNING_TO_SENDER') return const [];
    final q = _searchQuery.trim().toLowerCase();
    return rows.where((r) {
      if (q.isEmpty) return true;
      return r.orderNumber.toLowerCase().contains(q) ||
          r.customerName.toLowerCase().contains(q) ||
          r.customerPhone.toLowerCase().contains(q) ||
          r.pickupAddress.toLowerCase().contains(q) ||
          r.reason.toLowerCase().contains(q);
    }).toList();
  }

  List<ShipperOrderModel> _returningOrders(ShipperProvider shipper) {
    if (shipper.returnFilter != 'ALL' &&
        shipper.returnFilter != 'RETURNING_TO_SENDER') {
      return const [];
    }
    final q = _searchQuery.trim().toLowerCase();
    return shipper.orders.where((o) {
      if (!o.isReturningToSender) return false;
      if (q.isEmpty) return true;
      return o.orderNumber.toLowerCase().contains(q) ||
          o.customerName.toLowerCase().contains(q) ||
          o.customerPhone.toLowerCase().contains(q) ||
          o.shippingAddress.toLowerCase().contains(q);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final shipper = context.watch<ShipperProvider>();
    final visibleReturns = _filterReturns(
      shipper.returnPickups,
      shipper.returnFilter,
    );
    final returningOrders = _returningOrders(shipper);

    final pendingCount = shipper.returnPickups
        .where((r) => r.isPickupAssigned)
        .length;
    final custodyCount =
        shipper.returnPickups.where((r) => r.isPickedUp).length +
        returningOrders.length;

    return RefreshIndicator(
      onRefresh: () => _refreshAll(shipper),
      child: ListView(
        controller: _listController,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: ShipperLayout.pagePadding,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Thu hồi hàng hoàn',
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: AppColors.textMain,
                        ),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.primarySurface,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Text(
                        '${visibleReturns.length + returningOrders.length} mục',
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppColors.primaryDark,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: _searchOpen
                          ? 'Đóng tìm kiếm thu hồi'
                          : 'Tìm kiếm thu hồi',
                      visualDensity: VisualDensity.compact,
                      icon: Icon(
                        _searchOpen
                            ? Icons.search_off_rounded
                            : Icons.search_rounded,
                        color: _searchOpen
                            ? AppColors.primary
                            : AppColors.textMuted,
                      ),
                      onPressed: () {
                        if (_searchOpen) {
                          _clearSearch(close: true);
                        } else {
                          setState(() => _searchOpen = true);
                        }
                      },
                    ),
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  'Cần lấy: $pendingCount phiếu • Đang giữ chờ trả kho: $custodyCount kiện',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: AppColors.textMuted,
                  ),
                ),
                if (_searchOpen || _searchQuery.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  TextField(
                    controller: _searchController,
                    onChanged: _onSearchChanged,
                    onSubmitted: (val) {
                      _searchDebounce?.cancel();
                      unawaited(shipper.setReturnSearchKeyword(val));
                    },
                    textInputAction: TextInputAction.search,
                    decoration: InputDecoration(
                      hintText: 'Tìm mã đơn, khách, SĐT, lý do…',
                      isDense: true,
                      prefixIcon: const Icon(Icons.search_rounded, size: 20),
                      suffixIcon: _searchQuery.isNotEmpty
                          ? IconButton(
                              tooltip: 'Xóa tìm kiếm',
                              icon: const Icon(Icons.close_rounded, size: 18),
                              onPressed: () => _clearSearch(),
                            )
                          : null,
                      filled: true,
                      fillColor: AppColors.card,
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(
                          ShipperLayout.innerRadius,
                        ),
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 10),
                KeyedSubtree(
                  key: const ValueKey('shipper-return-status-filter'),
                  child: DropdownButtonFormField<String>(
                    key: ValueKey('ret-status-${shipper.returnFilter}'),
                    initialValue: _filters.containsKey(shipper.returnFilter)
                        ? shipper.returnFilter
                        : 'ALL',
                    isExpanded: true,
                    decoration: InputDecoration(
                      labelText: 'Lọc nhiệm vụ thu hồi',
                      isDense: true,
                      filled: true,
                      fillColor: AppColors.card,
                      prefixIcon: const Icon(
                        Icons.assignment_return_outlined,
                        size: 18,
                        color: AppColors.primary,
                      ),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(
                          ShipperLayout.innerRadius,
                        ),
                      ),
                    ),
                    items: [
                      for (final entry in _filters.entries)
                        DropdownMenuItem<String>(
                          value: entry.key,
                          child: Text(
                            entry.value,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                    ],
                    onChanged: shipper.isReturnsLoading
                        ? null
                        : (value) {
                            if (value != null) {
                              unawaited(shipper.setReturnFilter(value));
                            }
                          },
                  ),
                ),
              ],
            ),
          ),
          if (shipper.isReturnsLoading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 28),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (shipper.returnsError case final err?)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  children: [
                    Text(err, textAlign: TextAlign.center),
                    const SizedBox(height: 10),
                    FilledButton(
                      onPressed: () => _refreshAll(shipper),
                      child: const Text('Thử lại'),
                    ),
                  ],
                ),
              ),
            )
          else ...[
            for (final ret in visibleReturns)
              _buildReturnCard(ret, shipper.isReturnsLoading),
            if (returningOrders.isNotEmpty) ...[
              const Padding(
                padding: EdgeInsets.only(top: 6, bottom: 8),
                child: Text(
                  'Đơn giao thất bại cần hoàn kho',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 13.5,
                    color: AppColors.warning,
                  ),
                ),
              ),
              for (final order in returningOrders)
                _buildReturningOrderCard(order),
            ],
            if (visibleReturns.isEmpty && returningOrders.isEmpty)
              const Card(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 28, horizontal: 16),
                  child: Column(
                    children: [
                      Icon(
                        Icons.assignment_turned_in_outlined,
                        size: 40,
                        color: AppColors.textMuted,
                      ),
                      SizedBox(height: 8),
                      Text(
                        'Chưa có yêu cầu thu hồi hàng hoàn nào',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontWeight: FontWeight.w600,
                          color: AppColors.textMuted,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
          if (shipper.returnTotalPages > 1 ||
              shipper.hasPreviousReturnPage ||
              shipper.hasNextReturnPage)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  OutlinedButton.icon(
                    onPressed:
                        (!shipper.isReturnsLoading &&
                            shipper.hasPreviousReturnPage)
                        ? shipper.previousReturnPage
                        : null,
                    icon: const Icon(Icons.chevron_left, size: 18),
                    label: const Text('Trước'),
                  ),
                  Text(
                    'Trang ${shipper.returnCurrentPage + 1}/${shipper.returnTotalPages < 1 ? 1 : shipper.returnTotalPages}',
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      color: AppColors.textMuted,
                    ),
                  ),
                  OutlinedButton.icon(
                    onPressed:
                        (!shipper.isReturnsLoading && shipper.hasNextReturnPage)
                        ? shipper.nextReturnPage
                        : null,
                    icon: const Icon(Icons.chevron_right, size: 18),
                    label: const Text('Sau'),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildReturnCard(ShipperReturnModel ret, bool busy) {
    final style = ShipperStatusStyle.fromReturn(ret);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      color: AppColors.card,
      elevation: 0,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
        side: const BorderSide(color: AppColors.border),
      ),
      child: InkWell(
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => ShipperReturnDetailScreen(returnRequest: ret),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
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
                      Icon(style.icon, size: 18, color: style.foreground),
                      const SizedBox(width: 6),
                      Flexible(
                        child: Text(
                          'Thu hồi #${ret.id} · #${ret.orderNumber}',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 14.5,
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
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(
                        Icons.person_outline_rounded,
                        size: 18,
                        color: AppColors.textMuted,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              ret.customerName,
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 15,
                              ),
                            ),
                            if (ret.customerPhone.isNotEmpty)
                              Text(
                                ret.customerPhone,
                                style: const TextStyle(
                                  fontSize: 13,
                                  color: AppColors.textMuted,
                                ),
                              ),
                          ],
                        ),
                      ),
                      if (ret.customerPhone.isNotEmpty)
                        FilledButton.icon(
                          onPressed: () =>
                              launchPhoneCall(context, ret.customerPhone),
                          icon: const Icon(Icons.phone_rounded, size: 15),
                          label: const Text(
                            'Gọi',
                            style: TextStyle(fontSize: 12),
                          ),
                          style: FilledButton.styleFrom(
                            backgroundColor: AppColors.success,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 6,
                            ),
                            minimumSize: const Size(0, 34),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(
                        Icons.location_on_outlined,
                        size: 18,
                        color: AppColors.danger,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          ret.displayPickupAddress,
                          style: const TextStyle(fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                  if (ret.itemsSummary.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    const Divider(height: 1),
                    const SizedBox(height: 8),
                    for (final item in ret.itemsSummary.take(2))
                      Text(
                        '• $item',
                        style: const TextStyle(fontSize: 13),
                      ),
                  ],
                  const SizedBox(height: 8),
                  Text(
                    'Lý do: ${ret.reason}',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 12.5,
                      color: AppColors.textMuted,
                    ),
                  ),
                  if (ret.isPickupAssigned || ret.isPickedUp)
                    const SizedBox(height: 12),
                  if (ret.isPickupAssigned && !ret.isStarted)
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: busy ? null : () => _startPickup(ret),
                        icon: const Icon(Icons.assignment_return_outlined),
                        label: const Text('Bắt đầu đi lấy hàng hoàn'),
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          minimumSize: const Size(0, 44),
                        ),
                      ),
                    ),
                  if (ret.isPickupAssigned && ret.isStarted) ...[
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: () async {
                          final ok = await Navigator.push<bool>(
                            context,
                            MaterialPageRoute(
                              builder: (_) => ReturnPickupConfirmationScreen(
                                returnRequest: ret,
                              ),
                            ),
                          );
                          if (ok == true && mounted) {
                            await context
                                .read<ShipperProvider>()
                                .fetchReturnPickups();
                          }
                        },
                        icon: const Icon(Icons.camera_alt_outlined, size: 18),
                        label: const Text('Chụp ảnh xác nhận lấy hàng'),
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          minimumSize: const Size(0, 44),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: () => _reportFailure(ret),
                        icon: const Icon(
                          Icons.report_problem_outlined,
                          size: 18,
                        ),
                        label: const Text('Báo thu hồi thất bại'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.danger,
                          side: BorderSide(
                            color: AppColors.danger.withValues(alpha: 0.45),
                          ),
                          minimumSize: const Size(0, 42),
                        ),
                      ),
                    ),
                  ],
                  if (ret.isPickedUp && !ret.isHandoverRequested)
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: busy
                            ? null
                            : () => _handoverToWarehouse(ret),
                        icon: const Icon(Icons.warehouse_outlined, size: 18),
                        label: const Text('Báo đã bàn giao cho kho'),
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.warning,
                          foregroundColor: Colors.white,
                          minimumSize: const Size(0, 44),
                        ),
                      ),
                    ),
                  if (ret.isPickedUp && ret.isHandoverRequested)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AppColors.infoLight,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Text(
                        'Đã báo bàn giao về kho · Chờ thủ kho kiểm nhận',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 12.5,
                          fontWeight: FontWeight.w600,
                          color: AppColors.info,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildReturningOrderCard(ShipperOrderModel order) {
    final style = ShipperStatusStyle.fromOrder(order);
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      color: AppColors.card,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
        side: const BorderSide(color: AppColors.border),
      ),
      child: ListTile(
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => ShipperOrderDetailScreen(
              order: order,
              enableDeliveryActions: true,
            ),
          ),
        ),
        leading: CircleAvatar(
          backgroundColor: style.background,
          child: Icon(style.icon, color: style.foreground, size: 20),
        ),
        title: Text(
          '#${order.orderNumber} · ${order.customerName}',
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
        ),
        subtitle: Text(
          '${style.label} · Vui lòng bàn giao kiện hàng lại cho kho BeautyShop',
          style: const TextStyle(fontSize: 12.5),
        ),
        trailing: const Icon(Icons.chevron_right_rounded),
      ),
    );
  }
}

