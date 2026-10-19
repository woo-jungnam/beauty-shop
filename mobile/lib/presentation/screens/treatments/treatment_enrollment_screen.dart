import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/order_model.dart';
import '../../../data/models/treatment_model.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/treatment_provider.dart';
import '../checkout/vietqr_payment_screen.dart';
import 'treatment_widgets.dart';
import 'treatment_booking_screen.dart';
import 'treatment_appointment_screen.dart';
import 'treatment_template_editor.dart';

class TreatmentEnrollmentScreen extends StatefulWidget {
  const TreatmentEnrollmentScreen({
    super.key,
    required this.id,
    this.area = TreatmentArea.customer,
  });
  final int id;
  final TreatmentArea area;
  @override
  State<TreatmentEnrollmentScreen> createState() =>
      _TreatmentEnrollmentScreenState();
}

class _TreatmentEnrollmentScreenState extends State<TreatmentEnrollmentScreen> {
  Future<TreatmentEnrollment>? _future;
  bool _busy = false;
  String? _error;
  Set<int> _staffIds = {};
  String? _assignmentError;
  late final int _generation;
  bool get _operations => widget.area != TreatmentArea.customer;
  String get _base => '${widget.area.path}/${widget.id}';
  @override
  void initState() {
    super.initState();
    _generation = context.read<TreatmentProvider>().generation;
    _load();
  }

  void _load() {
    _future = _fetch();
  }

  Future<TreatmentEnrollment> _fetch() async {
    final provider = context.read<TreatmentProvider>();
    final course = await provider.enrollment(widget.id, widget.area);
    Set<int> ids = {};
    String? error;
    if (_operations && provider.canExecuteSpa && !provider.isAdmin) {
      try {
        ids = await provider.currentStaffIds();
      } catch (failure) {
        error = 'Chưa xác minh được phân công: $failure';
      }
    }
    if (_generation != provider.generation) {
      throw const TreatmentFailure('Phiên đăng nhập đã thay đổi.');
    }
    _staffIds = ids;
    _assignmentError = error;
    return course;
  }

  bool _clinical(TreatmentEnrollment course) {
    final provider = context.read<TreatmentProvider>();
    return _operations &&
        (provider.isAdmin ||
            provider.canExecuteSpa &&
                _staffIds.contains(
                  treatmentInt(course.raw['assignedStaffId']),
                ));
  }

  bool _shared(TreatmentEnrollment course) =>
      _clinical(course) ||
      course.userId == context.read<TreatmentProvider>().userId;

