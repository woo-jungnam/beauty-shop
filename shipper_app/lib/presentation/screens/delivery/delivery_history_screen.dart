import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';
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
    for (final order in _orders) {
      final timestamp =
          order.deliveredAt ?? order.assignedAt ?? order.createdAt;
      final date =
          timestamp == null ? 'Chưa rõ ngày' : formatVnDate(timestamp);
      groups.putIfAbsent(date, () => []).add(order);
    }

    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: ShipperLayout.pagePadding,
        children: [
          Text(
            'Lịch sử giao hàng',
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
              _load(reset: true);
            },
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: SegmentedButton<bool>(
              showSelectedIcon: false,
              style: ButtonStyle(
                visualDensity: VisualDensity.compact,
                padding: WidgetStateProperty.all(
                  const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                ),
              ),
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
          ),
          const SizedBox(height: 14),
          for (final group in groups.entries) ...[
            Padding(
              padding: const EdgeInsets.only(top: 4, bottom: 8),
              child: Row(
                children: [
                  const Icon(
                    Icons.calendar_today_outlined,
                    size: 15,
                    color: AppColors.primary,
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      group.key,
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
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
                      color: AppColors.surfaceMuted,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '${group.value.length} mục${!_last && group.key == groups.keys.last ? ' đã tải' : ''}',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textMuted,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            for (final order in group.value) _buildHistoryCard(order),
          ],
          if (_loading)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(child: CircularProgressIndicator()),
            ),
          if (_error != null)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    Text(_error!, textAlign: TextAlign.center),
                    const SizedBox(height: 8),
                    FilledButton(
                      onPressed: () => _load(reset: _orders.isEmpty),
                      child: const Text('Thử lại'),
                    ),
                  ],
                ),
              ),
            )
          else if (!_loading && !_last)
            Center(
              child: OutlinedButton(
                onPressed: _load,
                child: const Text('Tải thêm'),
              ),
            ),
          if (!_loading && _error == null && _orders.isEmpty)
            const Card(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 28, horizontal: 16),
                child: Column(
                  children: [
                    Icon(
                      Icons.history_toggle_off_rounded,
                      size: 38,
                      color: AppColors.textMuted,
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Không có đơn trong khoảng ngày này',
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
      ),
    );
  }

  Widget _buildHistoryCard(ShipperOrderModel order) {
    final style = ShipperStatusStyle.fromOrder(order);
    final pendingCod = order.codCollected - order.codRemitted;
    final subtitleText =
        !order.hasOrderDetails && (order.notes?.trim().isNotEmpty ?? false)
            ? order.notes!.trim()
            : order.customerName;

    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      clipBehavior: Clip.antiAlias,
      color: AppColors.card,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
        side: const BorderSide(color: AppColors.border),
      ),
      child: InkWell(
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => ShipperOrderDetailScreen(
              order: order,
              enableDeliveryActions: true,
            ),
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                alignment: WrapAlignment.spaceBetween,
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                runSpacing: 6,
                children: [
                  Text(
                    '#${order.orderNumber}',
                    style: const TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 15,
                      color: AppColors.textMain,
                    ),
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Flexible(
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 9,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: style.background,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Text(
                            style.label,
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: style.foreground,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      const Icon(
                        Icons.chevron_right_rounded,
                        size: 20,
                        color: AppColors.textMuted,
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                subtitleText,
                style: const TextStyle(
                  fontSize: 13,
                  color: AppColors.textMuted,
                ),
              ),
              if (order.codCollected > 0) ...[
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.warningLight,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    'COD chờ bàn giao: ${formatCurrency(pendingCod)}',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: AppColors.warning,
                    ),
                  ),
                ),
              ],
              if (order.items.isNotEmpty || order.itemsSummary.isNotEmpty) ...[
                const SizedBox(height: 6),
                OrderItemsSection(order: order, preview: true),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
