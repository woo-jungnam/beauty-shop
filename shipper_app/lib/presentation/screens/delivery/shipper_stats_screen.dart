import 'dart:math';

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';
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
    return FutureBuilder<Map<String, dynamic>>(
      future: _stats,
      builder: (context, snapshot) => RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: ShipperLayout.pagePadding,
          children: [
            Text(
              'Thống kê giao hàng',
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.bold,
                color: AppColors.textMain,
              ),
            ),
            const SizedBox(height: 10),
            DeliveryDates(
              range: _range,
              onChange: (range) {
                _range = range;
                _refresh();
              },
            ),
            const SizedBox(height: 14),
            if (snapshot.connectionState != ConnectionState.done)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 40),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (snapshot.hasError)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    children: [
                      const Icon(
                        Icons.cloud_off_outlined,
                        size: 36,
                        color: AppColors.textMuted,
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Không tải được thống kê giao hàng.',
                        style: TextStyle(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 10),
                      FilledButton(
                        onPressed: _refresh,
                        child: const Text('Thử lại'),
                      ),
                    ],
                  ),
                ),
              )
            else
              ..._report(snapshot.requireData),
          ],
        ),
      ),
    );
  }

  List<Widget> _report(Map<String, dynamic> data) {
    final earnings = data['estimatedEarnings'] as num?;
    final deliveredOrders = (data['deliveredOrders'] as num?)?.toInt() ?? 0;
    final totalOrders = (data['totalOrders'] as num?)?.toInt() ?? 0;
    final completionRate =
        totalOrders > 0 ? (deliveredOrders / totalOrders).clamp(0.0, 1.0) : 0.0;

    final rawOrdersByDay = data['ordersByDay'];
    final ordersByDay = <String, num>{
      if (rawOrdersByDay is Map)
        for (final entry in rawOrdersByDay.entries)
          if (entry.value is num) entry.key.toString(): entry.value as num,
    };

    return [
      Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [AppColors.primaryDark, AppColors.primary],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Thu nhập ước tính trong kỳ',
              style: TextStyle(color: AppColors.primarySurface, fontSize: 13),
            ),
            const SizedBox(height: 6),
            Text(
              earnings == null ? 'Chưa cấu hình' : formatCurrency(earnings),
              style: const TextStyle(
                color: Colors.white,
                fontSize: 26,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: completionRate,
                minHeight: 6,
                backgroundColor: Colors.white.withValues(alpha: 0.22),
                valueColor: const AlwaysStoppedAnimation<Color>(Colors.white),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              '$deliveredOrders / $totalOrders đơn đã giao trong kỳ',
              style: const TextStyle(
                color: AppColors.primarySurface,
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),

      // Compact KPI grid for Today / Week / Month
      LayoutBuilder(
        builder: (context, constraints) {
          final spacing = 8.0;
          final wide = constraints.maxWidth >= 320;
          final tileWidth = wide
              ? (constraints.maxWidth - spacing * 2) / 3
              : constraints.maxWidth;
          final metrics = [
            ('Hôm nay', (data['todayOrders'] as num?)?.toInt() ?? 0),
            ('Tuần này', (data['weekOrders'] as num?)?.toInt() ?? 0),
            ('Tháng này', (data['monthOrders'] as num?)?.toInt() ?? 0),
          ];
          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Wrap(
              spacing: spacing,
              runSpacing: spacing,
              children: [
                for (final (label, value) in metrics)
                  SizedBox(
                    width: tileWidth,
                    child: Card(
                      margin: EdgeInsets.zero,
                      color: AppColors.card,
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(
                          ShipperLayout.innerRadius,
                        ),
                        side: const BorderSide(color: AppColors.border),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 12,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              label,
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.textMuted,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '$value đơn',
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                color: AppColors.textMain,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      ),

      Card(
        color: AppColors.card,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
          side: const BorderSide(color: AppColors.border),
        ),
        child: Column(
          children: [
            for (final (idx, item) in const [
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
            ].indexed) ...[
              if (idx > 0) const Divider(height: 1, indent: 56),
              ListTile(
                leading: Icon(item.$1, color: AppColors.primary),
                title: Text(
                  item.$2,
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.textMuted,
                  ),
                ),
                subtitle: Text(
                  formatCurrency(data[item.$3] as num?),
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                    color: AppColors.textMain,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),

      Card(
        color: AppColors.card,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
          side: const BorderSide(color: AppColors.border),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Đơn đã giao theo ngày',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.textMain,
                ),
              ),
              const SizedBox(height: 14),
              _chart(ordersByDay),
            ],
          ),
        ),
      ),
    ];
  }

  Widget _chart(Map<String, num> days) {
    if (days.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 16),
        child: Center(
          child: Text(
            'Không có dữ liệu trong kỳ',
            style: TextStyle(color: AppColors.textMuted),
          ),
        ),
      );
    }

    final entries = days.entries.toList()
      ..sort((a, b) => a.key.compareTo(b.key));
    final colors = Theme.of(context).colorScheme;
    final maxCount = days.values.reduce(max);
    final yInterval = max(1, (maxCount / 4).ceil()).toDouble();

    return LayoutBuilder(
      builder: (context, constraints) {
        return SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: SizedBox(
            height: 230,
            width: max(constraints.maxWidth, entries.length * 48.0 + 40),
            child: BarChart(
              BarChartData(
                borderData: FlBorderData(show: false),
                gridData: FlGridData(
                  drawVerticalLine: false,
                  horizontalInterval: yInterval,
                ),
                maxY: max(1, maxCount).toDouble(),
                barTouchData: BarTouchData(
                  touchTooltipData: BarTouchTooltipData(
                    getTooltipItem: (group, groupIndex, rod, rodIndex) {
                      if (groupIndex < 0 || groupIndex >= entries.length) {
                        return null;
                      }
                      final entry = entries[groupIndex];
                      final parsed = DateTime.tryParse(entry.key);
                      final label = parsed == null
                          ? entry.key
                          : '${parsed.day.toString().padLeft(2, '0')}/${parsed.month.toString().padLeft(2, '0')}';
                      return BarTooltipItem(
                        '$label\n${rod.toY.toInt()} đơn',
                        const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      );
                    },
                  ),
                ),
                titlesData: FlTitlesData(
                  topTitles: const AxisTitles(),
                  rightTitles: const AxisTitles(),
                  leftTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 34,
                      interval: yInterval,
                      getTitlesWidget: (value, meta) {
                        if (value % 1 != 0) return const SizedBox.shrink();
                        return Text(
                          '${value.toInt()}',
                          style: Theme.of(context).textTheme.labelSmall,
                        );
                      },
                    ),
                  ),
                  bottomTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 30,
                      interval: 1,
                      getTitlesWidget: (value, meta) {
                        if (value % 1 != 0) return const SizedBox.shrink();
                        final idx = value.toInt();
                        if (idx < 0 || idx >= entries.length) {
                          return const SizedBox.shrink();
                        }
                        final parsed = DateTime.tryParse(entries[idx].key);
                        final text = parsed == null
                            ? entries[idx].key
                            : '${parsed.day.toString().padLeft(2, '0')}/${parsed.month.toString().padLeft(2, '0')}';
                        return Padding(
                          padding: const EdgeInsets.only(top: 6),
                          child: Text(
                            text,
                            style: Theme.of(context).textTheme.labelSmall,
                          ),
                        );
                      },
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
                          width: 16,
                          borderRadius: const BorderRadius.vertical(
                            top: Radius.circular(6),
                          ),
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
