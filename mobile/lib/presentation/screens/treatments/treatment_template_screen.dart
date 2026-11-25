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
        builder: (template) {
          final serviceDetails = template.serviceDetails;
          final retail = template.originalPrice ?? 0;
          final hasSavings = retail > template.price && template.price > 0;
          final savingsPct = hasSavings
              ? ((retail - template.price) * 100 / retail).round()
              : 0;
          return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (template.thumbnailUrl != null &&
                template.thumbnailUrl!.isNotEmpty) ...[
              SizedBox(
                width: double.infinity,
                height: 190,
                child: TreatmentThumbImage(
                  url: template.thumbnailUrl,
                  borderRadius: 12,
                ),
              ),
              const SizedBox(height: 12),
            ],
            if (template.categoryName != null)
              Align(
                alignment: Alignment.centerLeft,
                child: Chip(
                  visualDensity: VisualDensity.compact,
                  label: Text(template.categoryName!),
                ),
              ),
            Text(
              template.name,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            if (template.shortDescription.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(
                template.shortDescription,
                style: Theme.of(context).textTheme.titleSmall,
              ),
            ],
            if (template.description.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(template.description),
            ],
            const SizedBox(height: 12),
            Row(
              children: [
                Text(
                  AppFormatters.formatCurrency(template.price),
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                if (hasSavings) ...[
                  const SizedBox(width: 8),
                  Text(
                    AppFormatters.formatCurrency(retail),
                    style: const TextStyle(
                      decoration: TextDecoration.lineThrough,
                      color: Colors.grey,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Chip(
                    visualDensity: VisualDensity.compact,
                    label: Text('Tiết kiệm $savingsPct%'),
                  ),
                ],
              ],
            ),
            Text(
              '${template.sessions.length} buổi'
              '${template.totalDurationMinutes > 0 ? ' • ${template.totalDurationMinutes} phút' : ''}'
              ' • ${template.validityDays == null ? 'Không giới hạn hiệu lực' : 'Hiệu lực ${template.validityDays} ngày từ thanh toán'} • Phiên bản ${template.versionNumber}',
            ),
            const SizedBox(height: 12),
            ...template.sessions.map(
              (session) {
                final minGap = treatmentInt(session['minGapDays']);
                final maxGap = session['maxGapDays'] == null
                    ? null
                    : treatmentInt(session['maxGapDays']);
                final gapLabel = treatmentAdvisoryGap(minGap, maxGap);
                final items = treatmentMaps(session['items']);
                return Card(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Buổi ${session['sequence']}: ${session['title']}',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '${session['required'] != false ? 'Buổi bắt buộc' : 'Buổi tùy chọn'}'
                          '${gapLabel.isNotEmpty ? ' • $gapLabel' : ''}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                        const SizedBox(height: 8),
                        ...items.map((item) {
                          final sid = treatmentInt(item['serviceId']);
                          final detail = serviceDetails[sid] ?? {};
                          final thumb = detail['thumbnailUrl']?.toString();
                          final duration = detail['durationMinutes'];
                          final basePrice = double.tryParse(
                            '${detail['basePrice']}',
                          );
                          final shortDesc =
                              detail['shortDescription']?.toString() ?? '';
                          final fullDesc =
                              detail['description']?.toString() ?? '';
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 8),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                if (thumb != null && thumb.isNotEmpty) ...[
                                  TreatmentThumbImage(
                                    url: thumb,
                                    width: 48,
                                    height: 48,
                                    borderRadius: 8,
                                  ),
                                  const SizedBox(width: 10),
                                ],
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        '${item['serviceName'] ?? detail['name'] ?? 'Dịch vụ #$sid'}',
                                        style: const TextStyle(
                                          fontWeight: FontWeight.w600,
                                        ),
                                      ),
                                      if (duration != null || basePrice != null)
                                        Text(
                                          [
                                            if (duration != null)
                                              '$duration phút',
                                            if (basePrice != null)
                                              'Giá lẻ ${AppFormatters.formatCurrency(basePrice)}',
                                          ].join(' • '),
                                          style: Theme.of(
                                            context,
                                          ).textTheme.bodySmall,
                                        ),
                                      if (shortDesc.isNotEmpty)
                                        Text(
                                          shortDesc,
                                          style: Theme.of(
                                            context,
                                          ).textTheme.bodySmall,
                                        ),
                                      if (fullDesc.isNotEmpty &&
                                          fullDesc != shortDesc)
                                        Text(
                                          fullDesc,
                                          maxLines: 3,
                                          overflow: TextOverflow.ellipsis,
                                          style: Theme.of(
                                            context,
                                          ).textTheme.bodySmall,
                                        ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          );
                        }),
                      ],
                    ),
                  ),
                );
              },
            ),
            const SizedBox(height: 12),
            Text('Điều khoản', style: Theme.of(context).textTheme.titleMedium),
            SelectableText(template.terms),
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Text(
                'Sau thanh toán, hồ sơ lộ trình được kích hoạt ngay để bạn chủ động đặt từng buổi. Giãn cách ngày chỉ mang tính tham khảo; mọi thay đổi phác đồ đều cần bạn xác nhận.',
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
        );
        },
      ),
    );
  }
}