  void _reload() => setState(_load);
  Future<bool> _write(String path, JsonMap data) async {
    if (_generation != context.read<TreatmentProvider>().generation) {
      return false;
    }
    if (_busy) return false;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await context.read<TreatmentProvider>().write(path, data);
      if (!mounted) return false;
      setState(() {
        _busy = false;
        _load();
      });
      return true;
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error =
              '$error\nHãy kiểm tra trạng thái mới nhất trước khi gửi lại.';
          _load();
        });
      }
      return false;
    }
  }

  Future<void> _intake() async {
    final data = await treatmentTextForm(
      context,
      title: 'Tiếp nhận và xác nhận kế hoạch',
      fields: {'notes': 'Đánh giá tiếp nhận và lưu ý'},
    );
    if (data != null && mounted) await _write('$_base/intake', data);
  }

  Future<void> _assign() async {
    final provider = context.read<TreatmentProvider>();
    final staffFuture = provider.staff();
    final chosen = await showDialog<JsonMap>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Chọn người phụ trách'),
        content: SizedBox(
          width: double.maxFinite,
          height: 320,
          child: TreatmentAsync(
            future: staffFuture,
            retry: () {
              Navigator.pop(context);
              _assign();
            },
            builder: (staff) => ListView(
              children: staff
                  .map(
                    (person) => ListTile(
                      title: Text(
                        '${person['fullName'] ?? person['name'] ?? 'Nhân viên #${person['id']}'}',
                      ),
                      onTap: () => Navigator.pop(context, person),
                    ),
                  )
                  .toList(),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Hủy'),
          ),
        ],
      ),
    );
    if (chosen == null || !mounted) return;
    final reason = await treatmentTextForm(
      context,
      title: 'Lý do phân công',
      fields: {'reason': 'Lý do'},
    );
    if (reason != null && mounted) {
      await _write('$_base/assign', {'staffId': chosen['id'], ...reason});
    }
  }

  Future<void> _propose(TreatmentEnrollment course) async {
    final current = treatmentMap(course.raw['currentPlan']);
    if (current.isEmpty) {
      setState(
        () => _error =
            'Chưa tải được phiên bản kế hoạch hiện hành. Hãy cập nhật hồ sơ.',
      );
      return;
    }
    final reason = await treatmentTextForm(
      context,
      title: 'Đề xuất điều chỉnh',
      fields: {'reason': 'Lý do và nội dung thay đổi'},
    );
    if (reason == null || !mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TreatmentTemplateEditor(
          initialPlan: current,
          onSubmit: (plan) async {
            await context.read<TreatmentProvider>().write('$_base/changes', {
              'expectedVersion': course.version,
              'reason': reason['reason'],
              'plan': plan,
            });
          },
        ),
      ),
    );
    if (mounted) _reload();
  }

  Future<void> _result(TreatmentSession session) async {
    final data = await treatmentTextForm(
      context,
      title: 'Kết quả buổi ${session.sequence}',
      fields: {
        'result': 'Kết quả thực hiện',
        'aftercare': 'Hướng dẫn sau buổi',
        'internalNotes': 'Ghi chú nội bộ (khách không xem)',
      },
      optional: {'aftercare', 'internalNotes'},
    );
    if (data == null || !mounted) return;
    final share = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Chia sẻ kết quả với khách?'),
        content: const Text(
          'Kết quả và hướng dẫn sẽ hiển thị trong lộ trình của khách. Ghi chú nội bộ chỉ dành cho người có quyền.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Hủy'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Lưu nội bộ'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Lưu và chia sẻ'),
          ),
        ],
      ),
    );
    if (share != null && mounted) {
      await _write('$_base/sessions/${session.id}/result', {
        ...data,
        'sharedWithCustomer': share,
      });
    }
  }

  Future<void> _request(TreatmentEnrollment course, String type) async {
    final data = await treatmentTextForm(
      context,
      title: 'Yêu cầu ${treatmentStatus(type).toLowerCase()}',
      fields: {
        'reason': 'Lý do',
        if (type == 'EXTEND') 'extensionDays': 'Số ngày gia hạn',
      },
    );
    if (data == null || !mounted) return;
    final days = int.tryParse(data['extensionDays'] ?? '');
    if (type == 'EXTEND' && (days == null || days < 1 || days > 3650)) {
      setState(() => _error = 'Số ngày gia hạn phải từ 1 đến 3650.');
      return;
    }
    await _write('/api/v1/spa/treatments/${course.id}/requests', {
      'type': type,
      'reason': data['reason'],
      'extensionDays': ?days,
    });
  }

  Future<void> _review(JsonMap item, String kind, bool approve) async {
    final data = await treatmentTextForm(
      context,
      title: approve ? 'Duyệt yêu cầu' : 'Từ chối yêu cầu',
      fields: {'reason': 'Lý do quyết định'},
    );
    if (data != null && mounted) {
      await _write(
        '/api/v1/admin/spa/treatments/${widget.id}/$kind/${item['id']}/${approve ? 'approve' : 'reject'}',
        data,
      );
    }
  }

  Future<void> _accept(JsonMap change, bool accept) async {
    if (await showDialog<bool>(
              context: context,
              builder: (context) => AlertDialog(
                title: Text(
                  accept ? 'Đồng ý kế hoạch mới?' : 'Từ chối thay đổi?',
                ),
                content: Text(
                  accept
                      ? 'Bạn xác nhận đã xem kế hoạch cũ, kế hoạch đề xuất, giá và điều khoản bên dưới. Thay đổi chỉ có hiệu lực sau khi đủ điều kiện thanh toán và xử lý lịch đang mở.'
                      : 'Kế hoạch hiện hành được giữ nguyên. Nhân viên sẽ nhận được quyết định của bạn.',
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Quay lại'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('Xác nhận'),
                  ),
                ],
              ),
            ) !=
            true ||
        !mounted) {
      return;
    }
    await _write(
      '/api/v1/spa/treatments/${widget.id}/changes/${change['id']}/${accept ? 'accept' : 'reject'}',
      {'expectedVersion': change['expectedVersion']},
    );
  }

  Future<void> _settle(JsonMap change) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await context.read<TreatmentProvider>().write(
        '/api/v1/spa/treatments/${widget.id}/changes/${change['id']}/settlement',
        {},
      );
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => VietQrPaymentScreen(
            order: OrderResponse.fromJson(treatmentMap(result)),
          ),
        ),
      );
      if (mounted) {
        setState(() {
          _busy = false;
          _load();
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = '$error';
        });
      }
    }
  }

  Future<void> _refund(JsonMap request) async {
    final data = await treatmentTextForm(
      context,
      title: 'Xác nhận khoản hoàn đã thực hiện',
      fields: {
        'amount': 'Số tiền đã hoàn (VND)',
        'reference': 'Mã giao dịch hoàn tiền',
        'reason': 'Ghi chú đối soát',
      },
    );
    if (data == null || !mounted) return;
    final amount = num.tryParse(data['amount'] ?? '');
    if (amount == null || amount <= 0) {
      setState(() => _error = 'Số tiền hoàn phải lớn hơn 0.');
      return;
    }
    await _write(
      '/api/v1/admin/spa/treatments/${widget.id}/requests/${request['id']}/refund-confirmation',
      {...data, 'amount': amount},
    );
  }

  @override
  Widget build(BuildContext context) {
    context.watch<AuthProvider>();
    final provider = context.read<TreatmentProvider>();
    if (_generation != provider.generation || !provider.canUse(widget.area)) {
      _future = null;
      return Scaffold(
        appBar: AppBar(),
        body: const Center(
          child: Text('Phiên đăng nhập đã thay đổi. Vui lòng mở lại hồ sơ.'),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: Text('Hồ sơ lộ trình #${widget.id}'),
        actions: [
          IconButton(
            onPressed: _busy ? null : _reload,
            icon: const Icon(Icons.refresh),
            tooltip: 'Cập nhật',
          ),
        ],
      ),
      body: Column(
        children: [
          if (_busy) const LinearProgressIndicator(),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.all(12),
              child: Text(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          Expanded(
            child: TreatmentAsync(
              future: _future,
              retry: _reload,
              builder: (course) => RefreshIndicator(
                onRefresh: () async {
                  _reload();
                  await _future;
                },
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Text(
                      course.name,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    Wrap(
                      spacing: 8,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        TreatmentStatusChip(course.status),
                        Text('Kế hoạch phiên bản ${course.version}'),
                      ],
                    ),
                    if (_operations) Text(course.customerName),
                    if (_assignmentError != null) Text(_assignmentError!),
                    Text(
                      'Đã hoàn thành ${course.completedSessions}/${course.totalSessions} buổi',
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      child: LinearProgressIndicator(value: course.progress),
                    ),
                    Text('Hạn sử dụng: ${treatmentDate(course.expiryDate)}'),
                    const Text(
                      'Tiến độ tính từ buổi điều trị hoàn tất. Vắng hẹn hoặc trừ lượt chưa phải hoàn thành điều trị.',
                    ),
                    if (course.raw['assignedStaffId'] != null)
                      Text(
                        'Người phụ trách: #${course.raw['assignedStaffId']}',
                      ),
                    if (_clinical(course) && course.raw['intakeNotes'] != null)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Tiếp nhận (nội bộ)'),
                        subtitle: Text('${course.raw['intakeNotes']}'),
                      ),
                    Wrap(
                      spacing: 8,
                      children: [
                        if (_operations &&
                            provider.canManageReception &&
                            course.allows('ASSIGN'))
                          OutlinedButton(
                            onPressed: _busy ? null : _assign,
                            child: const Text('Phân công phụ trách'),
                          ),
                        if (_clinical(course) && course.allows('INTAKE'))
                          FilledButton(
                            onPressed: _busy ? null : _intake,
                            child: const Text('Tiếp nhận'),
                          ),
                        if (_clinical(course) &&
                            course.allows('PROPOSE_CHANGE'))
                          OutlinedButton(
                            onPressed: _busy ? null : () => _propose(course),
                            child: const Text('Đề xuất điều chỉnh'),
                          ),
                        if (_clinical(course) && course.allows('COMPLETE'))
                          FilledButton(
                            onPressed: _busy
                                ? null
                                : () async {
                                    final data = await treatmentTextForm(
                                      context,
                                      title: 'Tổng kết và kết thúc lộ trình',
                                      fields: {
                                        'summary':
                                            'Tổng kết được chia sẻ với khách',
                                      },
                                    );
                                    if (data != null && mounted) {
                                      await _write('$_base/complete', data);
                                    }
                                  },
                            child: const Text('Tổng kết lộ trình'),
                          ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'Các buổi điều trị',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    ...course.sessions.map(
                      (session) => Card(
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Buổi ${session.sequence}: ${session.title}',
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              TreatmentStatusChip(session.status),
                              ...session.items.map(
                                (item) => Padding(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 3,
                                  ),
                                  child: Text(
                                    '${item.name} • ${treatmentStatus(item.status)}${item.required ? ' • Bắt buộc' : ''}',
                                  ),
                                ),
                              ),
                              if (session.earliestBookingAt != null)
                                Text(
                                  'Sớm nhất: ${treatmentDate(session.earliestBookingAt)}',
                                ),
                              if (session.raw['latestBookingAt'] != null)
                                Text(
                                  'Muộn nhất: ${treatmentDate('${session.raw['latestBookingAt']}')}',
                                ),
                              if (session.blockedReason.isNotEmpty)
                                Text(session.blockedReason),
                              if (_shared(course) && session.result.isNotEmpty)
                                ListTile(
                                  contentPadding: EdgeInsets.zero,
                                  title: const Text('Kết quả'),
                                  subtitle: Text(session.result),
                                ),
                              if (_shared(course) &&
                                  session.aftercare.isNotEmpty)
                                ListTile(
                                  contentPadding: EdgeInsets.zero,
                                  title: const Text('Hướng dẫn sau buổi'),
                                  subtitle: Text(session.aftercare),
                                ),
                              if (_clinical(course) &&
                                  session.raw['internalNotes'] != null)
                                Text('Nội bộ: ${session.raw['internalNotes']}'),
                              if (_shared(course) &&
                                  treatmentMaps(
                                    session.raw['results'],
                                  ).isNotEmpty)
                                ExpansionTile(
                                  tilePadding: EdgeInsets.zero,
                                  title: const Text('Lịch sử kết quả buổi'),
                                  children: treatmentMaps(session.raw['results'])
                                      .map(
                                        (record) => ListTile(
                                          title: Text(
                                            '${record['result'] ?? ''}',
                                          ),
                                          subtitle: Text(
                                            '${record['aftercare'] ?? ''}\n${treatmentDate('${record['createdAt']}')}'
                                            '${_clinical(course) && record['internalNotes'] != null ? '\nNội bộ: ${record['internalNotes']}' : ''}',
                                          ),
                                        ),
                                      )
                                      .toList(),
                                ),
                              Wrap(
                                spacing: 8,
                                children: [
                                  if ((!_operations ||
                                          provider.canManageReception) &&
                                      session.allows('BOOK'))
                                    FilledButton.icon(
                                      onPressed: _busy
                                          ? null
                                          : () async {
                                              await Navigator.push(
                                                context,
                                                MaterialPageRoute(
                                                  builder: (_) =>
                                                      TreatmentBookingScreen(
                                                        course: course,
                                                        session: session,
                                                      ),
                                                ),
                                              );
                                              if (mounted) _reload();
                                            },
                                      icon: const Icon(Icons.calendar_month),
                                      label: const Text('Đặt buổi'),
                                    ),
                                  if (_clinical(course) &&
                                      session.allows('WRITE_RESULT'))
                                    OutlinedButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _result(session),
                                      child: const Text(
                                        'Ghi kết quả / hướng dẫn',
                                      ),
                                    ),
                                  ...session.appointmentIds.map(
                                    (id) => OutlinedButton.icon(
                                      onPressed: () async {
                                        await Navigator.push(
                                          context,
                                          MaterialPageRoute(
                                            builder: (_) =>
                                                TreatmentAppointmentScreen(
                                                  courseId: course.id,
                                                  sessionId: session.id,
                                                  id: id,
                                                  operate: _operations,
                                                ),
                                          ),
                                        );
                                        if (mounted) _reload();
                                      },
                                      icon: const Icon(Icons.event_note),
                                      label: Text(
                                        'Lịch #$id${_operations ? ' / Thực hiện' : ' / Chuẩn bị'}',
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    if (_shared(course) && course.summary.isNotEmpty)
                      Card(
                        child: ListTile(
                          title: const Text('Tổng kết lộ trình'),
                          subtitle: Text(course.summary),
                        ),
                      ),
                    if (_shared(course) && course.changes.isNotEmpty)
                      Text(
                        'Điều chỉnh kế hoạch',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ...(_shared(course) ? course.changes : <JsonMap>[]).map(
                      (change) => Card(
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Đề xuất #${change['id']} • ${treatmentStatus('${change['status']}')}',
                              ),
                              Text('${change['reason'] ?? ''}'),
                              Text(
                                'Chênh lệch: ${HasakiFormatters.formatCurrency(double.tryParse('${change['priceDifference']}') ?? 0)}',
                              ),
                              ExpansionTile(
                                tilePadding: EdgeInsets.zero,
                                title: const Text(
                                  'So sánh kế hoạch và điều khoản',
                                ),
                                children: [
                                  _PlanSummary(
                                    title: 'Kế hoạch hiện hành',
                                    plan: treatmentMap(change['oldPlan']),
                                  ),
                                  _PlanSummary(
                                    title: 'Kế hoạch đề xuất',
                                    plan: treatmentMap(change['plan']),
                                  ),
                                ],
                              ),
                              Wrap(
                                spacing: 8,
                                children: [
                                  if (widget.area == TreatmentArea.customer &&
                                      change['status'] ==
                                          'AWAITING_CUSTOMER') ...[
                                    FilledButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _accept(change, true),
                                      child: const Text('Đồng ý'),
                                    ),
                                    OutlinedButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _accept(change, false),
                                      child: const Text('Từ chối'),
                                    ),
                                  ],
                                  if (widget.area == TreatmentArea.admin &&
                                      change['status'] ==
                                          'PENDING_APPROVAL') ...[
                                    FilledButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _review(
                                              change,
                                              'changes',
                                              true,
                                            ),
                                      child: const Text('Duyệt đề xuất'),
                                    ),
                                    OutlinedButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _review(
                                              change,
                                              'changes',
                                              false,
                                            ),
                                      child: const Text('Từ chối'),
                                    ),
                                  ],
                                  if (change['status'] ==
                                      'AWAITING_SETTLEMENT') ...[
                                    if (widget.area == TreatmentArea.customer)
                                      FilledButton(
                                        onPressed: _busy
                                            ? null
                                            : () => _settle(change),
                                        child: const Text(
                                          'Thanh toán chênh lệch',
                                        ),
                                      ),
                                    OutlinedButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _write(
                                              '/api/v1/spa/treatments/${course.id}/changes/${change['id']}/apply',
                                              {},
                                            ),
                                      child: const Text('Áp dụng sau đối soát'),
                                    ),
                                  ],
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'Yêu cầu hỗ trợ',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    Wrap(
                      spacing: 8,
                      children: [
                        for (final type in [
                          'PAUSE',
                          'RESUME',
                          'EXTEND',
                          'CANCEL',
                        ])
                          if (course.allows('REQUEST_$type'))
                            OutlinedButton(
                              onPressed: _busy
                                  ? null
                                  : () => _request(course, type),
                              child: Text(treatmentStatus(type)),
                            ),
                        if (course.allows('REQUEST_INCIDENT'))
                          OutlinedButton(
                            onPressed: _busy
                                ? null
                                : () => _request(course, 'INCIDENT'),
                            child: const Text('Ghi nhận sự cố'),
                          ),
                      ],
                    ),
                    const Text(
                      'Gửi yêu cầu chưa làm thay đổi trạng thái. Khoản hoàn chỉ được ghi nhận khi đã đối soát giao dịch thực tế.',
                    ),
                    ...(_shared(course) ? course.requests : <JsonMap>[]).map(
                      (request) => Card(
                        child: ListTile(
                          title: Text(
                            '${treatmentStatus('${request['type']}')} • ${treatmentStatus('${request['status']}')}',
                          ),
                          subtitle: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('${request['reason'] ?? ''}'),
                              if (request['extensionDays'] != null)
                                Text(
                                  'Gia hạn: ${request['extensionDays']} ngày',
                                ),
                              if (request['refundAmount'] != null)
                                Text(
                                  'Khoản hoàn: ${HasakiFormatters.formatCurrency(double.tryParse('${request['refundAmount']}') ?? 0)}',
                                ),
                              if (request['refundReference'] != null)
                                Text(
                                  'Giao dịch: ${request['refundReference']}',
                                ),
                              if (request['reviewReason'] != null)
                                Text('Phản hồi: ${request['reviewReason']}'),
                              if (widget.area == TreatmentArea.admin &&
                                  request['status'] == 'PENDING_APPROVAL')
                                Wrap(
                                  spacing: 8,
                                  children: [
                                    FilledButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _review(
                                              request,
                                              'requests',
                                              true,
                                            ),
                                      child: const Text('Duyệt'),
                                    ),
                                    OutlinedButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _review(
                                              request,
                                              'requests',
                                              false,
                                            ),
                                      child: const Text('Từ chối'),
                                    ),
                                  ],
                                ),
                              if (widget.area == TreatmentArea.admin &&
                                  request['status'] == 'AWAITING_REFUND')
                                OutlinedButton(
                                  onPressed: _busy
                                      ? null
                                      : () => _refund(request),
                                  child: const Text('Đối soát khoản đã hoàn'),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    if (_clinical(course) && course.audit.isNotEmpty)
                      ExpansionTile(
                        title: const Text('Lịch sử thao tác'),
                        children: course.audit
                            .map(
                              (entry) => ListTile(
                                title: Text('${entry['action']}'),
                                subtitle: Text(
                                  '${entry['details'] ?? ''}\n${treatmentDate('${entry['createdAt']}')} • Người thao tác #${entry['actorId']}',
                                ),
                              ),
                            )
                            .toList(),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PlanSummary extends StatelessWidget {
  const _PlanSummary({required this.title, required this.plan});
  final String title;
  final JsonMap plan;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.titleMedium),
        Text('${plan['name'] ?? ''}'),
        Text('${plan['description'] ?? ''}'),
        Text(
          '${HasakiFormatters.formatCurrency(double.tryParse('${plan['price']}') ?? 0)} • ${plan['validityDays'] ?? 'Không giới hạn'} ngày',
        ),
        ...treatmentMaps(plan['sessions']).map(
          (session) => Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text(
              'Buổi ${session['sequence']}: ${session['title']} (${session['required'] == false ? 'Tùy chọn' : 'Bắt buộc'})\n'
              '${treatmentMaps(session['items']).map((item) => '${item['serviceName'] ?? 'Dịch vụ #${item['serviceId']}'}${item['required'] == false ? ' (tùy chọn)' : ''}').join(', ')}\n'
              'Khoảng cách: ${session['minGapDays'] ?? 0}–${session['maxGapDays'] ?? 'không giới hạn'} ngày',
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text('Điều khoản: ${plan['terms'] ?? ''}'),
      ],
    ),
  );
}
