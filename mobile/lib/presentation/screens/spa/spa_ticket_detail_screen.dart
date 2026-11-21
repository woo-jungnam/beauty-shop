import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/network/api_client.dart';
import '../../../core/theme/app_colors.dart';
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
  List<dynamic> _requests = [];
  UserServiceTicket? _currentTicket;
  Map<String, dynamic>? _quote;
  bool _sending = false;
  String _reason = '', _days = '30';
  String? _requestType;
  String get _requestPath => '/api/v1/spa/tickets/${widget.ticket.id}/requests';
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
      final ticketResult = await _api.get<dynamic>(
        '/api/v1/spa/tickets/${widget.ticket.id}',
      );
      if (!ticketResult.isSuccess)
        throw StateError(ticketResult.message ?? 'Chưa tải được vé');
      final latest = UserServiceTicket.fromJson(
        Map<String, dynamic>.from(ticketResult.data as Map),
      );
      final history = await _api.get<dynamic>(
        '/api/v1/spa/tickets/${widget.ticket.id}/movements',
        queryParameters: {'page': _page, 'size': 10},
      );
      final refunds = await _api.get<dynamic>(
        '/api/v1/spa/tickets/${widget.ticket.id}/refunds',
      );
      final requests = widget.ticket.benefitType == 'TREATMENT_COURSE'
          ? null
          : await _api.get<dynamic>(_requestPath);
      final quote =
          latest.benefitType == 'TREATMENT_COURSE' || latest.status == 'REVOKED'
          ? null
          : await _api.get<dynamic>(
              '/api/v1/spa/tickets/${latest.id}/settlement-quote',
            );
      if (requests != null && !requests.isSuccess)
        throw StateError(requests.message ?? 'Chưa tải được yêu cầu');
      if (!history.isSuccess || !refunds.isSuccess) {
        throw StateError(
          history.message ?? refunds.message ?? 'Chưa tải được lịch sử',
        );
      }
      if (!mounted) return;
      final data = Map<String, dynamic>.from(history.data as Map);
      setState(() {
        _currentTicket = latest;
        _quote = quote?.data is Map
            ? Map<String, dynamic>.from(quote!.data as Map)
            : null;
        _movements = data['content'] as List? ?? [];
        _refunds = refunds.data as List? ?? [];
        _requests = requests?.data as List? ?? [];
        _more = _page + 1 < (data['totalPages'] as num? ?? 0);
      });
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submitRequest() async {
    if (_sending) return;
    final days = int.tryParse(_days);
    if (_reason.trim().isEmpty ||
        (_requestType == 'EXTEND' &&
            (days == null || days < 1 || days > 3650))) {
      setState(() => _error = 'Nhập lý do và số ngày gia hạn hợp lệ (1–3650).');
      return;
    }
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      final result = await _api.post<dynamic>(
        _requestPath,
        data: {
          'type': _requestType,
          'reason': _reason.trim(),
          if (_requestType == 'EXTEND') 'extensionDays': days,
        },
      );
      if (!result.isSuccess)
        throw StateError(result.message ?? 'Chưa gửi được yêu cầu');
      if (!mounted) return;
      setState(() {
        _requestType = null;
        _reason = '';
      });
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _book() async {
    final t = _currentTicket ?? widget.ticket;
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
      if (mounted) await _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = _currentTicket ?? widget.ticket;
    return Scaffold(
      appBar: AppBar(title: Text('Vé #${t.id}')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(t.serviceName, style: Theme.of(context).textTheme.titleLarge),
          Text(
            t.benefitType == 'TREATMENT_COURSE'
                ? 'Lộ trình chăm sóc chuyên sâu'
                : 'Gói dịch vụ spa',
            style: const TextStyle(
              color: AppColors.primary,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Trạng thái: ${t.status == 'AWAITING_SETTLEMENT'
                ? 'Đã dừng · Chờ quyết toán'
                : t.status == 'REVOKED'
                ? 'Đã thu hồi'
                : t.isValid
                ? 'Còn hiệu lực'
                : 'Không khả dụng'} · Còn ${t.remainingQuantity}/${t.totalQuantity} buổi',
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          ...t.services.map(
            (s) => ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              title: Text(
                '${s['serviceName']}',
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              subtitle: Text(
                'Còn ${s['available']} buổi · Đang giữ: ${s['reserved'] ?? 0} · Đã dùng hoặc mất lượt: ${s['used']} · Tổng: ${s['total']} buổi',
              ),
            ),
          ),
          FilledButton(
            onPressed: t.isValid || t.benefitType == 'TREATMENT_COURSE'
                ? _book
                : null,
            child: Text(
              t.benefitType == 'TREATMENT_COURSE'
                  ? 'Mở lộ trình điều trị'
                  : 'Đặt lịch bằng vé',
            ),
          ),
          if (t.benefitType != 'TREATMENT_COURSE') ...[
            if (_quote != null)
              Text(
                _quote!['blockedReason']?.toString() ??
                    'Hoàn dự kiến: ${AppFormatters.formatCurrency((_quote!['refundableAmount'] as num).toDouble())}',
              ),
            for (final request in _requests)
              ListTile(
                title: Text(
                  request['type'] == 'EXTEND'
                      ? 'Gia hạn ${request['extensionDays']} ngày'
                      : 'Dừng và hoàn tiền',
                ),
                subtitle: Text(
                  '${const {'PENDING_APPROVAL': 'Chờ duyệt', 'APPROVED': 'Đã gia hạn', 'REJECTED': 'Từ chối', 'AWAITING_SETTLEMENT': 'Đã dừng · Chờ quyết toán', 'COMPLETED': 'Đã quyết toán'}[request['status']] ?? request['status']} · ${request['reviewReason'] ?? request['reason']}',
                ),
              ),
            if (!_loading &&
                _error == null &&
                !['REVOKED', 'AWAITING_SETTLEMENT'].contains(t.status) &&
                !_requests.any(
                  (r) => [
                    'PENDING_APPROVAL',
                    'AWAITING_SETTLEMENT',
                  ].contains(r['status']),
                ))
              Wrap(
                children: [
                  if (t.expiresAt != null)
                    TextButton(
                      onPressed: _sending
                          ? null
                          : () => setState(() => _requestType = 'EXTEND'),
                      child: const Text('Xin gia hạn'),
                    ),
                  TextButton(
                    onPressed: _sending
                        ? null
                        : () => setState(() => _requestType = 'CANCEL'),
                    child: const Text('Yêu cầu dừng gói'),
                  ),
                ],
              ),
            if (_requestType != null) ...[
              if (_requestType == 'EXTEND')
                TextFormField(
                  initialValue: _days,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Số ngày gia hạn',
                  ),
                  onChanged: (v) => _days = v,
                ),
              TextFormField(
                initialValue: _reason,
                maxLength: 1000,
                decoration: const InputDecoration(labelText: 'Lý do'),
                onChanged: (v) => _reason = v,
              ),
              FilledButton(
                onPressed: _sending ? null : _submitRequest,
                child: Text(
                  _requestType == 'EXTEND'
                      ? 'Gửi yêu cầu gia hạn'
                      : 'Gửi yêu cầu dừng gói',
                ),
              ),
            ],
          ],
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
                'Đã quyết toán ${AppFormatters.formatCurrency((r['amount'] as num).toDouble())}',
              ),
              subtitle: Text('${r['reference']} · ${r['reason']}'),
            ),
          ),
        ],
      ),
    );
  }
}
