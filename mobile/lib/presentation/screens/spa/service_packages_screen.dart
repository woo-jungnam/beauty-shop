import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/spa_provider.dart';
import '../../../providers/auth_provider.dart';
import '../../../core/utils/formatters.dart';
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
            '${HasakiFormatters.formatCurrency((pack['price'] as num).toDouble())}\n'
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
              (p) => Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${p['name']}',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      Text('${p['description'] ?? ''}'),
                      Text(
                        HasakiFormatters.formatCurrency(
                          (p['price'] as num).toDouble(),
                        ),
                      ),
                      Text(
                        p['validityDays'] == null
                            ? 'Không giới hạn hạn dùng'
                            : '${p['validityDays']} ngày từ thanh toán',
                      ),
                      ...(p['items'] as List).map(
                        (s) =>
                            Text('${s['serviceName']} — ${s['quantity']} lượt'),
                      ),
                      FilledButton(
                        onPressed: _busy ? null : () => _buy(p),
                        child: const Text('Xem điều kiện & mua gói'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        );
      },
    ),
  );
}
