import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../data/models/treatment_model.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/treatment_provider.dart';
import 'treatment_widgets.dart';

class TreatmentBookingScreen extends StatefulWidget {
  const TreatmentBookingScreen({
    super.key,
    required this.course,
    required this.session,
  });
  final TreatmentEnrollment course;
  final TreatmentSession session;
  @override
  State<TreatmentBookingScreen> createState() => _TreatmentBookingScreenState();
}

class _TreatmentBookingScreenState extends State<TreatmentBookingScreen> {
  late DateTime _date = DateTime.now().toUtc().add(const Duration(hours: 7));
  Future<List<String>>? _slots;
  String? _selected, _error;
  bool _busy = false;
  final _notes = TextEditingController();
  late final int _generation;
  String get _dateString =>
      '${_date.year}-${_date.month.toString().padLeft(2, '0')}-${_date.day.toString().padLeft(2, '0')}';
  @override
  void initState() {
    super.initState();
    _generation = context.read<TreatmentProvider>().generation;
    final first = widget.session.earliestBookingAt;
    if (first != null) {
      final earliest = DateTime.tryParse(
        first,
      )?.toUtc().add(const Duration(hours: 7));
      if (earliest != null && earliest.isAfter(_date)) _date = earliest;
    }
    _load();
  }

  @override
  void dispose() {
    _notes.dispose();
    super.dispose();
  }

  void _load() {
    _selected = null;
    _slots = context.read<TreatmentProvider>().slots(
      widget.course.id,
      widget.session.id,
      _dateString,
    );
  }

  Future<void> _book() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    final provider = context.read<TreatmentProvider>();
    try {
      await provider.write(
        '/api/v1/spa/treatments/${widget.course.id}/sessions/${widget.session.id}/book',
        {
          'appointmentDate': _dateString,
          'startTime': _selected,
          'notes': _notes.text.trim(),
          if (provider.userId != widget.course.userId)
            'targetUserId': widget.course.userId,
          'items': widget.session.outstandingItems
              .map(
                (item) => {
                  'serviceId': item.serviceId,
                  'treatmentSessionItemId': item.id,
                },
              )
              .toList(),
        },
      );
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      // Resolve an ambiguous timeout from the server before another booking attempt.
      try {
        final course = await provider.enrollment(
          widget.course.id,
          provider.userId == widget.course.userId
              ? TreatmentArea.customer
              : provider.isAdmin
              ? TreatmentArea.admin
              : TreatmentArea.staff,
        );
        final current = course.sessions
            .where((session) => session.id == widget.session.id)
            .firstOrNull;
        if (current != null &&
            current.appointmentIds.isNotEmpty &&
            !current.allows('BOOK')) {
          if (mounted) Navigator.pop(context, true);
          return;
        }
      } catch (_) {
        /* User keeps the unresolved error and can explicitly refresh. */
      }
      if (mounted) {
        setState(() {
          _busy = false;
          _error = '$error';
          _load();
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    context.watch<AuthProvider>();
    if (_generation != context.read<TreatmentProvider>().generation) {
      return Scaffold(
        appBar: AppBar(),
        body: const Center(
          child: Text('Phiên đăng nhập đã thay đổi. Vui lòng mở lại lộ trình.'),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text('Đặt buổi ${widget.session.sequence}')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            widget.session.title,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          ...widget.session.outstandingItems.map(
            (item) => ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.spa_outlined),
              title: Text(item.name),
            ),
          ),
          const Text(
            'Ngày giờ theo múi giờ Việt Nam. Giờ trống chỉ được giữ sau khi máy chủ xác nhận đặt lịch.',
          ),
          if (widget.session.earliestBookingAt != null)
            Text(
              'Được đặt sớm nhất: ${treatmentDate(widget.session.earliestBookingAt)}',
            ),
          OutlinedButton.icon(
            onPressed: _busy
                ? null
                : () async {
                    final today = DateTime.now().toUtc().add(
                      const Duration(hours: 7),
                    );
                    final chosen = await showDatePicker(
                      context: context,
                      initialDate: _date,
                      firstDate: DateTime(today.year, today.month, today.day),
                      lastDate: today.add(const Duration(days: 3650)),
                    );
                    if (chosen != null && mounted) {
                      setState(() {
                        _date = chosen;
                        _load();
                      });
                    }
                  },
            icon: const Icon(Icons.calendar_month),
            label: Text(treatmentDate(_dateString)),
          ),
          SizedBox(
            height: 210,
            child: TreatmentAsync(
              future: _slots,
              retry: () => setState(_load),
              builder: (slots) => slots.isEmpty
                  ? const Center(
                      child: Text(
                        'Không có giờ phù hợp. Hãy chọn ngày khác hoặc cập nhật lại.',
                      ),
                    )
                  : SingleChildScrollView(
                      child: Wrap(
                        spacing: 8,
                        children: slots
                            .map(
                              (slot) => ChoiceChip(
                                label: Text(slot.substring(0, 5)),
                                selected: _selected == slot,
                                onSelected: _busy
                                    ? null
                                    : (_) => setState(() => _selected = slot),
                              ),
                            )
                            .toList(),
                      ),
                    ),
            ),
          ),
          TextField(
            controller: _notes,
            maxLength: 500,
            maxLines: 3,
            decoration: const InputDecoration(
              labelText: 'Ghi chú / lý do đặt hộ',
            ),
          ),
          if (_error != null)
            Text(
              '$_error\nHãy cập nhật hồ sơ trước khi gửi lại.',
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          FilledButton(
            onPressed: _selected == null || _busy ? null : _book,
            child: Text(
              _busy ? 'Đang kiểm tra và đặt…' : 'Gửi yêu cầu đặt buổi',
            ),
          ),
        ],
      ),
    );
  }
}
