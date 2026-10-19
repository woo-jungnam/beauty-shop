import '../../../core/network/api_client.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../data/models/treatment_model.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/treatment_provider.dart';
import 'treatment_widgets.dart';

class TreatmentAppointmentScreen extends StatefulWidget {
  const TreatmentAppointmentScreen({
    super.key,
    required this.id,
    required this.operate,
    this.courseId = 0,
    this.sessionId = 0,
  });
  final int id;
  final int courseId, sessionId;
  final bool operate;
  @override
  State<TreatmentAppointmentScreen> createState() =>
      _TreatmentAppointmentScreenState();
}

class _TreatmentAppointmentScreenState
    extends State<TreatmentAppointmentScreen> {
  Future<JsonMap>? _future;
  bool _busy = false;
  String? _error;
  late final int _generation;
  @override
  void initState() {
    super.initState();
    _generation = context.read<TreatmentProvider>().generation;
    _load();
  }

  void _load() {
    _future = _fetch();
  }

  Future<JsonMap> _fetch() async {
    final provider = context.read<TreatmentProvider>();
    if (!provider.authenticated ||
        widget.operate && !provider.canUse(TreatmentArea.staff)) {
      throw const TreatmentFailure('Bạn không có quyền vào khu vực này.');
    }
    final appointment = await provider.appointment(widget.id);
    final owner = treatmentInt(appointment['userId']) == provider.userId;
    Set<int> staffIds = {};
    String? assignmentError;
    if (widget.operate && provider.canExecuteSpa && !provider.isAdmin) {
      try {
        staffIds = await provider.currentStaffIds();
      } catch (error) {
        assignmentError = 'Chưa xác minh được phân công: $error';
      }
    }
    final assignedItems = treatmentMaps(appointment['items'])
        .where((item) => staffIds.contains(treatmentInt(item['staffId'])))
        .map((item) => treatmentInt(item['id']))
        .toSet();
    final clinical =
        widget.operate &&
        (provider.isAdmin ||
            provider.canExecuteSpa && assignedItems.isNotEmpty);
    final customerView = !widget.operate && owner;
    List<JsonMap> preparation = [], instructions = [];
    String? preparationError, instructionError;
    if (customerView || clinical) {
      try {
        preparation = await provider.preparation(widget.id);
        if (widget.operate && !provider.isAdmin) {
          preparation = preparation
              .where(
                (row) => assignedItems.contains(
                  treatmentInt(row['appointmentItemId']),
                ),
              )
              .toList();
        }
      } catch (error) {
        preparationError = '$error';
      }
      try {
        instructions = await provider.instructions(widget.id);
      } catch (error) {
        instructionError = '$error';
      }
    }
    JsonMap? finance;
    String? financeError;
    if (widget.courseId == 0 && (owner || provider.canManageReception)) {
      try {
        final response = await ApiClient().get<dynamic>(
          '/api/v1/spa/appointments/${widget.id}/finance',
        );
        finance = treatmentMap(response.data);
      } catch (error) {
        financeError = '$error';
      }
    }
    JsonMap? care;
    String? careError;
    if (clinical) {
      try {
        care = await provider.careSummary(widget.id);
      } catch (error) {
        careError = '$error';
      }
    }
    return {
      'clinical': clinical,
      'customerView': customerView,
      'assignedItems': assignedItems,
      'assignmentError': assignmentError,
      'finance': finance,
      'financeError': financeError,
      'appointment': appointment,
      'preparation': preparation,
      'preparationError': preparationError,
      'instructions': instructions,
      'instructionError': instructionError,
      'care': care,
      'careError': careError,
    };
  }

  void _reload() => setState(_load);
  Future<void> _write(String path, JsonMap data, {bool put = false}) async {
    if (_generation != context.read<TreatmentProvider>().generation) return;
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await context.read<TreatmentProvider>().write(path, data, put: put);
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
          _error = '$error\nDữ liệu đang được tải lại từ hệ thống.';
          _load();
        });
      }
    }
  }

  Future<void> _status(String status) async {
    JsonMap body = {'status': status};
    if (status == 'NO_SHOW') {
      final reason = await treatmentTextForm(
        context,
        title: 'Ghi nhận vắng hẹn',
        fields: {'notes': 'Lý do và kết quả liên hệ khách'},
      );
      if (reason == null || !mounted) return;
      body.addAll(reason);
    }
    await _write(
      '/api/v1/appointments/admin/${widget.id}/status',
      body,
      put: true,
    );
  }

  Future<void> _execute(JsonMap item, String status) async {
    final interrupted =
        status == 'SKIPPED' && item['executionStatus'] == 'IN_PROGRESS';
    final data = await treatmentTextForm(
      context,
      title: status == 'IN_PROGRESS'
          ? 'Bắt đầu dịch vụ'
          : status == 'PERFORMED'
          ? 'Xác nhận dịch vụ đã thực hiện'
          : interrupted
          ? 'Dừng dịch vụ do sự cố'
          : 'Bỏ qua dịch vụ chưa thực hiện',
      fields: {
        'notes': interrupted
            ? 'Lý do dừng và sự cố đã ghi nhận'
            : status == 'SKIPPED'
            ? 'Lý do bỏ qua'
            : 'Ghi chú thực hiện',
      },
      optional: status == 'SKIPPED' ? {} : {'notes'},
    );
    if (data != null && mounted) {
      await _write(
        '/api/v1/appointments/${widget.id}/items/${item['id']}/execution',
        {'status': status, ...data},
        put: true,
      );
    }
  }

  Future<void> _cancel() async {
    final data = await treatmentTextForm(
      context,
      title: 'Hủy lịch và trả buổi về chờ đặt',
      fields: {'reason': 'Lý do hủy'},
    );
    if (data != null && mounted) {
      await _write('/api/v1/appointments/${widget.id}/cancel', data, put: true);
    }
  }

  Future<void> _reschedule() async {
    final today = DateTime.now().toUtc().add(const Duration(hours: 7));
    final date = await showDatePicker(
      context: context,
      initialDate: today,
      firstDate: DateTime(today.year, today.month, today.day),
      lastDate: today.add(const Duration(days: 3650)),
    );
    if (date == null || !mounted) return;
    final dateString =
        '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    Future<List<String>> loadSlots() async {
      if (widget.courseId > 0 && widget.sessionId > 0) {
        return context.read<TreatmentProvider>().slots(
          widget.courseId,
          widget.sessionId,
          dateString,
          appointmentId: widget.id,
        );
      }
      final response = await ApiClient().get<dynamic>(
        '/api/v1/appointments/${widget.id}/available-slots',
        queryParameters: {'date': dateString},
      );
      return (response.data as List? ?? []).map((slot) => '$slot').toList();
    }

    var slotsFuture = loadSlots();
    final time = await showDialog<String>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: Text('Giờ trống ${treatmentDate(dateString)}'),
          content: SizedBox(
            width: double.maxFinite,
            height: 280,
            child: TreatmentAsync(
              future: slotsFuture,
              retry: () => update(() {
                slotsFuture = loadSlots();
              }),
              builder: (slots) => slots.isEmpty
                  ? const Center(
                      child: Text('Không còn giờ phù hợp. Hãy chọn ngày khác.'),
                    )
                  : ListView(
                      children: slots
                          .map(
                            (slot) => ListTile(
                              title: Text(slot),
                              onTap: () => Navigator.pop(context, slot),
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
      ),
    );
    if (time == null || !mounted) return;
    final notes = await treatmentTextForm(
      context,
      title: 'Đổi lịch (giờ Việt Nam)',
      fields: {'notes': 'Lý do đổi lịch'},
    );
    if (notes != null && mounted) {
      await _write('/api/v1/appointments/${widget.id}/reschedule', {
        'appointmentDate': dateString,
        'startTime': time,
        ...notes,
      }, put: true);
    }
  }

  Future<void> _assign(JsonMap appointment) async {
    final provider = context.read<TreatmentProvider>();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final staff = await provider.staff();
      if (!mounted) return;
      setState(() => _busy = false);
      final assignments = <String, int>{};
      final items = treatmentMaps(appointment['items']);
      for (final item in items) {
        if (item['staffId'] != null) {
          assignments['${item['id']}'] = treatmentInt(item['staffId']);
        }
      }
      final formKey = GlobalKey<FormState>();
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Xác nhận và phân công từng dịch vụ'),
          content: SizedBox(
            width: double.maxFinite,
            child: SingleChildScrollView(
              child: Form(
                key: formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: items
                      .map(
                        (item) => DropdownButtonFormField<int>(
                          isExpanded: true,
                          initialValue:
                              staff.any(
                                (person) =>
                                    person['id'] ==
                                    assignments['${item['id']}'],
                              )
                              ? assignments['${item['id']}']
                              : null,
                          decoration: InputDecoration(
                            labelText: '${item['serviceName']}',
                          ),
                          items: staff
                              .map(
                                (person) => DropdownMenuItem(
                                  value: treatmentInt(person['id']),
                                  child: Text(
                                    '${person['fullName'] ?? person['name'] ?? 'Nhân viên #${person['id']}'}',
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              )
                              .toList(),
                          onChanged: (value) {
                            if (value != null) {
                              assignments['${item['id']}'] = value;
                            }
                          },
                          validator: (value) =>
                              value == null ? 'Chọn người thực hiện' : null,
                        ),
                      )
                      .toList(),
                ),
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () {
                if (formKey.currentState!.validate()) {
                  Navigator.pop(context, true);
                }
              },
              child: const Text('Xác nhận'),
            ),
          ],
        ),
      );
      if (confirmed == true && mounted) {
        await _write('/api/v1/appointments/admin/${widget.id}/status', {
          'status': 'CONFIRMED',
          'staffAssignments': assignments,
        }, put: true);
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

  Future<void> _consent(JsonMap requirement) async {
    final answers = await showDialog<JsonMap>(
      context: context,
      barrierDismissible: false,
      builder: (_) =>
          _ConsentDialog(requirement: requirement, generation: _generation),
    );
    if (answers != null && mounted) {
      await _write(
        '/api/v1/spa/appointments/${widget.id}/preparation/forms/${requirement['id']}/responses',
        {'acknowledged': true, 'answers': answers},
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    context.watch<AuthProvider>();
    final provider = context.read<TreatmentProvider>();
    if (_generation != provider.generation ||
        !provider.authenticated ||
        widget.operate && !(provider.isStaff || provider.isAdmin)) {
      _future = null;
      return Scaffold(
        appBar: AppBar(),
        body: const Center(
          child: Text('Phiên đăng nhập đã thay đổi. Vui lòng mở lại lịch.'),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: Text('Lịch điều trị #${widget.id}'),
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
              builder: (data) {
                final appointment = treatmentMap(data['appointment']);
                final preparations = treatmentMaps(data['preparation']);
                final care = treatmentMap(data['care']);
                final items = treatmentMaps(appointment['items']);
                final status = '${appointment['status']}';
                final canChange =
                    ['PENDING', 'CONFIRMED'].contains(status) &&
                    appointment['checkedInAt'] == null;
                final owner =
                    treatmentInt(appointment['userId']) == provider.userId;
                final reception = widget.operate && provider.canManageReception;
                final clinical = data['clinical'] == true;
                final customerView = data['customerView'] == true;
                final assignedItems = data['assignedItems'] as Set<int>;
                bool canPerform(JsonMap item) =>
                    clinical &&
                    (provider.isAdmin ||
                        assignedItems.contains(treatmentInt(item['id'])));
                return RefreshIndicator(
                  onRefresh: () async {
                    _reload();
                    await _future;
                  },
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      Text(
                        '${treatmentDate('${appointment['appointmentDate']}')} ${appointment['startTime']} (Việt Nam)',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      TreatmentStatusChip(status),
                      if (data['assignmentError'] != null)
                        Text('${data['assignmentError']}'),
                      if (data['financeError'] != null)
                        Text(
                          'Chưa tải được thông tin cọc/phí: ${data['financeError']}',
                        ),
                      if (data['finance'] is Map)
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text(
                                  'Cọc & phí lịch hẹn',
                                  style: TextStyle(fontWeight: FontWeight.bold),
                                ),
                                for (final entry in <String, String>{
                                  'depositRequired': 'Cọc yêu cầu',
                                  'depositCollected': 'Đã thu cọc',
                                  'appliedToInvoice': 'Đã trừ vào hóa đơn',
                                  'assessedFee':
                                      'Phí phát sinh theo chính sách',
                                  'feePaid': 'Phí đã thu',
                                  'feeDue': 'Phí còn thiếu',
                                  'refundable': 'Cọc còn có thể hoàn',
                                  'refunded': 'Đã hoàn',
                                }.entries)
                                  Text(
                                    '${entry.value}: ${(data['finance'] as Map)[entry.key] ?? 0} ₫',
                                  ),
                                const Text(
                                  'Liên hệ quầy để đối soát cọc, phí và chứng từ thanh toán.',
                                ),
                              ],
                            ),
                          ),
                        ),
                      if (widget.operate)
                        Text(
                          '${appointment['customerName'] ?? ''} • ${appointment['customerPhone'] ?? ''}',
                        ),
                      if (appointment['checkedInAt'] != null)
                        Text(
                          'Đã đến: ${treatmentDate('${appointment['checkedInAt']}')}',
                        ),
                      Wrap(
                        spacing: 8,
                        children: [
                          if (canChange && (owner || reception)) ...[
                            OutlinedButton(
                              onPressed: _busy ? null : _reschedule,
                              child: const Text('Đổi ngày giờ'),
                            ),
                            OutlinedButton(
                              onPressed: _busy ? null : _cancel,
                              child: const Text('Hủy lịch'),
                            ),
                          ],
                          if (reception &&
                              ['PENDING', 'CONFIRMED'].contains(status))
                            FilledButton(
                              onPressed: _busy
                                  ? null
                                  : () => _assign(appointment),
                              child: const Text('Xác nhận / Phân công'),
                            ),
                          if (reception &&
                              status == 'CONFIRMED' &&
                              appointment['checkedInAt'] == null)
                            FilledButton(
                              onPressed: _busy
                                  ? null
                                  : () => _write(
                                      '/api/v1/appointments/${widget.id}/check-in',
                                      {},
                                      put: true,
                                    ),
                              child: const Text('Check-in'),
                            ),
                          if ((reception || clinical) &&
                              status == 'CONFIRMED' &&
                              appointment['checkedInAt'] != null)
                            FilledButton(
                              onPressed: _busy
                                  ? null
                                  : () => _status('IN_PROGRESS'),
                              child: const Text('Bắt đầu buổi'),
                            ),
                          if (reception &&
                              status == 'CONFIRMED' &&
                              appointment['checkedInAt'] == null)
                            TextButton(
                              onPressed: _busy
                                  ? null
                                  : () => _status('NO_SHOW'),
                              child: const Text('Khách vắng hẹn'),
                            ),
                          if ((reception || clinical) &&
                              status == 'IN_PROGRESS')
                            FilledButton(
                              onPressed:
                                  _busy ||
                                      items.any(
                                        (item) => ![
                                          'PERFORMED',
                                          'SKIPPED',
                                        ].contains(item['executionStatus']),
                                      )
                                  ? null
                                  : () => _status('COMPLETED'),
                              child: const Text('Kết thúc lịch hẹn'),
                            ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      if (customerView || clinical)
                        Text(
                          'Chuẩn bị và đồng ý quy trình',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                      if (data['preparationError'] != null)
                        Text(
                          '${data['preparationError']}\nChỉ nhân viên được phân công mới xem phần chuẩn bị. Hãy phân công rồi cập nhật lịch.',
                        ),
                      if ((customerView || clinical) &&
                          preparations.isEmpty &&
                          data['preparationError'] == null)
                        const Text(
                          'Lịch này không có yêu cầu chuẩn bị đã lưu.',
                        ),
                      ...preparations.map(
                        (preparation) => Card(
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Dịch vụ #${preparation['appointmentItemId']}',
                                ),
                                if (preparation['legacyBooking'] == true)
                                  const Text(
                                    'Lịch cũ không có snapshot yêu cầu mới.',
                                  ),
                                ...treatmentMaps(preparation['forms']).map((
                                  requirement,
                                ) {
                                  final form = treatmentMap(
                                    requirement['form'],
                                  );
                                  final response = treatmentMap(
                                    requirement['latestResponse'],
                                  );
                                  return Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        '${form['title']} • Phiên bản ${form['version']}',
                                      ),
                                      Text(
                                        response.isEmpty
                                            ? 'Khách chưa xác nhận'
                                            : 'Đã xác nhận ${treatmentDate('${response['submittedAt']}')}',
                                      ),
                                      if (response.isNotEmpty)
                                        ...treatmentMaps(form['questions']).map(
                                          (question) => Text(
                                            '${question['label']}: ${treatmentMap(response['answers'])[question['key']] ?? 'Chưa trả lời'}',
                                          ),
                                        ),
                                      if (owner && !widget.operate && canChange)
                                        OutlinedButton(
                                          onPressed: _busy
                                              ? null
                                              : () => _consent(requirement),
                                          child: Text(
                                            response.isEmpty
                                                ? 'Điền và xác nhận biểu mẫu'
                                                : 'Bổ sung câu trả lời',
                                          ),
                                        ),
                                    ],
                                  );
                                }),
                                if (clinical &&
                                    preparation['warningsRequired'] ==
                                        true) ...[
                                  const Text(
                                    'Người bắt đầu dịch vụ phải đọc và xác nhận cảnh báo hiện hành.',
                                  ),
                                  if (care.isNotEmpty)
                                    OutlinedButton(
                                      onPressed: _busy
                                          ? null
                                          : () => _write(
                                              '/api/v1/spa/appointments/${widget.id}/preparation/items/${preparation['appointmentItemId']}/acknowledge-warnings',
                                              {
                                                'versionHash':
                                                    care['versionHash'],
                                              },
                                            ),
                                      child: const Text(
                                        'Tôi đã đọc cảnh báo bên dưới',
                                      ),
                                    ),
                                ],
                                if (widget.operate &&
                                    provider.isAdmin &&
                                    canChange)
                                  TextButton(
                                    onPressed: _busy
                                        ? null
                                        : () async {
                                            final reason =
                                                await treatmentTextForm(
                                                  context,
                                                  title:
                                                      'Duyệt ngoại lệ chuẩn bị',
                                                  fields: {
                                                    'reason': 'Lý do ngoại lệ',
                                                  },
                                                );
                                            if (reason != null && mounted) {
                                              await _write(
                                                '/api/v1/spa/appointments/${widget.id}/preparation/items/${preparation['appointmentItemId']}/override',
                                                reason,
                                              );
                                            }
                                          },
                                    child: const Text(
                                      'Duyệt ngoại lệ (lưu lý do)',
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                      if (clinical) ...[
                        Text(
                          'Cảnh báo và hồ sơ chăm sóc nội bộ',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        if (data['careError'] != null)
                          Text('${data['careError']}'),
                        ...treatmentMaps(care['notes']).map(
                          (note) => ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text(
                              '${note['type'] ?? note['category'] ?? 'Lưu ý'}',
                            ),
                            subtitle: Text(
                              '${note['content'] ?? note['note'] ?? note['description'] ?? ''}',
                            ),
                          ),
                        ),
                        if (care.isNotEmpty &&
                            treatmentMaps(care['notes']).isEmpty)
                          const Text(
                            'Không có ghi chú chăm sóc trong phiên bản hồ sơ hiện hành.',
                          ),
                      ],
                      const SizedBox(height: 12),
                      Text(
                        'Thực hiện từng dịch vụ',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      ...items.map(
                        (item) => Card(
                          child: ListTile(
                            title: Text('${item['serviceName']}'),
                            subtitle: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '${treatmentStatus('${item['executionStatus']}')} • ${item['staffName'] ?? 'Chưa phân công'}',
                                ),
                                if ((customerView || clinical) &&
                                    item['executionNotes'] != null)
                                  Text('${item['executionNotes']}'),
                                if (canPerform(item) && status == 'IN_PROGRESS')
                                  Wrap(
                                    spacing: 8,
                                    children: [
                                      if (item['executionStatus'] ==
                                          'PLANNED') ...[
                                        FilledButton(
                                          onPressed: _busy
                                              ? null
                                              : () => _execute(
                                                  item,
                                                  'IN_PROGRESS',
                                                ),
                                          child: const Text('Bắt đầu dịch vụ'),
                                        ),
                                        OutlinedButton(
                                          onPressed: _busy
                                              ? null
                                              : () => _execute(item, 'SKIPPED'),
                                          child: const Text(
                                            'Bỏ qua và trả lượt',
                                          ),
                                        ),
                                      ],
                                      if (item['executionStatus'] ==
                                          'IN_PROGRESS') ...[
                                        FilledButton(
                                          onPressed: _busy
                                              ? null
                                              : () =>
                                                    _execute(item, 'PERFORMED'),
                                          child: const Text('Đã thực hiện'),
                                        ),
                                        if (item['treatmentSessionItemId'] !=
                                            null)
                                          OutlinedButton.icon(
                                            onPressed: _busy
                                                ? null
                                                : () =>
                                                      _execute(item, 'SKIPPED'),
                                            icon: const Icon(
                                              Icons.report_problem_outlined,
                                            ),
                                            label: const Text('Dừng do sự cố'),
                                          ),
                                      ],
                                    ],
                                  ),
                                if (canPerform(item) &&
                                    item['executionStatus'] == 'PERFORMED')
                                  TextButton(
                                    onPressed: _busy
                                        ? null
                                        : () async {
                                            final content = await treatmentTextForm(
                                              context,
                                              title:
                                                  'Gửi hướng dẫn sau dịch vụ',
                                              fields: {
                                                'content':
                                                    'Nội dung dành cho khách',
                                              },
                                            );
                                            if (content != null && mounted) {
                                              await _write(
                                                '/api/v1/spa/appointments/${widget.id}/follow-up-instructions/items/${item['id']}',
                                                {
                                                  ...content,
                                                  'dueAt': DateTime.now()
                                                      .toUtc()
                                                      .toIso8601String(),
                                                },
                                              );
                                            }
                                          },
                                    child: const Text(
                                      'Ghi hướng dẫn sau dịch vụ',
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                      if (data['instructionError'] != null)
                        Text('${data['instructionError']}'),
                      if (treatmentMaps(data['instructions']).isNotEmpty)
                        Text(
                          'Hướng dẫn sau buổi',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                      ...treatmentMaps(data['instructions']).map(
                        (instruction) => ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text('${instruction['content'] ?? ''}'),
                          subtitle: Text(
                            treatmentDate('${instruction['createdAt']}'),
                          ),
                        ),
                      ),
                      if (clinical)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 16),
                          child: Text(
                            'Sau khi thực hiện và kết thúc lịch hẹn, quay lại hồ sơ lộ trình để ghi kết quả buổi. Phần đã thực hiện được lưu theo từng dịch vụ; lượt bị trừ do vắng hẹn không được tính là hoàn thành điều trị.',
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _ConsentDialog extends StatefulWidget {
  const _ConsentDialog({required this.requirement, required this.generation});
  final JsonMap requirement;
  final int generation;
  @override
  State<_ConsentDialog> createState() => _ConsentDialogState();
}

class _ConsentDialogState extends State<_ConsentDialog> {
  final _key = GlobalKey<FormState>();
  JsonMap _answers = {};
  bool _acknowledged = false;
  @override
  void initState() {
    super.initState();
    _answers = {
      ...treatmentMap(
        treatmentMap(widget.requirement['latestResponse'])['answers'],
      ),
    };
  }

  @override
  Widget build(BuildContext context) {
    context.watch<AuthProvider>();
    if (widget.generation != context.read<TreatmentProvider>().generation) {
      return AlertDialog(
        title: const Text('Phiên đăng nhập đã thay đổi'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Đóng'),
          ),
        ],
      );
    }
    final form = treatmentMap(widget.requirement['form']);
    return AlertDialog(
      title: Text('${form['title']}'),
      content: SizedBox(
        width: double.maxFinite,
        child: SingleChildScrollView(
          child: Form(
            key: _key,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ...treatmentMaps(form['questions']).map(
                  (question) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: question['type'] == 'BOOLEAN'
                        ? DropdownButtonFormField<bool>(
                            initialValue:
                                _answers['${question['key']}'] as bool?,
                            decoration: InputDecoration(
                              labelText: '${question['label']}',
                            ),
                            items: const [
                              DropdownMenuItem(value: true, child: Text('Có')),
                              DropdownMenuItem(
                                value: false,
                                child: Text('Không'),
                              ),
                            ],
                            onChanged: (value) =>
                                _answers['${question['key']}'] = value,
                            validator: (value) =>
                                question['required'] == true && value == null
                                ? 'Vui lòng trả lời'
                                : null,
                          )
                        : TextFormField(
                            initialValue: _answers['${question['key']}']
                                ?.toString(),
                            maxLines: 3,
                            maxLength: 4000,
                            decoration: InputDecoration(
                              labelText: '${question['label']}',
                            ),
                            onChanged: (value) =>
                                _answers['${question['key']}'] = value,
                            validator: (value) =>
                                question['required'] == true &&
                                    (value == null || value.trim().isEmpty)
                                ? 'Vui lòng trả lời'
                                : null,
                          ),
                  ),
                ),
                CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  value: _acknowledged,
                  onChanged: (value) => setState(() => _acknowledged = value!),
                  title: const Text(
                    'Tôi xác nhận thông tin và đồng ý nội dung biểu mẫu này.',
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: !_acknowledged
              ? null
              : () {
                  if (_key.currentState!.validate()) {
                    Navigator.pop(context, _answers);
                  }
                },
          child: const Text('Gửi xác nhận'),
        ),
      ],
    );
  }
}
