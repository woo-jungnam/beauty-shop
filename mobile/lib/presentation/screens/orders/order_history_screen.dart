import '../treatments/treatment_appointment_screen.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_response.dart';
import '../../../core/utils/cart_actions.dart';
import 'order_detail_screen.dart';
import 'order_lookup_screen.dart';
import 'package:flutter/material.dart';
import '../../widgets/catalog_state.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/core/utils/image_utils.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/checkout/vietqr_payment_screen.dart';

enum _SpaOrderFilter { all, spaVisit, spaPackage, uninvoiced }

class OrderHistoryScreen extends StatefulWidget {
  final int initialTabIndex;
  final bool unpaidOnly;
  final ApiClient? serviceOrdersApi;

  const OrderHistoryScreen({
    Key? key,
    this.initialTabIndex = 0,
    this.unpaidOnly = false,
    this.serviceOrdersApi,
  }) : super(key: key);

  @override
  State<OrderHistoryScreen> createState() => _OrderHistoryScreenState();
}

class _OrderHistoryScreenState extends State<OrderHistoryScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  int _lastFetchedTab = -1;
  final _search = TextEditingController();
  bool _searching = false;
  int _searchRequest = 0;
  final _reordering = <int>{};
  final List<String> _tabs = [
    'Tất cả',
    'Chờ xác nhận',
    'Đang xử lý / Vận chuyển',
    'Đã nhận hàng',
    'Đã hủy / Đổi trả',
    'Dịch vụ đã đặt',
  ];
  _SpaOrderFilter _spaFilter = _SpaOrderFilter.all;
  bool get _showInvoices => _spaFilter != _SpaOrderFilter.uninvoiced;
  bool get _showBookings =>
      !widget.unpaidOnly &&
      (_spaFilter == _SpaOrderFilter.all ||
          _spaFilter == _SpaOrderFilter.uninvoiced);
  List<Map<String, dynamic>> _serviceOrders = [];
  int _servicePage = 0;
  bool _serviceHasMore = false;
  bool _loadingServiceOrders = false;
  String? _serviceOrdersError;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(
      length: _tabs.length,
      vsync: this,
      initialIndex: widget.initialTabIndex,
    );
    _lastFetchedTab = widget.initialTabIndex;
    _tabController.addListener(() {
      if (!_tabController.indexIsChanging &&
          _tabController.index != _lastFetchedTab) {
        _lastFetchedTab = _tabController.index;
        _searchRequest++;
        setState(() => _searching = false);
        if (_tabController.index == 5) {
          _refreshSpaOrders();
        } else {
          final status = _tabIndexToStatus(_tabController.index);
          context.read<OrderProvider>().fetchOrders(status: status);
        }
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (widget.initialTabIndex == 5) {
        _refreshSpaOrders();
      } else {
        final initialStatus = _tabIndexToStatus(widget.initialTabIndex);
        context.read<OrderProvider>().fetchOrders(status: initialStatus);
      }
    });
  }

  Future<void> _refreshSpaOrders() async {
    await Future.wait([
      context.read<OrderProvider>().fetchOrders(),
      if (!widget.unpaidOnly) _fetchServiceOrders(),
    ]);
  }

  Future<void> _loadMoreSpaOrders(OrderProvider provider) async {
    await Future.wait([
      if (_showInvoices && provider.hasMore && provider.errorMessage == null)
        provider.loadMore(),
      if (_showBookings && _serviceHasMore && _serviceOrdersError == null)
        _fetchServiceOrders(append: true),
    ]);
  }

  bool _hasMoreSpaOrders(OrderProvider provider) =>
      (_showInvoices && provider.hasMore && provider.errorMessage == null) ||
      (_showBookings && _serviceHasMore && _serviceOrdersError == null);

  Future<void> _fetchServiceOrders({bool append = false}) async {
    if (_loadingServiceOrders || (append && !_serviceHasMore)) return;
    final page = append ? _servicePage + 1 : 0;
    setState(() {
      _loadingServiceOrders = true;
      _serviceOrdersError = null;
    });
    try {
      final response = await (widget.serviceOrdersApi ?? ApiClient())
          .get<PageResponse<Map<String, dynamic>>>(
            '/api/v1/appointments/my-service-orders',
            queryParameters: {'page': page, 'size': 10},
            fromJsonT: (json) => PageResponse.fromJson(
              json as Map<String, dynamic>,
              (item) => item as Map<String, dynamic>,
            ),
          );
      final data = response.data;
      if (!response.isSuccess || data == null)
        throw StateError('Missing service order data');
      if (mounted) {
        setState(() {
          _serviceOrders = append
              ? [..._serviceOrders, ...data.content]
              : data.content;
          _servicePage = page;
          _serviceHasMore = !data.last && page + 1 < data.totalPages;
        });
      }
    } catch (_) {
      if (mounted)
        setState(() => _serviceOrdersError = 'Không thể tải đơn dịch vụ.');
    } finally {
      if (mounted) setState(() => _loadingServiceOrders = false);
    }
  }

  String? _tabIndexToStatus(int tabIndex) => tabIndex == 1 ? 'PENDING' : null;

  @override
  void dispose() {
    _searchRequest++;
    _search.dispose();
    _tabController.dispose();
    super.dispose();
  }

  List<OrderResponse> _filterOrders(List<OrderResponse> orders, int tabIndex) {
    const statuses = {
      1: {'PENDING'},
      2: {'CONFIRMED', 'PROCESSING', 'SHIPPED'},
      3: {'DELIVERED', 'COMPLETED'},
      4: {
        'CANCELLED',
        'RETURNED',
        'DELIVERY_FAILED',
        'RETURNING_TO_SENDER',
        'RETURN_TO_SENDER',
      },
    };
    final accepted = statuses[tabIndex];
    return orders
        .where(
          (order) =>
              order.historyType == OrderHistoryType.product &&
              (!widget.unpaidOnly || order.canPayByQr) &&
              (accepted == null || accepted.contains(order.status)) &&
              order.matchesHistoryQuery(_search.text),
        )
        .toList();
  }

  Future<void> _searchAllPages(String query) async {
    if (query.trim().isEmpty || _searching) return;
    final provider = context.read<OrderProvider>();
    final isSpa = _lastFetchedTab == 5;
    if (provider.isLoading || (isSpa && _loadingServiceOrders)) return;
    final request = ++_searchRequest;
    setState(() => _searching = true);
    try {
      while (mounted && request == _searchRequest) {
        if (isSpa) {
          if (!_hasMoreSpaOrders(provider)) break;
          await _loadMoreSpaOrders(provider);
        } else {
          if (!provider.hasMore || provider.errorMessage != null) break;
          await provider.loadMore();
        }
      }
    } finally {
      if (mounted && request == _searchRequest)
        setState(() => _searching = false);
    }
  }

  Future<void> _reorder(OrderResponse order) async {
    setState(() => _reordering.add(order.id));
    try {
      await reorderProducts(context, order.items);
    } finally {
      if (mounted) setState(() => _reordering.remove(order.id));
    }
  }

  Color _getStatusColor(String status) {
    switch (status) {
      case 'DELIVERED':
        return AppColors.success;
      case 'SHIPPED':
      case 'PROCESSING':
        return const Color(0xFF0284C7);
      case 'PENDING':
        return Colors.orange;
      case 'CANCELLED':
        return AppColors.dealRed;
      default:
        return AppColors.getTextMain(context);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: Text(
          widget.unpaidOnly ? 'Đơn hàng chờ thanh toán' : 'Lịch Sử Đơn Hàng',
        ),
        backgroundColor: AppColors.primary,
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            tooltip: 'Tra cứu đơn hàng',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const OrderLookupScreen()),
            ),
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          indicatorColor: Colors.white,
          indicatorWeight: 3,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white70,
          labelStyle: const TextStyle(
            fontWeight: FontWeight.w800,
            fontSize: 13,
          ),
          tabs: _tabs.map((t) => Tab(text: t)).toList(),
        ),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
            child: TextField(
              controller: _search,
              textInputAction: TextInputAction.search,
              onChanged: (_) => setState(() {
                _searchRequest++;
                _searching = false;
              }),
              onSubmitted: _searchAllPages,
              decoration: InputDecoration(
                labelText: _lastFetchedTab == 5
                    ? 'Tìm mã đơn, gói, lịch hẹn hoặc tên dịch vụ'
                    : 'Tìm mã đơn, vận đơn, sản phẩm, SKU, SĐT',
                prefixIcon: IconButton(
                  icon: const Icon(Icons.search),
                  tooltip: 'Tìm trong tất cả các trang',
                  onPressed: _search.text.trim().isEmpty || _searching
                      ? null
                      : () {
                          FocusScope.of(context).unfocus();
                          _searchAllPages(_search.text);
                        },
                ),
                suffixIcon: _search.text.isEmpty
                    ? null
                    : IconButton(
                        tooltip: 'Xóa từ khóa',
                        icon: const Icon(Icons.clear),
                        onPressed: () => setState(() {
                          _search.clear();
                          _searchRequest++;
                          _searching = false;
                        }),
                      ),
              ),
            ),
          ),
          if (_searching) const LinearProgressIndicator(),
          Expanded(
            child: Consumer<OrderProvider>(
              builder: (context, orderProvider, _) {
                final allOrders = orderProvider.orders;

                return TabBarView(
                  controller: _tabController,
                  children: List.generate(_tabs.length, (tabIdx) {
                    if (tabIdx == 5) {
                      return _buildServiceOrdersTab(orderProvider);
                    }
                    if (orderProvider.errorMessage != null) {
                      return CatalogState(
                        message: orderProvider.errorMessage!,
                        onRetry: () => orderProvider.fetchOrders(
                          status: _tabIndexToStatus(tabIdx),
                        ),
                      );
                    }
                    if (orderProvider.isLoading && allOrders.isEmpty) {
                      return const Center(
                        child: CircularProgressIndicator(
                          color: AppColors.primary,
                        ),
                      );
                    }
                    final filtered = _filterOrders(allOrders, tabIdx);

                    if (filtered.isEmpty) {
                      return Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.receipt_long_outlined,
                              size: 64,
                              color: AppColors.textLight.withOpacity(0.5),
                            ),
                            const SizedBox(height: 12),
                            if (orderProvider.hasMore)
                              TextButton(
                                onPressed: orderProvider.isLoading
                                    ? null
                                    : orderProvider.loadMore,
                                child: const Text('Tải các đơn hàng tiếp theo'),
                              ),
                            Text(
                              'Chưa có đơn hàng nào trong mục này',
                              style: AppTypography.caption.copyWith(
                                fontSize: 13,
                              ),
                            ),
                          ],
                        ),
                      );
                    }

                    return RefreshIndicator(
                      color: AppColors.primary,
                      onRefresh: () => orderProvider.fetchOrders(
                        status: _tabIndexToStatus(tabIdx),
                      ),
                      child: ListView.separated(
                        padding: const EdgeInsets.all(12),
                        itemCount:
                            filtered.length + (orderProvider.hasMore ? 1 : 0),
                        separatorBuilder: (_, __) => const SizedBox(height: 12),
                        itemBuilder: (context, idx) {
                          if (idx == filtered.length)
                            return TextButton(
                              onPressed: orderProvider.isLoading
                                  ? null
                                  : orderProvider.loadMore,
                              child: const Text('Tải thêm đơn hàng'),
                            );
                          final order = filtered[idx];
                          return _buildOrderCard(context, order, orderProvider);
                        },
                      ),
                    );
                  }),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOrderCard(
    BuildContext context,
    OrderResponse order,
    OrderProvider provider,
  ) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.borderSubtle),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                order.orderCode,
                style: const TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 14,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: _getStatusColor(order.status).withOpacity(0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  order.statusLabel,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    color: _getStatusColor(order.status),
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 18, color: AppColors.borderSubtle),

          if (order.historyType == OrderHistoryType.spaVisit)
            Text(
              order.spaServiceNames.isEmpty
                  ? 'Hóa đơn buổi Spa tại quầy'
                  : order.spaServiceNames.join(', '),
            ),
          if (order.historyType == OrderHistoryType.spaPackage)
            Text('Gói liệu trình #${order.servicePackageId}'),
          if (order.appointmentId != null)
            Text('Mã buổi hẹn: SPA-APT-#${order.appointmentId}'),
          if (order.carrierName?.trim().isNotEmpty == true)
            Text('Vận chuyển: ${order.carrierName}'),
          if (order.trackingCode?.trim().isNotEmpty == true)
            Text('Mã vận đơn: ${order.trackingCode}'),
          if (order.items.isNotEmpty)
            ...order.items.map(
              (item) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: Container(
                        width: 34,
                        height: 34,
                        color: const Color(0xFFF3F4F6),
                        child: () {
                          final imgUrl = ImageUtils.resolveUrl(
                            item.thumbnailUrl,
                          );
                          return imgUrl != null && imgUrl.isNotEmpty
                              ? Image.network(
                                  imgUrl,
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => const Icon(
                                    Icons.image_outlined,
                                    size: 14,
                                    color: Color(0xFF9CA3AF),
                                  ),
                                )
                              : const Icon(
                                  Icons.image_outlined,
                                  size: 14,
                                  color: Color(0xFF9CA3AF),
                                );
                        }(),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            item.productName,
                            style: AppTypography.bodySmall.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          Text(
                            '${item.variantName} × ${item.quantity}',
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.textMuted,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Text(
                      AppFormatters.formatCurrency(
                        item.effectiveUnitPrice * item.quantity,
                      ),
                      style: const TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
            )
          else
            Text('Đơn hàng sản phẩm', style: AppTypography.bodySmall),

          const Divider(height: 18, color: AppColors.borderSubtle),

          OutlinedButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => OrderDetailScreen(orderId: order.id),
              ),
            ),
            child: const Text('Chi tiết đơn hàng'),
          ),
          if (order.items.any((item) => (item.variantId ?? 0) > 0))
            OutlinedButton(
              onPressed: _reordering.contains(order.id)
                  ? null
                  : () => _reorder(order),
              child: const Text('Mua lại'),
            ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Tổng thanh toán:',
                    style: TextStyle(fontSize: 11, color: AppColors.textMuted),
                  ),
                  Text(
                    AppFormatters.formatCurrency(order.totalAmount),
                    style: AppTypography.priceLarge.copyWith(fontSize: 16),
                  ),
                ],
              ),
              Row(
                children: [
                  if (order.canPayByQr)
                    OutlinedButton(
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => VietQrPaymentScreen(order: order),
                          ),
                        );
                      },
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.primary,
                        side: const BorderSide(color: AppColors.primary),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 6,
                        ),
                      ),
                      child: const Text(
                        'Xem mã QR',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  if (order.status == 'PENDING') ...[
                    const SizedBox(width: 8),
                    TextButton(
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('Hủy đơn hàng?'),
                            content: const Text(
                              'Bạn có chắc chắn muốn hủy đơn hàng này không?',
                            ),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, false),
                                child: const Text('Không'),
                              ),
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, true),
                                child: const Text(
                                  'Hủy đơn',
                                  style: TextStyle(color: AppColors.dealRed),
                                ),
                              ),
                            ],
                          ),
                        );
                        if (confirm == true) {
                          final ok = await provider.cancelOrder(order.id);
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  ok
                                      ? 'Đã hủy đơn hàng thành công'
                                      : provider.actionError ??
                                            'Không thể hủy đơn hàng',
                                ),
                                backgroundColor: ok
                                    ? AppColors.primary
                                    : AppColors.dealRed,
                              ),
                            );
                          }
                        }
                      },
                      child: const Text(
                        'Hủy đơn',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppColors.dealRed,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildServiceOrdersTab(OrderProvider provider) {
    const filters = {
      _SpaOrderFilter.all: 'Tất cả dịch vụ',
      _SpaOrderFilter.spaVisit: 'Hóa đơn tại quầy',
      _SpaOrderFilter.spaPackage: 'Gói liệu trình & Lộ trình',
      _SpaOrderFilter.uninvoiced: 'Chưa lập hóa đơn',
    };
    final query = _search.text.trim().toLowerCase();
    final invoices = _showInvoices
        ? provider.orders.where((order) {
            final type = order.historyType;
            if (type == OrderHistoryType.product ||
                (widget.unpaidOnly && !order.canPayByQr)) {
              return false;
            }
            if (_spaFilter == _SpaOrderFilter.spaVisit &&
                type != OrderHistoryType.spaVisit)
              return false;
            if (_spaFilter == _SpaOrderFilter.spaPackage &&
                type != OrderHistoryType.spaPackage)
              return false;
            return order.matchesHistoryQuery(query);
          }).toList()
        : <OrderResponse>[];
    final bookings = _showBookings
        ? _serviceOrders
              .where(
                (row) =>
                    'LH-${row['appointmentId'] ?? ''} ${(row['services'] as List? ?? []).join(' ')}'
                        .toLowerCase()
                        .contains(query),
              )
              .toList()
        : <Map<String, dynamic>>[];
    final loading =
        (_showInvoices && provider.isLoading) ||
        (_showBookings && _loadingServiceOrders);
    final hasMore = _hasMoreSpaOrders(provider);
    final error =
        (_showInvoices ? provider.errorMessage : null) ??
        (_showBookings ? _serviceOrdersError : null);
    return RefreshIndicator(
      onRefresh: _refreshSpaOrders,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(12),
        children: [
          Wrap(
            spacing: 8,
            children: [
              for (final filter in filters.entries.where(
                (entry) =>
                    !widget.unpaidOnly ||
                    entry.key != _SpaOrderFilter.uninvoiced,
              ))
                ChoiceChip(
                  label: Text(filter.value),
                  selected: _spaFilter == filter.key,
                  onSelected: (_) => setState(() {
                    _spaFilter = filter.key;
                    _searchRequest++;
                    _searching = false;
                  }),
                ),
            ],
          ),
          if (loading) const LinearProgressIndicator(),
          if (error != null)
            ListTile(
              title: Text(error),
              trailing: TextButton(
                onPressed: _refreshSpaOrders,
                child: const Text('Thử lại'),
              ),
            ),
          if (!loading && error == null && invoices.isEmpty && bookings.isEmpty)
            const ListTile(title: Text('Không có đơn dịch vụ Spa phù hợp')),
          for (final order in invoices)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: _buildOrderCard(context, order, provider),
            ),
          for (final row in bookings)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: _buildUninvoicedCard(row),
            ),
          if (hasMore)
            TextButton(
              onPressed: loading ? null : () => _loadMoreSpaOrders(provider),
              child: const Text('Tải thêm đơn dịch vụ'),
            ),
        ],
      ),
    );
  }

  Widget _buildUninvoicedCard(Map<String, dynamic> row) {
    final aptId = (row['appointmentId'] as num?)?.toInt() ?? 0;
    final services = (row['services'] as List? ?? []).join(', ');
    final dateStr = row['appointmentDate']?.toString() ?? '';
    final timeStr = row['startTime']?.toString() ?? '';
    final est = (row['estimatedAmount'] as num?)?.toDouble() ?? 0;
    final collected = (row['collectedAmount'] as num?)?.toDouble() ?? 0;
    final st = row['status']?.toString() ?? '';
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.borderSubtle),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Mã đặt dịch vụ LH-$aptId',
                style: const TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 13.5,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFFDF2F8),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  st,
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 11,
                    color: Color(0xFF9D174D),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            services.isNotEmpty ? services : 'Dịch vụ Spa',
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
          ),
          const SizedBox(height: 4),
          Text(
            '$dateStr · $timeStr',
            style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
          ),
          const Divider(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Giá dự kiến: ${AppFormatters.formatCurrency(est)}',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (collected > 0)
                    Text(
                      'Đã thu: ${AppFormatters.formatCurrency(collected)}',
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: Color(0xFF059669),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                ],
              ),
              OutlinedButton(
                style: OutlinedButton.styleFrom(
                  visualDensity: VisualDensity.compact,
                ),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) =>
                        TreatmentAppointmentScreen(id: aptId, operate: false),
                  ),
                ),
                child: const Text('Chi tiết'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
