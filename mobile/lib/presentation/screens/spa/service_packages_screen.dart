import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/spa_provider.dart';
import '../../../providers/auth_provider.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/utils/image_utils.dart';
import '../auth/login_screen.dart';
import '../checkout/vietqr_payment_screen.dart';
import 'spa_tickets_screen.dart';

class ServicePackagesScreen extends StatefulWidget {
  const ServicePackagesScreen({super.key});
  @override
  State<ServicePackagesScreen> createState() => _ServicePackagesScreenState();
}

class _ServicePackagesScreenState extends State<ServicePackagesScreen> {
  late Future<List<Map<String, dynamic>>> _future;
  bool _busy = false;
  String? _error;
  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _future = context.read<SpaProvider>().servicePackages();
  }

  Future<void> _buy(Map<String, dynamic> pack) async {
    if (_busy) return;
    if (!context.read<AuthProvider>().isAuthenticated) {
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
      );
      return;
    }
    final accepted = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Mua ${pack['name']}'),
        content: SingleChildScrollView(
          child: Text(
            '${AppFormatters.formatCurrency((pack['price'] as num).toDouble())}\n'
            '${(pack['items'] as List).map((s) => "${s['serviceName']}: ${s['quantity']} lượt").join('\n')}\n'
            '${pack['validityDays'] == null ? 'Không giới hạn hạn dùng' : '${pack['validityDays']} ngày từ khi thanh toán'}.\n'
            'Vé chỉ cấp sau xác nhận đủ tiền. Hủy/vắng mặt theo chính sách lúc đặt. Quyết toán cần hủy lịch đang giữ; lượt đã dùng/mất không được hoàn. Giá trị còn lại phân bổ theo giá dịch vụ lúc mua, làm tròn xuống VND; lượt bù không làm tăng tiền hoàn.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Đóng'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Đồng ý & tạo đơn'),
          ),
        ],
      ),
    );
    if (accepted != true || !mounted) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final order = await context.read<SpaProvider>().purchasePackage(pack);
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => order.isPaid
              ? const SpaTicketsScreen()
              : VietQrPaymentScreen(order: order),
        ),
      );
    } catch (e) {
      if (mounted) {
        setState(
          () => _error =
              '$e\nThử lại sử dụng cùng yêu cầu; kiểm tra Đơn hàng trước khi mua mới.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _packageImage(String? rawUrl, {double height = 160}) {
    final resolved = ImageUtils.resolveUrl(rawUrl);
    if (resolved == null || resolved.isEmpty) return const SizedBox.shrink();
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: Image.network(
        resolved,
        width: double.infinity,
        height: height,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => const SizedBox.shrink(),
      ),
    );
  }

  void _showPackageDetail(Map<String, dynamic> pack) {
    final items = (pack['items'] as List? ?? []);
    final price = (pack['price'] as num).toDouble();
    final originalPrice = (pack['originalPrice'] as num?)?.toDouble() ?? 0;
    final totalDuration = (pack['totalDurationMinutes'] as num?)?.toInt() ?? 0;
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.78,
        minChildSize: 0.45,
        maxChildSize: 0.94,
        builder: (_, controller) => ListView(
          controller: controller,
          padding: const EdgeInsets.all(16),
          children: [
            _packageImage(pack['thumbnailUrl']?.toString(), height: 185),
            const SizedBox(height: 12),
            Text(
              '${pack['name']}',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                Text(
                  AppFormatters.formatCurrency(price),
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                if (originalPrice > price) ...[
                  const SizedBox(width: 8),
                  Text(
                    AppFormatters.formatCurrency(originalPrice),
                    style: const TextStyle(
                      decoration: TextDecoration.lineThrough,
                      color: Colors.grey,
                    ),
                  ),
                ],
              ],
            ),
            Text(
              '${pack['validityDays'] == null ? 'Không giới hạn hạn dùng' : '${pack['validityDays']} ngày từ khi thanh toán'}'
              '${totalDuration > 0 ? ' • Tổng $totalDuration phút' : ''}',
            ),
            if ((pack['description']?.toString() ?? '').isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('${pack['description']}'),
            ],
            const Divider(height: 24),
            Text(
              'Dịch vụ trong gói (${items.length})',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            ...items.map((raw) {
              final s = Map<String, dynamic>.from(raw as Map);
              final thumb = ImageUtils.resolveUrl(s['thumbnailUrl']?.toString());
              final duration = (s['durationMinutes'] as num?)?.toInt();
              final unitPrice = (s['basePrice'] as num?)?.toDouble();
              final cat = s['categoryName']?.toString();
              final shortDesc = s['shortDescription']?.toString() ?? '';
              final fullDesc = s['description']?.toString() ?? '';
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                child: Padding(
                  padding: const EdgeInsets.all(10),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (thumb != null && thumb.isNotEmpty) ...[
                        ClipRRect(
                          borderRadius: BorderRadius.circular(8),
                          child: Image.network(
                            thumb,
                            width: 56,
                            height: 56,
                            fit: BoxFit.cover,
                            errorBuilder: (_, _, _) => const SizedBox.shrink(),
                          ),
                        ),
                        const SizedBox(width: 10),
                      ],
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${s['serviceName']} (${s['quantity']} lượt)',
                              style: const TextStyle(fontWeight: FontWeight.w700),
                            ),
                            Text(
                              [
                                if (cat != null) cat,
                                if (duration != null) '$duration phút/buổi',
                                if (unitPrice != null)
                                  'Giá lẻ ${AppFormatters.formatCurrency(unitPrice)}',
                              ].join(' • '),
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                            if (shortDesc.isNotEmpty) ...[
                              const SizedBox(height: 4),
                              Text(shortDesc),
                            ],
                            if (fullDesc.isNotEmpty && fullDesc != shortDesc) ...[
                              const SizedBox(height: 4),
                              Text(
                                fullDesc,
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _busy
                  ? null
                  : () {
                      Navigator.pop(ctx);
                      _buy(pack);
                    },
              child: const Text('Xem điều kiện & mua gói'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Gói dịch vụ nhiều lượt'),
      actions: [
        IconButton(
          onPressed: _busy ? null : () => setState(_load),
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: FutureBuilder<List<Map<String, dynamic>>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(
            child: TextButton(
              onPressed: () => setState(_load),
              child: Text('Không tải được gói: ${snapshot.error}. Thử lại'),
            ),
          );
        }
        final packs = snapshot.data ?? [];
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (_error != null)
              Text(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            if (_busy) const LinearProgressIndicator(),
            if (packs.isEmpty) const Text('Chưa có gói đang bán.'),
            ...packs.map(
              (p) {
                final price = (p['price'] as num).toDouble();
                final original = (p['originalPrice'] as num?)?.toDouble() ?? 0;
                final totalDuration =
                    (p['totalDurationMinutes'] as num?)?.toInt() ?? 0;
                final items = p['items'] as List? ?? [];
                final discountPct = original > price && price > 0
                    ? ((original - price) * 100 / original).round()
                    : 0;
                return Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  clipBehavior: Clip.antiAlias,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _packageImage(p['thumbnailUrl']?.toString()),
                        if (ImageUtils.resolveUrl(p['thumbnailUrl']?.toString()) !=
                            null)
                          const SizedBox(height: 12),
                        Text(
                          '${p['name']}',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        if ((p['description']?.toString() ?? '').isNotEmpty) ...[
                          const SizedBox(height: 4),
                          Text('${p['description']}'),
                        ],
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Text(
                              AppFormatters.formatCurrency(price),
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                            if (discountPct > 0) ...[
                              const SizedBox(width: 8),
                              Text(
                                AppFormatters.formatCurrency(original),
                                style: const TextStyle(
                                  decoration: TextDecoration.lineThrough,
                                  color: Colors.grey,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Chip(
                                visualDensity: VisualDensity.compact,
                                label: Text('Tiết kiệm $discountPct%'),
                              ),
                            ],
                          ],
                        ),
                        Text(
                          '${p['validityDays'] == null ? 'Không giới hạn hạn dùng' : '${p['validityDays']} ngày từ thanh toán'}'
                          '${totalDuration > 0 ? ' • Tổng $totalDuration phút' : ''}',
                        ),
                        const SizedBox(height: 8),
                        ...items.map((raw) {
                          final s = Map<String, dynamic>.from(raw as Map);
                          final thumb = ImageUtils.resolveUrl(
                            s['thumbnailUrl']?.toString(),
                          );
                          final duration = (s['durationMinutes'] as num?)?.toInt();
                          final unitPrice = (s['basePrice'] as num?)?.toDouble();
                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 4),
                            child: Row(
                              children: [
                                if (thumb != null && thumb.isNotEmpty) ...[
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(6),
                                    child: Image.network(
                                      thumb,
                                      width: 36,
                                      height: 36,
                                      fit: BoxFit.cover,
                                      errorBuilder: (_, _, _) =>
                                          const SizedBox.shrink(),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                ],
                                Expanded(
                                  child: Text(
                                    '${s['serviceName']} — ${s['quantity']} lượt'
                                    '${duration != null ? ' (${duration}p)' : ''}'
                                    '${unitPrice != null ? ' • Lẻ ${AppFormatters.formatCurrency(unitPrice)}' : ''}',
                                  ),
                                ),
                              ],
                            ),
                          );
                        }),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            OutlinedButton(
                              onPressed: _busy ? null : () => _showPackageDetail(p),
                              child: const Text('Chi tiết gói'),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: FilledButton(
                                onPressed: _busy ? null : () => _buy(p),
                                child: const Text('Xem điều kiện & mua gói'),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ],
        );
      },
    ),
  );
}
