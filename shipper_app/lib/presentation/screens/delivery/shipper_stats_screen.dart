import 'dart:math';

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
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
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Thống kê giao hàng',
              style: Theme.of(
                context,
              ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            DeliveryDates(
              range: _range,
              onChange: (range) {
                _range = range;
                _refresh();
              },
            ),
            const SizedBox(height: 12),
            if (snapshot.connectionState != ConnectionState.done)
              const Center(child: CircularProgressIndicator())
            else if (snapshot.hasError) ...[
              const Text('Không tải được thống kê giao hàng.'),
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
    final earnings = data['estimatedEarnings'];
    return [
      Card(
        color: AppColors.primaryDark,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Thu nhập ước tính trong kỳ',
                style: TextStyle(color: AppColors.primarySurface),
              ),
              const SizedBox(height: 6),
              Text(
                earnings == null ? 'Chưa cấu hình' : currency.format(earnings),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                "${data['deliveredOrders']} / ${data['totalOrders']} đơn đã giao trong kỳ",
                style: const TextStyle(color: AppColors.primarySurface),
              ),
            ],
          ),
        ),
      ),
      Card(
        child: Column(
          children: [
            for (final (icon, label, key) in const [
              (
                Icons.payments_outlined,
                'COD tài xế đã thu',
                'totalCodCollected',
              ),
              (
                Icons.receipt_long_outlined,
                'Giá trị đơn đã giao',
                'totalRevenue',
              ),
              (
                Icons.local_shipping_outlined,
                'Phí giao hàng trên đơn đã giao',
                'totalDeliveryFees',
              ),
            ])
              ListTile(
                leading: Icon(icon, color: AppColors.primary),
                title: Text(label),
                subtitle: Text(
                  currency.format(data[key]),
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
              ),
          ],
        ),
      ),
      Card(
        child: Column(
          children: [
            for (final (label, key) in const [
              ('Đã giao hôm nay', 'todayOrders'),
              ('Đã giao tuần này', 'weekOrders'),
              ('Đã giao tháng này', 'monthOrders'),
            ])
              ListTile(
                title: Text(label),
                trailing: Text(
                  '${data[key]}',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
              ),
          ],
        ),
      ),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Đơn đã giao theo ngày',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 12),
              _chart(Map<String, num>.from(data['ordersByDay'] as Map)),
            ],
          ),
        ),
      ),
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
                borderData: FlBorderData(show: false),
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
