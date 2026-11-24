import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../data/models/shipper_order_model.dart';
import '../../../providers/shipper_provider.dart';
import 'delivery_dates.dart';
import 'shipper_order_detail_screen.dart';

class DeliveryHistoryScreen extends StatefulWidget {
  const DeliveryHistoryScreen({super.key});

  @override
  State<DeliveryHistoryScreen> createState() => _DeliveryHistoryScreenState();
}

class _DeliveryHistoryScreenState extends State<DeliveryHistoryScreen> {
  DateTimeRange _range = DeliveryDates.month();
  bool _deliveredOnly = true;
  final List<ShipperOrderModel> _orders = [];
  int _page = 0;
  int _request = 0;
  bool _last = false;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load(reset: true);
  }

  Future<void> _load({bool reset = false}) async {
    final request = ++_request;
    setState(() {
      if (reset) {
        _orders.clear();
        _page = 0;
        _last = false;
      }
      _loading = true;
      _error = null;
    });
    try {
      final data = await context.read<ShipperProvider>().fetchHistory(
        DeliveryDates.apiDate(_range.start),
        DeliveryDates.apiDate(_range.end),
        _page,
        _deliveredOnly,
      );
      if (!mounted || request != _request) return;
      setState(() {
        _orders.addAll(
          (data['content'] as List).cast<Map<String, dynamic>>().map(
            ShipperOrderModel.fromJson,
          ),
        );
        _last = data['last'] == true;
        _page++;
        _loading = false;
      });
    } catch (error) {
      if (!mounted || request != _request) return;
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final groups = <String, List<ShipperOrderModel>>{};
    final format = DateFormat('dd/MM/yyyy');
    final currency = NumberFormat.currency(
      locale: 'vi_VN',
      symbol: '₫',
      decimalDigits: 0,
    );
    for (final order in _orders) {
      final timestamp =
          order.deliveredAt ?? order.assignedAt ?? order.createdAt;
      final date = timestamp == null
          ? 'Chưa rõ ngày'
          : format.format(vietnamTime(timestamp));
      groups.putIfAbsent(date, () => []).add(order);
    }
    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Lịch sử giao hàng',
            style: Theme.of(
              context,
            ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          DeliveryDates(
            range: _range,
            onChange: (range) {
              _range = range;
              _load(reset: true);
            },
          ),
          SegmentedButton<bool>(
            segments: const [
              ButtonSegment(value: true, label: Text('Đã giao')),
              ButtonSegment(value: false, label: Text('Tất cả')),
            ],
            selected: {_deliveredOnly},
            onSelectionChanged: (value) {
              _deliveredOnly = value.single;
              _load(reset: true);
            },
          ),
          for (final group in groups.entries) ...[
            ListTile(
              title: Text(
                group.key,
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              trailing: Text(
                '${group.value.length} mục${!_last && group.key == groups.keys.last ? ' đã tải' : ''}',
              ),
            ),
            for (final order in group.value)
              Card(
                child: ListTile(
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => ShipperOrderDetailScreen(order: order),
                    ),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  title: Text(
                    '#${order.orderNumber}',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        !order.hasOrderDetails && order.notes != null
                            ? order.notes!
                            : order.customerName,
                      ),
                      if (order.codCollected > 0)
                        Text(
                          'COD chờ bàn giao: ${currency.format(order.codCollected - order.codRemitted)}',
                        ),
                      if (order.items.isNotEmpty ||
                          order.itemsSummary.isNotEmpty)
                        OrderItemsSection(order: order, preview: true),
                      const SizedBox(height: 6),
                      DecoratedBox(
                        decoration: BoxDecoration(
                          color: order.isDelivered
                              ? AppColors.successLight
                              : AppColors.primarySurface,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          child: Text(
                            switch (order.status) {
                              'DELIVERED' => 'Đã giao',
                              'SHIPPED' => 'Đang giao',
                              'DELIVERY_FAILED' => 'Giao thất bại',
                              'CANCELLED' => 'Đã hủy',
                              'RETURNING_TO_SENDER' => 'Đang hoàn về kho',
                              'RETURN_TO_SENDER' => 'Đã hoàn về kho',
                              'RETURNED' => 'Đã trả hàng',
                              'REFUNDED' => 'Đã hoàn tiền',
                              _ => order.status,
                            },
                            style: TextStyle(
                              color: order.isDelivered
                                  ? AppColors.success
                                  : AppColors.primaryDark,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
          if (_loading) const Center(child: CircularProgressIndicator()),
          if (_error != null) ...[
            Text(_error!),
            TextButton(onPressed: _load, child: const Text('Thử lại')),
          ] else if (!_loading && !_last)
            TextButton(onPressed: _load, child: const Text('Tải thêm')),
          if (!_loading && _error == null && _orders.isEmpty)
            const Center(child: Text('Không có đơn trong khoảng ngày này')),
        ],
      ),
    );
  }
}
