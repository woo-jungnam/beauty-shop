import '../treatments/treatment_appointment_screen.dart';
import '../../../core/network/api_client.dart';
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

class OrderHistoryScreen extends StatefulWidget {
  final int initialTabIndex;
  final bool unpaidOnly;

  const OrderHistoryScreen({
    Key? key,
    this.initialTabIndex = 0,
    this.unpaidOnly = false,
  }) : super(key: key);

  @override
  State<OrderHistoryScreen> createState() => _OrderHistoryScreenState();
}

class _OrderHistoryScreenState extends State<OrderHistoryScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final List<String> _tabs = [
    'Tất cả',
    'Chờ xử lý',
    'Đang giao',
    'Đã giao',
    'Đã hủy',
    'Dịch vụ đã đặt',
  ];
  List<dynamic> _serviceOrders = [];
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
    _tabController.addListener(() {
      if (_tabController.indexIsChanging) {
        if (_tabController.index == 5) {
          _fetchServiceOrders();
        } else {
          final status = _tabIndexToStatus(_tabController.index);
          context.read<OrderProvider>().fetchOrders(status: status);
        }
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (widget.initialTabIndex == 5) {
        _fetchServiceOrders();
      } else {
        final initialStatus = _tabIndexToStatus(widget.initialTabIndex);
        context.read<OrderProvider>().fetchOrders(status: initialStatus);
      }
    });
  }

  Future<void> _fetchServiceOrders() async {
    setState(() {
      _loadingServiceOrders = true;
      _serviceOrdersError = null;
    });
    try {
      final res = await ApiClient().get<dynamic>('/api/v1/appointments/my-service-orders');
      if (mounted) {
        final data = res.data;
        final list = data is Map ? data['content'] as List? ?? [] : (data is List ? data : []);
        setState(() => _serviceOrders = list);
      }
    } catch (e) {
      if (mounted) setState(() => _serviceOrdersError = '$e');
    } finally {
      if (mounted) setState(() => _loadingServiceOrders = false);
    }
  }

  String? _tabIndexToStatus(int tabIndex) {
    switch (tabIndex) {
      case 2:
        return 'SHIPPED';
      case 3:
        return 'DELIVERED';
      case 4:
        return 'CANCELLED';
      default:
        return null;
    }
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  List<OrderResponse> _filterOrders(List<OrderResponse> orders, int tabIndex) {
    if (widget.unpaidOnly) {
      orders = orders
          .where(
            (order) =>
                order.paymentMethod == 'BANK' &&
                order.paymentStatus == 'PENDING' &&
                order.status != 'CANCELLED',
          )
          .toList();
    }
    switch (tabIndex) {
      case 1:
        return orders
            .where(
              (o) =>
                  o.status == 'PENDING' ||
                  o.status == 'CONFIRMED' ||
                  o.status == 'PROCESSING',
            )
            .toList();
      case 2:
        return orders.where((o) => o.status == 'SHIPPED').toList();
      case 3:
        return orders.where((o) => o.status == 'DELIVERED').toList();
      case 4:
        return orders.where((o) => o.status == 'CANCELLED').toList();
      default:
        return orders;
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
      body: Consumer<OrderProvider>(
        builder: (context, orderProvider, _) {
          if (orderProvider.errorMessage != null)
            return CatalogState(
              message: orderProvider.errorMessage!,
              onRetry: orderProvider.fetchOrders,
            );
          if (orderProvider.isLoading && orderProvider.orders.isEmpty) {
            return const Center(
              child: CircularProgressIndicator(color: AppColors.primary),
            );
          }

          final allOrders = orderProvider.orders;

          return TabBarView(
            controller: _tabController,
            children: List.generate(_tabs.length, (tabIdx) {
              if (tabIdx == 5) {
                return _buildServiceOrdersTab();
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
                        style: AppTypography.caption.copyWith(fontSize: 13),
                      ),
                    ],
                  ),
                );
              }

              return RefreshIndicator(
                color: AppColors.primary,
                onRefresh: () => orderProvider.fetchOrders(),
                child: ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: filtered.length + (orderProvider.hasMore ? 1 : 0),
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
                          final imgUrl = ImageUtils.resolveUrl(item.thumbnailUrl);
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
                            style: AppTypography.bodySmall.copyWith(fontWeight: FontWeight.w600),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          Text(
                            '${item.variantName} × ${item.quantity}',
                            style: const TextStyle(fontSize: 11, color: AppColors.textMuted),
                          ),
                        ],
                      ),
                    ),
                    Text(
                      AppFormatters.formatCurrency(
                        item.price * item.quantity,
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
            Text(
              'Đơn hàng sản phẩm',
              style: AppTypography.bodySmall,
            ),

          const Divider(height: 18, color: AppColors.borderSubtle),

          OutlinedButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => OrderDetailScreen(orderId: order.id),
              ),
            ),
            child: const Text('Chi tiết, mua lại & đổi trả'),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Tổng thanh toán:',
                    style: TextStyle(
                      fontSize: 11,
                      color: AppColors.textMuted,
                    ),
                  ),
                  Text(
                    AppFormatters.formatCurrency(order.totalAmount),
                    style: AppTypography.priceLarge.copyWith(fontSize: 16),
                  ),
                ],
              ),
              Row(
                children: [
                  if (order.paymentMethod == 'BANK' &&
                      !order.isPaid &&
                      order.status != 'CANCELLED')
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
                  if (order.status == 'PENDING' ||
                      order.status == 'CONFIRMED') ...[
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
                          await provider.cancelOrder(order.id);
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

  Widget _buildServiceOrdersTab() {
    if (_loadingServiceOrders) {
      return const Center(child: CircularProgressIndicator(color: AppColors.primary));
    }
    if (_serviceOrdersError != null) {
      return CatalogState(message: _serviceOrdersError!, onRetry: _fetchServiceOrders);
    }
    if (_serviceOrders.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.spa_outlined, size: 64, color: AppColors.textLight.withOpacity(0.5)),
            const SizedBox(height: 12),
            Text(
              'Chưa có dịch vụ đơn chờ lập hóa đơn',
              style: AppTypography.caption.copyWith(fontSize: 13),
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      color: AppColors.primary,
      onRefresh: _fetchServiceOrders,
      child: ListView.separated(
        padding: const EdgeInsets.all(12),
        itemCount: _serviceOrders.length,
        separatorBuilder: (_, __) => const SizedBox(height: 12),
        itemBuilder: (context, idx) {
          final row = _serviceOrders[idx];
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
                    Text('Mã đặt dịch vụ LH-$aptId', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13.5)),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFDF2F8),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(st, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 11, color: Color(0xFF9D174D))),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(services.isNotEmpty ? services : 'Dịch vụ Spa', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                const SizedBox(height: 4),
                Text('$dateStr · $timeStr', style: const TextStyle(color: AppColors.textMuted, fontSize: 12)),
                const Divider(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Giá dự kiến: ${AppFormatters.formatCurrency(est)}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                        if (collected > 0)
                          Text('Đã thu: ${AppFormatters.formatCurrency(collected)}', style: const TextStyle(fontSize: 11.5, color: Color(0xFF059669), fontWeight: FontWeight.w600)),
                      ],
                    ),
                    OutlinedButton(
                      style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                      onPressed: () => Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => TreatmentAppointmentScreen(id: aptId, operate: false)),
                      ),
                      child: const Text('Chi tiết'),
                    ),
                  ],
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
