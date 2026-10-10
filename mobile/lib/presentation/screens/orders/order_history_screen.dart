import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/checkout/vietqr_payment_screen.dart';

class OrderHistoryScreen extends StatefulWidget {
  final int initialTabIndex;

  const OrderHistoryScreen({Key? key, this.initialTabIndex = 0}) : super(key: key);

  @override
  State<OrderHistoryScreen> createState() => _OrderHistoryScreenState();
}

class _OrderHistoryScreenState extends State<OrderHistoryScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final List<String> _tabs = [
    'Tất cả',
    'Chờ xử lý',
    'Đang giao',
    'Đã giao',
    'Đã hủy',
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this, initialIndex: widget.initialTabIndex);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<OrderProvider>().fetchOrders();
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  List<OrderResponse> _filterOrders(List<OrderResponse> orders, int tabIndex) {
    switch (tabIndex) {
      case 1:
        return orders.where((o) => o.status == 'PENDING' || o.status == 'CONFIRMED' || o.status == 'PROCESSING').toList();
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
        return HasakiColors.success;
      case 'SHIPPED':
      case 'PROCESSING':
        return const Color(0xFF0284C7);
      case 'PENDING':
        return Colors.orange;
      case 'CANCELLED':
        return HasakiColors.dealRed;
      default:
        return HasakiColors.textMain;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.canvas,
      appBar: AppBar(
        title: const Text('Lịch Sử Đơn Hàng'),
        backgroundColor: HasakiColors.primary,
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          indicatorColor: Colors.white,
          indicatorWeight: 3,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white70,
          labelStyle: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13),
          tabs: _tabs.map((t) => Tab(text: t)).toList(),
        ),
      ),
      body: Consumer<OrderProvider>(
        builder: (context, orderProvider, _) {
          if (orderProvider.isLoading && orderProvider.orders.isEmpty) {
            return const Center(child: CircularProgressIndicator(color: HasakiColors.primary));
          }

          final allOrders = orderProvider.orders;

          return TabBarView(
            controller: _tabController,
            children: List.generate(_tabs.length, (tabIdx) {
              final filtered = _filterOrders(allOrders, tabIdx);

              if (filtered.isEmpty) {
                return Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.receipt_long_outlined, size: 64, color: HasakiColors.textLight.withOpacity(0.5)),
                      const SizedBox(height: 12),
                      Text(
                        'Chưa có đơn hàng nào trong mục này',
                        style: HasakiTypography.caption.copyWith(fontSize: 13),
                      ),
                    ],
                  ),
                );
              }

              return RefreshIndicator(
                color: HasakiColors.primary,
                onRefresh: () => orderProvider.fetchOrders(),
                child: ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: filtered.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, idx) {
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

  Widget _buildOrderCard(BuildContext context, OrderResponse order, OrderProvider provider) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: HasakiColors.borderSubtle),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Order Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                order.orderCode,
                style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14),
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
          const Divider(height: 18, color: HasakiColors.borderSubtle),

          // Items summary
          if (order.items.isNotEmpty)
            ...order.items.map((item) => Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Text(
                          '${item.productName} (x${item.quantity})',
                          style: HasakiTypography.bodySmall,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Text(
                        HasakiFormatters.formatCurrency(item.price * item.quantity),
                        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
                      ),
                    ],
                  ),
                ))
          else
            Text('Đơn hàng sản phẩm BeautyShop', style: HasakiTypography.bodySmall),

          const Divider(height: 18, color: HasakiColors.borderSubtle),

          // Total and actions
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Tổng thanh toán:', style: TextStyle(fontSize: 11, color: HasakiColors.textMuted)),
                  Text(
                    HasakiFormatters.formatCurrency(order.totalAmount),
                    style: HasakiTypography.priceLarge.copyWith(fontSize: 16),
                  ),
                ],
              ),
              Row(
                children: [
                  if (order.paymentMethod == 'BANK' && !order.isPaid && order.status != 'CANCELLED')
                    OutlinedButton(
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => VietQrPaymentScreen(order: order)),
                        );
                      },
                      style: OutlinedButton.styleFrom(
                        foregroundColor: HasakiColors.primary,
                        side: const BorderSide(color: HasakiColors.primary),
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      ),
                      child: const Text('Xem mã QR', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                    ),
                  if (order.status == 'PENDING' || order.status == 'CONFIRMED') ...[
                    const SizedBox(width: 8),
                    TextButton(
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('Hủy đơn hàng?'),
                            content: const Text('Bạn có chắc chắn muốn hủy đơn hàng này không?'),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Không')),
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, true),
                                child: const Text('Hủy đơn', style: TextStyle(color: HasakiColors.dealRed)),
                              ),
                            ],
                          ),
                        );
                        if (confirm == true) {
                          await provider.cancelOrder(order.id);
                        }
                      },
                      child: const Text('Hủy đơn', style: TextStyle(fontSize: 12, color: HasakiColors.dealRed)),
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
}
