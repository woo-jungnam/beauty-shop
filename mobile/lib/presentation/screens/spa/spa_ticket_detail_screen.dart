import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/network/api_client.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/spa_model.dart';
import '../../../providers/spa_provider.dart';
import '../treatments/treatment_hub_screen.dart';
import '../treatments/treatment_enrollment_screen.dart';
import 'spa_booking_screen.dart';

class SpaTicketDetailScreen extends StatefulWidget {
  const SpaTicketDetailScreen({super.key, required this.ticket});
  final UserServiceTicket ticket;
  @override
  State<SpaTicketDetailScreen> createState() => _SpaTicketDetailScreenState();
}

class _SpaTicketDetailScreenState extends State<SpaTicketDetailScreen> {
  final _api = ApiClient();
  List<dynamic> _movements = [], _refunds = [];
  int _page = 0;
  bool _loading = true, _more = false;
  String? _error;
  static const labels = {
    'RESERVE': 'Giữ lượt',
    'RELEASE': 'Giải phóng lượt',
    'REDEEM': 'Đã thực hiện dịch vụ',
    'FORFEIT': 'Mất lượt theo chính sách',
    'EXTEND': 'Gia hạn',
    'COMPENSATE': 'Bù lượt',
    'REFUND_CLOSE': 'Quyết toán & thu hồi vé',
  };
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final history = await _api.get<dynamic>(
        '/api/v1/spa/tickets/${widget.ticket.id}/movements',
        queryParameters: {'page': _page, 'size': 10},
      );
      final refunds = await _api.get<dynamic>(
        '/api/v1/spa/tickets/${widget.ticket.id}/refunds',
      );
      if (!history.isSuccess || !refunds.isSuccess) {
        throw StateError(
          history.message ?? refunds.message ?? 'Chưa tải được lịch sử',
        );
      }
      if (!mounted) return;
      final data = Map<String, dynamic>.from(history.data as Map);
      setState(() {
        _movements = data['content'] as List? ?? [];
        _refunds = refunds.data as List? ?? [];
        _more = _page + 1 < (data['totalPages'] as num? ?? 0);
      });
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _book() async {
    final t = widget.ticket;
    if (t.benefitType == 'TREATMENT_COURSE') {
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => t.treatmentCourseId != null
              ? TreatmentEnrollmentScreen(id: t.treatmentCourseId!)
              : const TreatmentHubScreen(),
        ),
      );
      return;
    }
    final provider = context.read<SpaProvider>();
    await provider.fetchServices();
    if (!mounted) return;
    final services = provider.services
        .where(
          (s) => !s.isTreatmentCourse && (t.remainingByService[s.id] ?? 0) > 0,
        )
        .toList();
    if (services.isEmpty) {
      setState(
        () => _error =
            'Không tìm thấy dịch vụ còn lượt và đang hoạt động. Liên hệ quầy để đối soát.',
      );
      return;
    }
    final selected = await showDialog<SpaService>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('Chọn dịch vụ sử dụng vé'),
        children: services
            .map(
              (s) => SimpleDialogOption(
                onPressed: () => Navigator.pop(ctx, s),
                child: Text('${s.name} · ${t.remainingByService[s.id]} lượt'),
              ),
            )
            .toList(),
      ),
    );
    if (selected != null && mounted) {
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => SpaBookingScreen(service: selected, ticketId: t.id),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = widget.ticket;
    return Scaffold(
      appBar: AppBar(title: Text('Vé #${t.id}')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(t.serviceName, style: Theme.of(context).textTheme.titleLarge),
          if (!t.nameSnapshot)
            const Text('Tên hiện tại; chưa có snapshot tên lúc mua.'),
          Text(
            t.benefitType == 'TREATMENT_COURSE'
                ? 'Lộ trình điều trị'
                : 'Gói nhiều lượt thông thường',
          ),
          Text(
            '${t.isValid ? 'Khả dụng' : 'Chưa dùng (vé không khả dụng)'} ${t.remainingQuantity}/${t.totalQuantity} · Đang giữ ${t.reservedSessions}',
          ),
          ...t.services.map(
            (s) => ListTile(
              title: Text('${s['serviceName']}'),
              subtitle: Text(
                '${s['available']} ${t.isValid ? 'khả dụng' : 'chưa dùng (vé không khả dụng)'} · ${s['used']} đã dùng/mất · ${s['reserved']} đang giữ / ${s['total']} tổng',
              ),
            ),
          ),
          FilledButton(
            onPressed: t.isValid ? _book : null,
            child: Text(
              t.benefitType == 'TREATMENT_COURSE'
                  ? 'Mở lộ trình điều trị'
                  : 'Đặt lịch bằng vé',
            ),
          ),
          const Divider(),
          Text('Lịch sử vé', style: Theme.of(context).textTheme.titleMedium),
          if (_loading) const LinearProgressIndicator(),
          if (_error != null)
            TextButton(onPressed: _load, child: Text('$_error · Thử lại')),
          ..._movements.map(
            (m) => ListTile(
              title: Text('${labels[m['operation']] ?? m['operation']}'),
              subtitle: Text('${m['createdAt']}\n${m['reason'] ?? ''}'),
            ),
          ),
          Row(
            children: [
              TextButton(
                onPressed: _loading || _page == 0
                    ? null
                    : () {
                        _page--;
                        _load();
                      },
                child: const Text('Trang trước'),
              ),
              TextButton(
                onPressed: _loading || !_more
                    ? null
                    : () {
                        _page++;
                        _load();
                      },
                child: const Text('Trang tiếp'),
              ),
            ],
          ),
          ..._refunds.map(
            (r) => ListTile(
              title: Text(
                'Đã quyết toán ${HasakiFormatters.formatCurrency((r['amount'] as num).toDouble())}',
              ),
              subtitle: Text('${r['reference']} · ${r['reason']}'),
            ),
          ),
        ],
      ),
    );
  }
}
