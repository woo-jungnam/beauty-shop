import 'dart:math';

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../providers/shipper_provider.dart';
import 'delivery_dates.dart';

class ShipperStatsScreen extends StatefulWidget {
  const ShipperStatsScreen({super.key});

  @override
  State<ShipperStatsScreen> createState() => _ShipperStatsScreenState();
}

class _ShipperStatsScreenState extends State<ShipperStatsScreen> {
  DateTimeRange _range = DeliveryDates.month();
  late Future<Map<String, dynamic>> _stats;

  @override
  void initState() {
    super.initState();
    _stats = _fetch();
  }

  Future<Map<String, dynamic>> _fetch() =>
      context.read<ShipperProvider>().fetchStats(
        DeliveryDates.apiDate(_range.start),
        DeliveryDates.apiDate(_range.end),
      );

  Future<void> _refresh() async {
    setState(() => _stats = _fetch());
    try {
      await _stats;
    } catch (_) {
      // The FutureBuilder displays the request error.
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
      future: _stats,
      builder: (context, snapshot) => RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(12),
          children: [
            DeliveryDates(
              range: _range,
              onChange: (range) {
                _range = range;
                _refresh();
              },
            ),
            if (snapshot.connectionState != ConnectionState.done)
              const Center(child: CircularProgressIndicator())
            else if (snapshot.hasError) ...[
              Text(snapshot.error.toString()),
              TextButton(onPressed: _refresh, child: const Text('Thử lại')),
            ] else
              ..._report(snapshot.requireData),
          ],
        ),
      ),
    );
  }

  List<Widget> _report(Map<String, dynamic> data) {
    final currency = NumberFormat.currency(
      locale: 'vi_VN',
      symbol: '₫',
      decimalDigits: 0,
    );
    return [
      Card(
        child: Column(
          children: [
            ListTile(
              title: const Text('Đã giao hôm nay'),
              trailing: Text('${data['todayOrders']}'),
            ),
            ListTile(
              title: const Text('Đã giao tuần này'),
              trailing: Text('${data['weekOrders']}'),
            ),
            ListTile(
              title: const Text('Đã giao tháng này'),
              trailing: Text('${data['monthOrders']}'),
            ),
          ],
        ),
      ),
      Card(
        child: Column(
          children: [
            ListTile(
              title: const Text('Tổng số đơn trong kỳ'),
              trailing: Text('${data['totalOrders']}'),
            ),
            ListTile(
              title: const Text('Số đơn đã giao trong kỳ'),
              trailing: Text('${data['deliveredOrders']}'),
            ),
            ListTile(
              title: const Text('COD tài xế đã thu'),
              subtitle: Text(currency.format(data['totalCodCollected'])),
            ),
            ListTile(
              title: const Text('Giá trị đơn đã giao'),
              subtitle: Text(currency.format(data['totalRevenue'])),
            ),
            ListTile(
              title: const Text('Phí giao hàng trên đơn đã giao'),
              subtitle: Text(currency.format(data['totalDeliveryFees'])),
            ),
            ListTile(
              title: const Text('Thu nhập ước tính trong kỳ'),
              subtitle: Text(
                data['estimatedEarnings'] == null
                    ? 'Chưa cấu hình'
                    : currency.format(data['estimatedEarnings']),
              ),
            ),
          ],
        ),
      ),
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Text(
          'Đơn đã giao theo ngày',
          style: Theme.of(context).textTheme.titleMedium,
        ),
      ),
      _chart(Map<String, num>.from(data['ordersByDay'] as Map)),
    ];
  }

  Widget _chart(Map<String, num> days) {
    if (days.isEmpty) return const Text('Không có dữ liệu');
    final entries = (days.entries.toList()
      ..sort((a, b) => a.key.compareTo(b.key)));
    final colors = Theme.of(context).colorScheme;
    final dateFormat = DateFormat('dd/MM');
    final maxCount = days.values.reduce(max);
    return LayoutBuilder(
      builder: (context, constraints) {
        return SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: SizedBox(
            height: 230,
            width: max(constraints.maxWidth, entries.length * 48 + 36),
            child: BarChart(
              BarChartData(
                maxY: max(1, maxCount).toDouble(),
                titlesData: FlTitlesData(
                  topTitles: const AxisTitles(),
                  rightTitles: const AxisTitles(),
                  leftTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 36,
                      interval: max(1, (maxCount / 4).ceil()).toDouble(),
                    ),
                  ),
                  bottomTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 30,
                      getTitlesWidget: (value, meta) => Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: Text(
                          dateFormat.format(
                            DateTime.parse(entries[value.toInt()].key),
                          ),
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                      ),
                    ),
                  ),
                ),
                barGroups: [
                  for (final (index, day) in entries.indexed)
                    BarChartGroupData(
                      x: index,
                      barRods: [
                        BarChartRodData(
                          toY: day.value.toDouble(),
                          color: colors.primary,
                          width: 14,
                        ),
                      ],
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
