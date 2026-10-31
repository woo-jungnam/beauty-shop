import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../data/models/treatment_model.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/treatment_provider.dart';
import 'treatment_widgets.dart';

class TreatmentTemplateEditor extends StatefulWidget {
  const TreatmentTemplateEditor({
    super.key,
    this.template,
    this.initialPlan,
    this.onSubmit,
  });
  final TreatmentTemplate? template;
  final JsonMap? initialPlan;
  final Future<void> Function(JsonMap)? onSubmit;
  @override
  State<TreatmentTemplateEditor> createState() =>
      _TreatmentTemplateEditorState();
}

class _TreatmentTemplateEditorState extends State<TreatmentTemplateEditor> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController(),
      _description = TextEditingController(),
      _price = TextEditingController(),
      _validity = TextEditingController(),
      _terms = TextEditingController();
  List<JsonMap> _sessions = [];
  int? _serviceId;
  Future<List<JsonMap>>? _services;
  bool _busy = false;
  late final int _generation;
  String? _error;
  late final bool _revision = widget.onSubmit != null;
  late final bool _newVersion =
      widget.template?.allowedActions.contains('CREATE_VERSION') == true;
  @override
  void initState() {
    super.initState();
    _generation = context.read<TreatmentProvider>().generation;
    final plan = widget.initialPlan ?? widget.template?.command ?? {};
    _name.text = plan['name']?.toString() ?? '';
    _description.text = plan['description']?.toString() ?? '';
    _price.text = plan['price']?.toString() ?? '';
    _validity.text = plan.isEmpty
        ? '90'
        : plan['validityDays']?.toString() ?? '';
    _terms.text = plan['terms']?.toString() ?? '';
    _serviceId = plan['serviceId'] == null
        ? null
        : treatmentInt(plan['serviceId']);
    _sessions = treatmentMaps(jsonDecode(jsonEncode(plan['sessions'] ?? [])));
    if (_sessions.isEmpty) _addSession();
    _loadServices();
  }

  void _loadServices() {
    _services = context.read<TreatmentProvider>().services();
  }

  void _addSession() => _sessions.add({
    'sequence': _sessions.length + 1,
    'title': '',
    'minGapDays': 0,
    'maxGapDays': null,
    'required': true,
    'items': <JsonMap>[
      {'serviceId': null, 'required': true},
    ],
  });
  @override
  void dispose() {
    for (final controller in [_name, _description, _price, _validity, _terms]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _save({bool publish = false}) async {
    if (_generation != context.read<TreatmentProvider>().generation) return;
    if (!_formKey.currentState!.validate()) return;
    final plan = <String, dynamic>{
      'name': _name.text.trim(),
      'description': _description.text.trim(),
      'serviceId': _serviceId,
      'price': num.parse(_price.text),
      'validityDays': int.tryParse(_validity.text),
      'terms': _terms.text.trim(),
      'sessions': _sessions
          .asMap()
          .entries
          .map((entry) => {...entry.value, 'sequence': entry.key + 1})
          .toList(),
    };
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      if (_revision) {
        await widget.onSubmit!(plan);
      } else {
        final template = widget.template;
        await context.read<TreatmentProvider>().write(
          template == null
              ? '/api/v1/admin/spa/treatment-templates'
              : '/api/v1/admin/spa/treatment-templates/${template.id}${_newVersion ? '/versions' : ''}',
          plan,
          put: template != null && !_newVersion,
        );
        if (publish && mounted && template != null) {
          await context.read<TreatmentProvider>().write(
            '/api/v1/admin/spa/treatment-templates/${template.id}/publish',
            {},
          );
        }
      }
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = '$error';
        });
      }
    }
  }

  Future<void> _templateAction(String action) async {
    if (_generation != context.read<TreatmentProvider>().generation) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await context.read<TreatmentProvider>().write(
        '/api/v1/admin/spa/treatment-templates/${widget.template!.id}/${action.toLowerCase()}',
        {},
      );
      if (mounted) Navigator.pop(context, true);
    } catch (error) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = '$error';
        });
      }
    }
  }

  String? _required(String? value) =>
      value == null || value.trim().isEmpty ? 'Vui lòng nhập nội dung' : null;
  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    if (_generation != context.read<TreatmentProvider>().generation ||
        !auth.isAuthenticated ||
        (!_revision && auth.currentUser?.isAdmin != true)) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Mẫu lộ trình'),
        ),
        body: const Center(child: Text('Bạn không có quyền chỉnh sửa mẫu lộ trình.')),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: Text(
          _revision
              ? 'Đề xuất kế hoạch mới'
              : _newVersion
              ? 'Tạo phiên bản mới'
              : 'Soạn mẫu lộ trình',
        ),
      ),
      body: TreatmentAsync(
        future: _services,
        retry: () => setState(_loadServices),
        builder: (services) {
          final courses = services
              .where((value) => value['serviceType'] == 'TREATMENT_COURSE')
              .toList();
          final singles = services
              .where((value) => value['serviceType'] != 'TREATMENT_COURSE')
              .toList();
          return AbsorbPointer(
            absorbing: _busy,
            child: Form(
              key: _formKey,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (widget.template != null)
                    Text(
                      'Mẫu #${widget.template!.id} • Phiên bản ${widget.template!.versionNumber} • ${treatmentStatus(widget.template!.status)}',
                    ),
                  if (_newVersion && !_revision)
                    const Text(
                      'Bản đang bán được giữ nguyên. Lưu để tạo bản nháp mới, sau đó mở lại để xuất bản.',
                    ),
                  TextFormField(
                    controller: _name,
                    decoration: const InputDecoration(
                      labelText: 'Tên lộ trình',
                    ),
                    validator: _required,
                    maxLength: 150,
                  ),
                  TextFormField(
                    controller: _description,
                    decoration: const InputDecoration(labelText: 'Mô tả'),
                    maxLines: 3,
                  ),
                  DropdownButtonFormField<int>(
                    key: ValueKey('course-$_serviceId-${courses.length}'),
                    initialValue:
                        courses.any(
                          (value) => treatmentInt(value['id']) == _serviceId,
                        )
                        ? _serviceId
                        : null,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Dịch vụ loại điều trị theo lộ trình',
                    ),
                    items: courses
                        .map(
                          (value) => DropdownMenuItem(
                            value: treatmentInt(value['id']),
                            child: Text(
                              '${value['name']}',
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        )
                        .toList(),
                    onChanged: (value) => _serviceId = value,
                    validator: (value) => value == null
                        ? 'Chọn hoặc tạo dịch vụ loại lộ trình'
                        : null,
                  ),
                  if (!_revision)
                    TextButton.icon(
                      onPressed: () async {
                        await Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const _TreatmentServiceCatalog(),
                          ),
                        );
                        if (mounted) setState(_loadServices);
                      },
                      icon: const Icon(Icons.category_outlined),
                      label: const Text('Tạo hoặc phân loại dịch vụ'),
                    ),
                  TextFormField(
                    controller: _price,
                    decoration: const InputDecoration(
                      labelText: 'Giá toàn lộ trình (VND)',
                    ),
                    keyboardType: TextInputType.number,
                    validator: (value) => (num.tryParse(value ?? '') ?? 0) <= 0
                        ? 'Giá phải lớn hơn 0'
                        : null,
                  ),
                  TextFormField(
                    controller: _validity,
                    decoration: const InputDecoration(
                      labelText: 'Hiệu lực (ngày, bỏ trống nếu không giới hạn)',
                    ),
                    keyboardType: TextInputType.number,
                    validator: (value) =>
                        value != null &&
                            value.trim().isNotEmpty &&
                            ((int.tryParse(value) ?? 0) < 1 ||
                                (int.tryParse(value) ?? 0) > 3650)
                        ? 'Số ngày phải từ 1 đến 3650'
                        : null,
                  ),
                  TextFormField(
                    controller: _terms,
                    decoration: const InputDecoration(
                      labelText: 'Điều khoản đặt/đổi/hủy và phần chưa sử dụng',
                    ),
                    maxLines: 4,
                    validator: _required,
                  ),
                  const SizedBox(height: 16),
                  ..._sessions.asMap().entries.map((entry) {
                    final index = entry.key, session = entry.value;
                    final items = treatmentMaps(session['items']);
                    return Card(
                      key: ObjectKey(session),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    'Buổi ${index + 1}',
                                    style: Theme.of(
                                      context,
                                    ).textTheme.titleMedium,
                                  ),
                                ),
                                IconButton(
                                  onPressed: index == 0
                                      ? null
                                      : () => setState(() {
                                          _sessions.removeAt(index);
                                          _sessions.insert(index - 1, session);
                                        }),
                                  icon: const Icon(Icons.arrow_upward),
                                  tooltip: 'Đưa lên',
                                ),
                                IconButton(
                                  onPressed: _sessions.length <= 1
                                      ? null
                                      : () => setState(
                                          () => _sessions.removeAt(index),
                                        ),
                                  icon: const Icon(Icons.delete_outline),
                                  tooltip: 'Xóa buổi',
                                ),
                              ],
                            ),
                            TextFormField(
                              initialValue: session['title']?.toString(),
                              decoration: const InputDecoration(
                                labelText: 'Mục tiêu / nội dung buổi',
                              ),
                              validator: _required,
                              onChanged: (value) => session['title'] = value,
                            ),
                            Row(
                              children: [
                                Expanded(
                                  child: TextFormField(
                                    initialValue:
                                        '${session['minGapDays'] ?? 0}',
                                    keyboardType: TextInputType.number,
                                    decoration: const InputDecoration(
                                      labelText: 'Cách tối thiểu (ngày)',
                                    ),
                                    onChanged: (value) =>
                                        session['minGapDays'] = int.tryParse(
                                          value,
                                        ),
                                    validator: (value) =>
                                        (int.tryParse(value ?? '') ?? -1) < 0
                                        ? 'Phải ≥ 0'
                                        : null,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: TextFormField(
                                    initialValue:
                                        session['maxGapDays']?.toString() ?? '',
                                    keyboardType: TextInputType.number,
                                    decoration: const InputDecoration(
                                      labelText: 'Tối đa (tùy chọn)',
                                    ),
                                    onChanged: (value) =>
                                        session['maxGapDays'] = int.tryParse(
                                          value,
                                        ),
                                    validator: (value) =>
                                        value != null &&
                                            value.isNotEmpty &&
                                            (int.tryParse(value) ?? -1) <
                                                treatmentInt(
                                                  session['minGapDays'],
                                                )
                                        ? 'Phải ≥ tối thiểu'
                                        : null,
                                  ),
                                ),
                              ],
                            ),
                            SwitchListTile(
                              contentPadding: EdgeInsets.zero,
                              title: const Text('Buổi bắt buộc'),
                              value: session['required'] != false,
                              onChanged: (value) =>
                                  setState(() => session['required'] = value),
                            ),
                            ...items.asMap().entries.map((itemEntry) {
                              final item = itemEntry.value;
                              return Row(
                                children: [
                                  Expanded(
                                    child: DropdownButtonFormField<int>(
                                      initialValue:
                                          singles.any(
                                            (value) =>
                                                value['id'] ==
                                                item['serviceId'],
                                          )
                                          ? item['serviceId'] as int?
                                          : null,
                                      isExpanded: true,
                                      decoration: const InputDecoration(
                                        labelText: 'Dịch vụ thực hiện',
                                      ),
                                      items: singles
                                          .map(
                                            (value) => DropdownMenuItem(
                                              value: treatmentInt(value['id']),
                                              child: Text(
                                                '${value['name']}',
                                                overflow: TextOverflow.ellipsis,
                                              ),
                                            ),
                                          )
                                          .toList(),
                                      onChanged: (value) {
                                        item['serviceId'] = value;
                                        items[itemEntry.key] = item;
                                        session['items'] = items;
                                      },
                                      validator: (value) => value == null
                                          ? 'Chọn dịch vụ một lần'
                                          : null,
                                    ),
                                  ),
                                  Checkbox(
                                    value: item['required'] != false,
                                    onChanged: (value) => setState(() {
                                      item['required'] = value;
                                      items[itemEntry.key] = item;
                                      session['items'] = items;
                                    }),
                                  ),
                                  IconButton(
                                    onPressed: items.length <= 1
                                        ? null
                                        : () => setState(() {
                                            items.removeAt(itemEntry.key);
                                            session['items'] = items;
                                          }),
                                    icon: const Icon(Icons.close),
                                  ),
                                ],
                              );
                            }),
                            const Text('Ô chọn bên phải: mục bắt buộc.'),
                            TextButton.icon(
                              onPressed: () => setState(() {
                                items.add({
                                  'serviceId': null,
                                  'required': true,
                                });
                                session['items'] = items;
                              }),
                              icon: const Icon(Icons.add),
                              label: const Text('Thêm dịch vụ vào buổi'),
                            ),
                          ],
                        ),
                      ),
                    );
                  }),
                  OutlinedButton.icon(
                    onPressed: () => setState(_addSession),
                    icon: const Icon(Icons.add),
                    label: const Text('Thêm buổi'),
                  ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.all(8),
                      child: Text(
                        '$_error\nKiểm tra lại dữ liệu mới nhất trước khi gửi lại.',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                  FilledButton(
                    onPressed: _busy ? null : _save,
                    child: Text(
                      _busy
                          ? 'Đang gửi…'
                          : _revision
                          ? 'Gửi đề xuất để duyệt'
                          : 'Lưu bản nháp',
                    ),
                  ),
                  if (!_revision && widget.template != null) ...[
                    if (widget.template!.allowedActions.contains('PUBLISH'))
                      OutlinedButton(
                        onPressed: _busy ? null : () => _save(publish: true),
                        child: const Text('Lưu và xuất bản'),
                      ),
                    if (widget.template!.allowedActions.contains('ARCHIVE'))
                      TextButton(
                        onPressed: _busy
                            ? null
                            : () => _templateAction('archive'),
                        child: const Text('Ngừng bán mẫu'),
                      ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _TreatmentServiceCatalog extends StatefulWidget {
  const _TreatmentServiceCatalog();
  @override
  State<_TreatmentServiceCatalog> createState() =>
      _TreatmentServiceCatalogState();
}

class _TreatmentServiceCatalogState extends State<_TreatmentServiceCatalog> {
  Future<List<JsonMap>>? _future;
  String? _error;
  bool _busy = false;
  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _future = context.read<TreatmentProvider>().services();
  }

  Future<void> _edit([JsonMap? service]) async {
    final values = await treatmentTextForm(
      context,
      title: service == null ? 'Tạo dịch vụ lộ trình' : 'Chuyển loại dịch vụ',
      fields: {
        if (service == null) ...{
          'name': 'Tên dịch vụ',
          'slug': 'Slug (không dấu, duy nhất)',
          'duration': 'Thời lượng dự kiến một buổi (phút)',
        },
        'reason': 'Lý do tạo / chuyển loại',
      },
    );
    if (values == null || !mounted) return;
    if (service == null && (int.tryParse(values['duration'] ?? '') ?? 0) <= 0) {
      setState(() => _error = 'Thời lượng phải là số nguyên dương.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await context.read<TreatmentProvider>().write(
        '/api/v1/admin/spa/services${service == null ? '' : '/${service['id']}'}',
        {
          ...?service,
          if (service == null) ...{
            'name': values['name'],
            'slug': values['slug'],
            'basePrice': 0,
            'durationMinutes': int.parse(values['duration']!),
            'preparationTimeMinutes': 0,
          },
          'active': service?['isActive'] ?? true,
          'serviceType': service?['serviceType'] == 'TREATMENT_COURSE'
              ? 'SINGLE_SESSION'
              : 'TREATMENT_COURSE',
          'reason': values['reason'],
        },
        put: service != null,
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

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Phân loại dịch vụ'),
      actions: [
        IconButton(
          onPressed: _busy ? null : () => _edit(),
          icon: const Icon(Icons.add),
          tooltip: 'Tạo dịch vụ lộ trình',
        ),
      ],
    ),
    body: Column(
      children: [
        const Padding(
          padding: EdgeInsets.all(16),
          child: Text(
            'Chọn dịch vụ để chuyển giữa một lần và lộ trình. Hệ thống kiểm tra các nghĩa vụ đang có trước khi đổi loại.',
          ),
        ),
        if (_error != null)
          Padding(padding: const EdgeInsets.all(16), child: Text(_error!)),
        if (_busy) const LinearProgressIndicator(),
        Expanded(
          child: TreatmentAsync(
            future: _future,
            retry: () => setState(_load),
            builder: (services) => ListView(
              children: services
                  .map(
                    (service) => ListTile(
                      title: Text('${service['name']}'),
                      subtitle: Text(
                        service['serviceType'] == 'TREATMENT_COURSE'
                            ? 'Điều trị theo lộ trình'
                            : 'Dịch vụ một lần',
                      ),
                      trailing: const Icon(Icons.edit_outlined),
                      onTap: _busy ? null : () => _edit(service),
                    ),
                  )
                  .toList(),
            ),
          ),
        ),
      ],
    ),
  );
}
