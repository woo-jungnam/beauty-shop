import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_shipper/core/storage/shipper_settings_store.dart';
import 'package:beautyshop_shipper/core/theme/app_colors.dart';
import 'package:beautyshop_shipper/core/utils/shipper_formatters.dart';
import 'package:beautyshop_shipper/data/models/shipper_order_model.dart';
import 'package:beautyshop_shipper/providers/auth_provider.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';
import 'package:beautyshop_shipper/presentation/screens/delivery/delivery_confirmation_screen.dart';

import '../account/shipper_account_screen.dart';
import '../delivery/delivery_history_screen.dart';
import '../delivery/shipper_order_detail_screen.dart';
import '../delivery/shipper_stats_screen.dart';
import '../notification/notification_screen.dart';

class ShipperDashboardScreen extends StatefulWidget {
  const ShipperDashboardScreen({super.key});

  @override
  State<ShipperDashboardScreen> createState() => _ShipperDashboardScreenState();
}

class _ShipperDashboardScreenState extends State<ShipperDashboardScreen>
    with WidgetsBindingObserver {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _listController = ScrollController();
  Timer? _pollTimer;
  Timer? _searchDebounce;
  int _tab = 0;
  final Set<int> _visitedTabs = {0};
  bool _searchOpen = false;
  String _searchQuery = '';

  void _startPolling() {
    _pollTimer?.cancel();
    if (!mounted) return;
    final shipper = context.read<ShipperProvider>();
    unawaited(shipper.refreshUnreadCount());
    final settings = ShipperSettingsStore.instance;
    if (!settings.autoRefreshEnabled) return;
    _pollTimer = Timer.periodic(
      Duration(seconds: settings.refreshIntervalSeconds),
      (_) {
        unawaited(shipper.refreshUnreadCount());
        if (_tab == 0) unawaited(shipper.fetchOrders(background: true));
      },
    );
  }

  void _onSettingsChanged() {
    if (!mounted) return;
    _startPolling();
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
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    ShipperSettingsStore.instance.addListener(_onSettingsChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      context.read<ShipperProvider>().fetchOrders();
      _startPolling();
    });
  }

  @override
  void dispose() {
    ShipperSettingsStore.instance.removeListener(_onSettingsChanged);
    WidgetsBinding.instance.removeObserver(this);
    _pollTimer?.cancel();
    _searchDebounce?.cancel();
    _searchController.dispose();
    _listController.dispose();
    super.dispose();
  }

  Future<void> _handleLogout() async {
    _pollTimer?.cancel();
    final loggedOut = await confirmAndLogout(context);
    if (!loggedOut && mounted) {
      _startPolling();
    }
  }

  Future<void> _openOrderDetail(ShipperOrderModel order) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ShipperOrderDetailScreen(
          order: order,
          enableDeliveryActions: true,
        ),
      ),
    );
  }

  Future<void> _startDelivery(ShipperOrderModel order) async {
    if (ShipperSettingsStore.instance.confirmBeforeStart) {
      final confirm = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Bắt đầu giao hàng'),
          content: Text('Xác nhận bắt đầu giao đơn #${order.orderNumber}?'),
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
      await context.read<ShipperProvider>().startDelivery(order.id);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> _reportFailure(ShipperOrderModel order) async {
    final reason = await showDeliveryFailureDialog(context);
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

  void _onSearchChanged(String val) {
    setState(() => _searchQuery = val);
    _searchDebounce?.cancel();
    final shipper = context.read<ShipperProvider>();
    _searchDebounce = Timer(const Duration(milliseconds: 350), () {
      if (!mounted) return;
      unawaited(shipper.setSearchKeyword(val));
    });
  }

  void _clearSearch({bool close = false}) {
    _searchDebounce?.cancel();
    _searchController.clear();
    setState(() {
      _searchQuery = '';
      if (close) _searchOpen = false;
    });
    unawaited(context.read<ShipperProvider>().setSearchKeyword(''));
  }

  Future<void> _changePage(Future<void> Function() action) async {
    await action();
    if (!mounted || !_listController.hasClients) return;
    await _listController.animateTo(
      0,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOut,
    );
  }

  InputDecoration _selectDecoration({
    String? labelText,
    Widget? prefixIcon,
    EdgeInsetsGeometry? contentPadding,
  }) {
    final border = OutlineInputBorder(
      borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
      borderSide: const BorderSide(color: AppColors.border),
    );
    return InputDecoration(
      labelText: labelText,
      labelStyle: const TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.w600,
        color: AppColors.textMuted,
      ),
      isDense: true,
      prefixIcon: prefixIcon,
      filled: true,
      fillColor: AppColors.card,
      contentPadding:
          contentPadding ??
          const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      border: border,
      enabledBorder: border,
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
        borderSide: const BorderSide(color: AppColors.primary, width: 1.4),
      ),
    );
  }

  List<int?> _buildPaginationPages(int currentPage, int totalPages) {
    if (totalPages <= 5) {
      return List<int?>.generate(totalPages, (index) => index);
    }
    final pages = <int?>[0];
    var start = (currentPage - 1).clamp(1, totalPages - 2);
    var end = (currentPage + 1).clamp(1, totalPages - 2);
    if (currentPage <= 2) {
      start = 1;
      end = 3;
    } else if (currentPage >= totalPages - 3) {
      start = totalPages - 4;
      end = totalPages - 2;
    }
    if (start > 2) {
      pages.add(null);
    } else if (start == 2) {
      pages.add(1);
    }
    for (var i = start; i <= end; i++) {
      pages.add(i);
    }
    if (end < totalPages - 3) {
      pages.add(null);
    } else if (end == totalPages - 3) {
      pages.add(totalPages - 2);
    }
    pages.add(totalPages - 1);
    return pages;
  }

  List<ShipperOrderModel> _filterOrdersByQuery(List<ShipperOrderModel> orders) {
    final query = _searchQuery.trim().toLowerCase();
    if (query.isEmpty) return orders;
    return orders.where((order) {
      return order.orderNumber.toLowerCase().contains(query) ||
          order.customerName.toLowerCase().contains(query) ||
          order.customerPhone.toLowerCase().contains(query) ||
          order.shippingAddress.toLowerCase().contains(query);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    if (!auth.isAuthenticated) return const SizedBox.shrink();
    final shipper = context.watch<ShipperProvider>();
    final driverName = auth.currentFullName ?? auth.currentUsername ?? 'Bưu tá';

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        elevation: 0,
        title: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'BeautyShip',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
            ),
            Text(
              'Tài xế: $driverName',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12, color: AppColors.textMuted),
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
          PopupMenuButton<String>(
            tooltip: 'Menu tài khoản',
            icon: const Icon(Icons.more_vert_rounded),
            onSelected: (value) {
              if (value == 'account') {
                setState(() {
                  _tab = 3;
                  _visitedTabs.add(3);
                });
              } else if (value == 'logout') {
                _handleLogout();
              }
            },
            itemBuilder: (context) => [
              PopupMenuItem<String>(
                enabled: false,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      driverName,
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        color: AppColors.textMain,
                      ),
                    ),
                    if (auth.currentUsername != null)
                      Text(
                        '@${auth.currentUsername}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textMuted,
                        ),
                      ),
                  ],
                ),
              ),
              const PopupMenuDivider(),
              const PopupMenuItem<String>(
                value: 'account',
                child: Row(
                  children: [
                    Icon(
                      Icons.manage_accounts_outlined,
                      size: 18,
                      color: AppColors.primary,
                    ),
                    SizedBox(width: 8),
                    Text('Hồ sơ & Cài đặt'),
                  ],
                ),
              ),
              const PopupMenuItem<String>(
                value: 'logout',
                child: Row(
                  children: [
                    Icon(
                      Icons.logout_rounded,
                      size: 18,
                      color: AppColors.danger,
                    ),
                    SizedBox(width: 8),
                    Text(
                      'Đăng xuất',
                      style: TextStyle(
                        color: AppColors.danger,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (index) {
          setState(() {
            _tab = index;
            _visitedTabs.add(index);
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.local_shipping_outlined),
            selectedIcon: Icon(Icons.local_shipping),
            label: 'Đơn giao',
          ),
          NavigationDestination(icon: Icon(Icons.history), label: 'Lịch sử'),
          NavigationDestination(icon: Icon(Icons.bar_chart), label: 'Thống kê'),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Tài khoản',
          ),
        ],
      ),
      body: ShipperPageContainer(
        child: IndexedStack(
          index: _tab,
          children: [
            _buildDashboard(shipper),
            _visitedTabs.contains(1)
                ? const DeliveryHistoryScreen()
                : const SizedBox.shrink(),
            _visitedTabs.contains(2)
                ? const ShipperStatsScreen()
                : const SizedBox.shrink(),
            _visitedTabs.contains(3)
                ? const ShipperAccountScreen()
                : const SizedBox.shrink(),
          ],
        ),
      ),
    );
  }

  Widget _buildDashboard(ShipperProvider shipper) {
    const filters = {
      'ALL': 'Tất cả',
      'SHIPPED': 'Cần giao',
      'DELIVERED': 'Đã giao',
      'DELIVERY_FAILED': 'Giao thất bại',
      'RETURNING_TO_SENDER': 'Cần hoàn kho',
    };

    final visibleOrders = _filterOrdersByQuery(shipper.orders);

    return RefreshIndicator(
      onRefresh: shipper.fetchOrders,
      child: ListView.builder(
        controller: _listController,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: ShipperLayout.pagePadding,
        itemCount: visibleOrders.length + 2,
        itemBuilder: (context, index) {
          if (index == 0) {
            return _buildDashboardHeader(
              shipper: shipper,
              filters: filters,
              visibleCount: visibleOrders.length,
            );
          }
          if (index <= visibleOrders.length) {
            return _buildOrderCard(visibleOrders[index - 1], shipper.isLoading);
          }
          return _buildDashboardFooter(shipper, visibleOrders);
        },
      ),
    );
  }

  Widget _buildDashboardHeader({
    required ShipperProvider shipper,
    required Map<String, String> filters,
    required int visibleCount,
  }) {
    final activeShipped = shipper.orders.where((o) => o.isShipped).toList();
    final pendingCod = activeShipped.fold<double>(
      0,
      (sum, o) => sum + (o.isCod ? o.codAmount : 0),
    );
    final unremittedCod = shipper.orders.fold<double>(
      0,
      (sum, o) =>
          sum + (o.codCollected > o.codRemitted ? o.codCollected - o.codRemitted : 0),
    );
    final displayCount =
        (shipper.totalElements > 0 && visibleCount == shipper.orders.length)
            ? shipper.totalElements
            : visibleCount;

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Danh sách đơn',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: AppColors.textMain,
                  ),
                ),
              ),
              if (!shipper.isLoading || shipper.orders.isNotEmpty)
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
                    '$displayCount đơn',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: AppColors.primaryDark,
                    ),
                  ),
                ),
              IconButton(
                tooltip: _searchOpen ? 'Đóng tìm kiếm' : 'Tìm kiếm đơn',
                visualDensity: VisualDensity.compact,
                icon: Icon(
                  _searchOpen ? Icons.search_off_rounded : Icons.search_rounded,
                  color: _searchOpen ? AppColors.primary : AppColors.textMuted,
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
          if (activeShipped.isNotEmpty || unremittedCod > 0) ...[
            const SizedBox(height: 2),
            Text(
              [
                'Đang phụ trách: ${activeShipped.length} đơn',
                if (pendingCod > 0) 'COD cần thu: ${formatCurrency(pendingCod)}',
                if (unremittedCod > 0)
                  'Chờ nộp: ${formatCurrency(unremittedCod)}',
              ].join(' • '),
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w500,
                color: AppColors.textMuted,
              ),
            ),
          ],
          if (_searchOpen || _searchQuery.isNotEmpty) ...[
            const SizedBox(height: 8),
            TextField(
              controller: _searchController,
              onChanged: _onSearchChanged,
              onSubmitted: (val) {
                _searchDebounce?.cancel();
                unawaited(shipper.setSearchKeyword(val));
              },
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: 'Tìm mã đơn, tên khách, SĐT, địa chỉ…',
                hintStyle: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textMuted,
                ),
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
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(
                    ShipperLayout.innerRadius,
                  ),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
              ),
            ),
          ],
          const SizedBox(height: 10),
          KeyedSubtree(
            key: const ValueKey('shipper-status-filter'),
            child: DropdownButtonFormField<String>(
              key: ValueKey('status-${shipper.currentFilter}'),
              initialValue: filters.containsKey(shipper.currentFilter)
                  ? shipper.currentFilter
                  : 'ALL',
              isExpanded: true,
              icon: const Icon(Icons.keyboard_arrow_down_rounded),
              decoration: _selectDecoration(
                labelText: 'Lọc theo trạng thái',
                prefixIcon: const Icon(
                  Icons.filter_list_rounded,
                  size: 18,
                  color: AppColors.primary,
                ),
              ),
              items: [
                for (final entry in filters.entries)
                  DropdownMenuItem<String>(
                    value: entry.key,
                    child: Row(
                      children: [
                        Container(
                          width: 8,
                          height: 8,
                          decoration: BoxDecoration(
                            color: entry.key == 'ALL'
                                ? AppColors.primary
                                : ShipperStatusStyle.of(entry.key).foreground,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            entry.value,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
              onChanged: shipper.isLoading
                  ? null
                  : (value) {
                      if (value == null) return;
                      unawaited(_changePage(() => shipper.setFilter(value)));
                    },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDashboardFooter(
    ShipperProvider shipper,
    List<ShipperOrderModel> visibleOrders,
  ) {
    if (shipper.isLoading) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 28),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (shipper.ordersError case final error?) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            children: [
              const Icon(
                Icons.cloud_off_outlined,
                size: 36,
                color: AppColors.textMuted,
              ),
              const SizedBox(height: 8),
              Text(
                error,
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.textMain),
              ),
              const SizedBox(height: 10),
              FilledButton(
                onPressed: () => shipper.fetchOrders(),
                child: const Text('Thử lại'),
              ),
            ],
          ),
        ),
      );
    }
    if (visibleOrders.isEmpty) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 16),
          child: Column(
            children: [
              const Icon(
                Icons.inbox_outlined,
                size: 40,
                color: AppColors.textMuted,
              ),
              const SizedBox(height: 8),
              Text(
                _searchQuery.trim().isNotEmpty
                    ? 'Không tìm thấy đơn khớp từ khóa'
                    : 'Không có đơn hàng trong mục này',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontWeight: FontWeight.w600,
                  color: AppColors.textMuted,
                ),
              ),
              if (shipper.hasPreviousPage) ...[
                const SizedBox(height: 12),
                OutlinedButton(
                  onPressed: () => _changePage(() => shipper.goToPage(0)),
                  child: const Text('Quay lại trang đầu'),
                ),
              ],
            ],
          ),
        ),
      );
    }

    final totalCount =
        shipper.totalElements > 0 ? shipper.totalElements : visibleOrders.length;
    final effectiveTotalPages = shipper.totalPages > 0
        ? shipper.totalPages
        : ((totalCount + shipper.pageSize - 1) ~/ shipper.pageSize);
    final showPagination =
        effectiveTotalPages > 1 ||
        shipper.hasPreviousPage ||
        shipper.hasNextPage ||
        shipper.totalElements > 0;
    if (!showPagination) {
      return const SizedBox.shrink();
    }

    final displayTotalPages = effectiveTotalPages < 1 ? 1 : effectiveTotalPages;
    final currentPageDisplay = (shipper.currentPage + 1).clamp(
      1,
      displayTotalPages,
    );
    final startItem = (shipper.currentPage * shipper.pageSize) + 1;
    final rawEnd = startItem + visibleOrders.length - 1;
    final endItem = rawEnd > totalCount ? totalCount : rawEnd;
    final pageItems = _buildPaginationPages(
      currentPageDisplay - 1,
      displayTotalPages,
    );

    return Card(
      key: const ValueKey('shipper-pagination-card'),
      margin: const EdgeInsets.only(top: 4, bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 10,
              runSpacing: 8,
              children: [
                Text(
                  'Trang $currentPageDisplay/$displayTotalPages • $startItem–$endItem / $totalCount đơn',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textMuted,
                  ),
                ),
                SizedBox(
                  width: 148,
                  child: KeyedSubtree(
                    key: const ValueKey('shipper-page-size-select'),
                    child: DropdownButtonFormField<int>(
                      key: ValueKey('page-size-${shipper.pageSize}'),
                      initialValue: ShipperProvider.pageSizeOptions.contains(
                            shipper.pageSize,
                          )
                          ? shipper.pageSize
                          : ShipperProvider.defaultPageSize,
                      isExpanded: true,
                      icon: const Icon(
                        Icons.keyboard_arrow_down_rounded,
                        size: 18,
                      ),
                      decoration: _selectDecoration(
                        labelText: 'Số đơn/trang',
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 8,
                        ),
                      ),
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textMain,
                      ),
                      items: [
                        for (final size in ShipperProvider.pageSizeOptions)
                          DropdownMenuItem<int>(
                            value: size,
                            child: Text(
                              '$size / trang',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                      ],
                      onChanged: shipper.isLoading
                          ? null
                          : (size) {
                              if (size == null) return;
                              unawaited(
                                _changePage(() => shipper.setPageSize(size)),
                              );
                            },
                    ),
                  ),
                ),
              ],
            ),
            if (displayTotalPages > 1 ||
                shipper.hasPreviousPage ||
                shipper.hasNextPage) ...[
              const SizedBox(height: 10),
              Center(
                child: Wrap(
                  alignment: WrapAlignment.center,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    OutlinedButton.icon(
                      key: const ValueKey('shipper-page-prev'),
                      onPressed: (!shipper.isLoading && shipper.hasPreviousPage)
                          ? () => _changePage(shipper.previousPage)
                          : null,
                      icon: const Icon(Icons.chevron_left_rounded, size: 18),
                      label: const Text('Trước'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.textMain,
                        side: const BorderSide(color: AppColors.border),
                        visualDensity: VisualDensity.compact,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 8,
                        ),
                        minimumSize: const Size(0, 36),
                      ),
                    ),
                    for (final pageIndex in pageItems)
                      if (pageIndex == null)
                        const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 4),
                          child: Text(
                            '…',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: AppColors.textMuted,
                            ),
                          ),
                        )
                      else if (pageIndex == shipper.currentPage)
                        FilledButton(
                          key: ValueKey('shipper-page-btn-${pageIndex + 1}'),
                          onPressed: () {},
                          style: FilledButton.styleFrom(
                            backgroundColor: AppColors.primary,
                            foregroundColor: Colors.white,
                            visualDensity: VisualDensity.compact,
                            padding: const EdgeInsets.symmetric(horizontal: 10),
                            minimumSize: const Size(36, 36),
                          ),
                          child: Text('${pageIndex + 1}'),
                        )
                      else
                        OutlinedButton(
                          key: ValueKey('shipper-page-btn-${pageIndex + 1}'),
                          onPressed: shipper.isLoading
                              ? null
                              : () => _changePage(
                                  () => shipper.goToPage(pageIndex),
                                ),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: AppColors.textMain,
                            side: const BorderSide(color: AppColors.border),
                            visualDensity: VisualDensity.compact,
                            padding: const EdgeInsets.symmetric(horizontal: 10),
                            minimumSize: const Size(36, 36),
                          ),
                          child: Text('${pageIndex + 1}'),
                        ),
                    OutlinedButton.icon(
                      key: const ValueKey('shipper-page-next'),
                      onPressed: (!shipper.isLoading && shipper.hasNextPage)
                          ? () => _changePage(shipper.nextPage)
                          : null,
                      iconAlignment: IconAlignment.end,
                      icon: const Icon(Icons.chevron_right_rounded, size: 18),
                      label: const Text('Sau'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.textMain,
                        side: const BorderSide(color: AppColors.border),
                        visualDensity: VisualDensity.compact,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 8,
                        ),
                        minimumSize: const Size(0, 36),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildOrderCard(ShipperOrderModel order, bool providerBusy) {
    final style = ShipperStatusStyle.fromOrder(order);

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
        onTap: () => _openOrderDetail(order),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header synced with semantic order status colors
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
                          '#${order.orderNumber}',
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: style.foreground,
                          ),
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Flexible(
                        child: Container(
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
                      ),
                      const SizedBox(width: 4),
                      Icon(
                        Icons.chevron_right_rounded,
                        size: 20,
                        color: style.foreground,
                      ),
                    ],
                  ),
                ],
              ),
            ),

            Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Recipient & call action
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
                              order.customerName,
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 15,
                                color: AppColors.textMain,
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
                        FilledButton.icon(
                          onPressed: () =>
                              launchPhoneCall(context, order.customerPhone),
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
                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 10),

                  // Shipping Address & flush-left Directions action
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
                                order.shippingAddress.isNotEmpty) ...[
                              const SizedBox(height: 6),
                              OutlinedButton.icon(
                                onPressed: () => openMapDirections(
                                  context,
                                  order.shippingAddress,
                                ),
                                icon: const Icon(
                                  Icons.directions_outlined,
                                  size: 16,
                                ),
                                label: const Text(
                                  'Chỉ đường',
                                  style: TextStyle(fontSize: 12),
                                ),
                                style: OutlinedButton.styleFrom(
                                  foregroundColor: AppColors.primary,
                                  side: const BorderSide(
                                    color: AppColors.border,
                                  ),
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 10,
                                    vertical: 6,
                                  ),
                                  minimumSize: const Size(0, 34),
                                  tapTargetSize:
                                      MaterialTapTargetSize.shrinkWrap,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),

                  if (order.items.isNotEmpty ||
                      order.itemsSummary.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    const Divider(height: 1),
                    const SizedBox(height: 10),
                    const Text(
                      'Sản phẩm trong đơn',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textMuted,
                      ),
                    ),
                    OrderItemsSection(order: order, preview: true),
                  ],

                  const SizedBox(height: 12),

                  // COD / Payment Highlight Box
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 10,
                    ),
                    decoration: BoxDecoration(
                      color: order.isCod
                          ? AppColors.warningLight
                          : AppColors.successLight,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Wrap(
                      alignment: WrapAlignment.spaceBetween,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 12,
                      runSpacing: 4,
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
                          formatCurrency(
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

                  if (order.codCollected > 0) ...[
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 8,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceMuted,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'COD đã thu: ${formatCurrency(order.codCollected)}',
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.textMain,
                            ),
                          ),
                          Text(
                            'Đã bàn giao: ${formatCurrency(order.codRemitted)}',
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.textMuted,
                            ),
                          ),
                          Text(
                            'Chờ bàn giao: ${formatCurrency(order.codCollected - order.codRemitted)}',
                            style: const TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: AppColors.warning,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],

                  // Consistent full-width action buttons with clear hierarchy
                  if (order.isShipped || order.isDelivered)
                    const SizedBox(height: 12),
                  if (order.isShipped && order.deliveryStartedAt == null)
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: providerBusy
                            ? null
                            : () => _startDelivery(order),
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          minimumSize: const Size(0, 44),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                        icon: const Icon(Icons.local_shipping_outlined),
                        label: const Text('Bắt đầu giao'),
                      ),
                    ),
                  if (order.isShipped && order.deliveryStartedAt != null) ...[
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
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
                        icon: const Icon(Icons.camera_alt_outlined, size: 18),
                        label: const Text(
                          'Chụp ảnh xác nhận giao hàng',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                          ),
                        ),
                        style: FilledButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          minimumSize: const Size(0, 44),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: () => _reportFailure(order),
                        icon: const Icon(
                          Icons.report_problem_outlined,
                          size: 18,
                        ),
                        label: const Text('Báo giao thất bại'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.danger,
                          side: BorderSide(
                            color: AppColors.danger.withValues(alpha: 0.45),
                          ),
                          minimumSize: const Size(0, 42),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                      ),
                    ),
                  ] else if (order.isDelivered)
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: () => showDeliveryProofDialog(context, order),
                        icon: const Icon(Icons.photo_outlined, size: 17),
                        label: const Text('Xem bằng chứng giao hàng'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.success,
                          side: const BorderSide(color: AppColors.success),
                          minimumSize: const Size(0, 42),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
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
}
