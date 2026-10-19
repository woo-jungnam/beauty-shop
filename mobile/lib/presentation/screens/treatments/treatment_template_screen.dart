import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/treatment_model.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/treatment_provider.dart';
import '../auth/login_screen.dart';
import '../checkout/vietqr_payment_screen.dart';
import '../orders/order_history_screen.dart';
import 'treatment_widgets.dart';

class TreatmentTemplateScreen extends StatefulWidget {
  const TreatmentTemplateScreen({super.key, required this.id});
  final int id;
  @override
  State<TreatmentTemplateScreen> createState() =>
      _TreatmentTemplateScreenState();
}

class _TreatmentTemplateScreenState extends State<TreatmentTemplateScreen> {
  Future<TreatmentTemplate>? _future;
  bool _accepted = false, _busy = false;
  String? _error;
  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _accepted = false;
    _future = context.read<TreatmentProvider>().template(widget.id);
  }

  Future<void> _purchase(TreatmentTemplate template) async {
    if (!context.read<AuthProvider>().isAuthenticated) {
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
      );
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final order = await context.read<TreatmentProvider>().purchase(template);
      if (!mounted) return;
      setState(() => _busy = false);
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => order.isPaid
              ? const OrderHistoryScreen()
              : VietQrPaymentScreen(order: order),
        ),
      );
      if (mounted) setState(_load);
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = '$error';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    context.watch<AuthProvider>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Chi tiết lộ trình'),
        actions: [
          IconButton(
            onPressed: _busy ? null : () => setState(_load),
            icon: const Icon(Icons.refresh),
            tooltip: 'Cập nhật',
          ),
        ],
      ),
      body: TreatmentAsync(
        future: _future,
        retry: () => setState(_load),
        builder: (template) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              template.name,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            Text(template.description),
            const SizedBox(height: 12),
            Text(
              HasakiFormatters.formatCurrency(template.price),
              style: Theme.of(context).textTheme.titleLarge,
            ),
            Text(
              '${template.sessions.length} buổi • ${template.validityDays == null ? 'Không giới hạn hiệu lực' : 'Hiệu lực ${template.validityDays} ngày từ thanh toán'} • Phiên bản ${template.versionNumber}',
            ),
            const SizedBox(height: 12),
            ...template.sessions.map(
              (session) => Card(
                child: ListTile(
                  title: Text(
                    'Buổi ${session['sequence']}: ${session['title']}',
                  ),
                  subtitle: Text(
                    '${treatmentMaps(session['items']).map((item) => item['serviceName'] ?? 'Dịch vụ #${item['serviceId']}').join('\n')}\n'
                    'Khoảng cách tối thiểu: ${session['minGapDays'] ?? 0} ngày'
                    '${session['maxGapDays'] == null ? '' : ' • Tối đa ${session['maxGapDays']} ngày'}\n'
                    '${session['required'] != false ? 'Buổi bắt buộc' : 'Buổi tùy chọn'}',
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text('Điều khoản', style: Theme.of(context).textTheme.titleMedium),
            SelectableText(template.terms),
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Text(
                'Sau thanh toán, nhân viên tiếp nhận và rà soát kế hoạch riêng. Mọi thay đổi cam kết cần được bạn xác nhận. Hồ sơ chỉ được cấp khi hệ thống ghi nhận thanh toán.',
              ),
            ),
            CheckboxListTile(
              value: _accepted,
              onChanged: _busy
                  ? null
                  : (value) => setState(() => _accepted = value!),
              contentPadding: EdgeInsets.zero,
              title: Text(
                'Tôi đã đọc và đồng ý điều khoản của phiên bản ${template.versionNumber}',
              ),
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(
                  '$_error\nNếu kết nối gián đoạn, kiểm tra Đơn hàng trước khi thử lại. Lần gửi lại dùng cùng mã yêu cầu mua.',
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            FilledButton.icon(
              onPressed:
                  !_accepted ||
                      _busy ||
                      !template.allowedActions.contains('PURCHASE')
                  ? null
                  : () => _purchase(template),
              icon: _busy
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.shopping_bag_outlined),
              label: const Text('Mua lộ trình và thanh toán'),
            ),
            if (!template.allowedActions.contains('PURCHASE'))
              const Text(
                'Lộ trình này hiện chưa mở bán. Các hồ sơ đã mua vẫn được quản lý trong mục Của tôi.',
              ),
            TextButton(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => const OrderHistoryScreen(unpaidOnly: true),
                ),
              ),
              child: const Text('Xem đơn chờ thanh toán'),
            ),
          ],
        ),
      ),
    );
  }
}
