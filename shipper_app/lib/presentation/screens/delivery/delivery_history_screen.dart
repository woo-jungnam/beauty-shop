import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../data/models/shipper_order_model.dart';
import '../../../providers/shipper_provider.dart';
import 'delivery_dates.dart';

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
    for (final order in _orders) {
      final timestamp =
          order.deliveredAt ?? order.assignedAt ?? order.createdAt;
      final date = timestamp == null
          ? 'Chưa rõ ngày'
          : format.format(vietnamTime(timestamp));
      groups.putIfAbsent(date, () => []).add(order);
    }
    return Column(
      children: [
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
        Expanded(
          child: RefreshIndicator(
            onRefresh: () => _load(reset: true),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(12),
              children: [
                for (final group in groups.entries) ...[
                  ListTile(
                    title: Text(group.key),
                    trailing: Text(
                      '${group.value.length} đơn${!_last && group.key == groups.keys.last ? ' đã tải' : ''}',
                    ),
                  ),
                  for (final order in group.value)
                    Card(
                      child: ListTile(
                        title: Text('#${order.orderNumber}'),
                        subtitle: Text(order.customerName),
                        trailing: Text(switch (order.status) {
                          'DELIVERED' => 'Đã giao',
                          'SHIPPED' => 'Đang giao',
                          'DELIVERY_FAILED' => 'Giao thất bại',
                          'CANCELLED' => 'Đã hủy',
                          'RETURNED' => 'Đã trả hàng',
                          'REFUNDED' => 'Đã hoàn tiền',
                          _ => order.status,
                        }),
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
                  const Center(
                    child: Text('Không có đơn trong khoảng ngày này'),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
