import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/treatment_model.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/treatment_provider.dart';
import '../auth/login_screen.dart';
import 'treatment_widgets.dart';
import 'treatment_template_screen.dart';
import 'treatment_template_editor.dart';
import 'treatment_enrollment_screen.dart';

class TreatmentHubScreen extends StatefulWidget {
  const TreatmentHubScreen({
    super.key,
    this.initialArea = TreatmentArea.customer,
  });
  final TreatmentArea initialArea;
  @override
  State<TreatmentHubScreen> createState() => _TreatmentHubScreenState();
}

class _TreatmentHubScreenState extends State<TreatmentHubScreen> {
  late TreatmentArea _area = widget.initialArea;
  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final provider = context.read<TreatmentProvider>();
    if (_area != TreatmentArea.customer && !provider.canUse(_area)) {
      _area = TreatmentArea.customer;
    }
    final admin = _area == TreatmentArea.admin;
    return DefaultTabController(
      key: ValueKey(
        '${_area.name}:${auth.currentUser?.id}:${auth.isAuthenticated}',
      ),
      length: admin || _area == TreatmentArea.customer ? 3 : 2,
      child: Scaffold(
        appBar: AppBar(
          title: Text(_area.label),
          actions: [
            PopupMenuButton<TreatmentArea>(
              tooltip: 'Chuyển khu vực',
              initialValue: _area,
              onSelected: (area) => setState(() => _area = area),
              itemBuilder: (_) => [
                const PopupMenuItem(
                  value: TreatmentArea.customer,
                  child: Text('Khách hàng'),
                ),
                if (auth.currentUser?.canOperateSpa == true)
                  const PopupMenuItem(
                    value: TreatmentArea.staff,
                    child: Text('Nhân viên'),
                  ),
                if (auth.currentUser?.isAdmin == true)
                  const PopupMenuItem(
                    value: TreatmentArea.admin,
                    child: Text('Quản trị Spa'),
                  ),
              ],
            ),
          ],
          bottom: TabBar(
            tabs: admin
                ? const [
                    Tab(text: 'Hồ sơ'),
                    Tab(text: 'Mẫu lộ trình'),
                    Tab(text: 'Báo cáo'),
                  ]
                : _area == TreatmentArea.customer
                ? const [
                    Tab(text: 'Khám phá'),
                    Tab(text: 'Của tôi'),
                    Tab(text: 'Thông báo'),
                  ]
                : const [Tab(text: 'Hồ sơ được giao'), Tab(text: 'Danh mục')],
          ),
        ),
        body: TabBarView(
          children: admin
              ? [
                  _EnrollmentList(area: _area),
                  const _TemplateList(admin: true),
                  const _ReportPanel(),
                ]
              : _area == TreatmentArea.customer
              ? [
                  const _TemplateList(),
                  _EnrollmentList(area: _area),
                  const _TreatmentInbox(),
                ]
              : [_EnrollmentList(area: _area), const _TemplateList()],
        ),
      ),
    );
  }
}

class _TemplateList extends StatefulWidget {
  const _TemplateList({this.admin = false});
  final bool admin;
  @override
  State<_TemplateList> createState() => _TemplateListState();
}

class _TemplateListState extends State<_TemplateList> {
  Future<List<TreatmentTemplate>>? _future;
  int? _generation;
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    context.watch<AuthProvider>();
    final generation = context.read<TreatmentProvider>().generation;
    if (_generation != generation) {
      _generation = generation;
      _load();
    }
  }

  void _load() {
    _future = context.read<TreatmentProvider>().templates(admin: widget.admin);
  }

  void _reload() => setState(_load);
  @override
  Widget build(BuildContext context) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Row(
          children: [
            const Expanded(
              child: Text('Điều trị nhiều buổi theo kế hoạch riêng.'),
            ),
            IconButton(
              onPressed: _reload,
              icon: const Icon(Icons.refresh),
              tooltip: 'Cập nhật',
            ),
            if (widget.admin)
              IconButton(
                onPressed: () async {
                  await Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const TreatmentTemplateEditor(),
                    ),
                  );
                  if (mounted) _reload();
                },
                icon: const Icon(Icons.add),
                tooltip: 'Tạo mẫu lộ trình',
              ),
          ],
        ),
      ),
      Expanded(
        child: TreatmentAsync(
          future: _future,
          retry: _reload,
          builder: (templates) => RefreshIndicator(
            onRefresh: () async {
              _reload();
              await _future;
            },
            child: templates.isEmpty
                ? const TreatmentEmpty('Chưa có mẫu lộ trình phù hợp.')
                : ListView.builder(
                    itemCount: templates.length,
                    itemBuilder: (context, index) {
                      final template = templates[index];
                      return Card(
                        margin: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        child: ListTile(
                          title: Text(template.name),
                          subtitle: Text(
                            '${template.sessions.length} buổi • ${template.validityDays == null ? 'Không giới hạn' : '${template.validityDays} ngày'}\n'
                            '${AppFormatters.formatCurrency(template.price)} • Phiên bản ${template.versionNumber}',
                          ),
                          isThreeLine: true,
                          trailing: widget.admin
                              ? TreatmentStatusChip(template.status)
                              : const Icon(Icons.chevron_right),
                          onTap: () async {
                            await Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => widget.admin
                                    ? TreatmentTemplateEditor(
                                        template: template,
                                      )
                                    : TreatmentTemplateScreen(id: template.id),
                              ),
                            );
                            if (mounted) _reload();
                          },
                        ),
                      );
                    },
                  ),
          ),
        ),
      ),
    ],
  );
}

class _EnrollmentList extends StatefulWidget {
  const _EnrollmentList({required this.area});
  final TreatmentArea area;
  @override
  State<_EnrollmentList> createState() => _EnrollmentListState();
}

class _EnrollmentListState extends State<_EnrollmentList> {
  Future<List<TreatmentEnrollment>>? _future;
  int? _generation;
  String _status = 'ALL';
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    context.watch<AuthProvider>();
    final provider = context.read<TreatmentProvider>();
    if (_generation != provider.generation) {
      _generation = provider.generation;
      _future = provider.canUse(widget.area)
          ? provider.enrollments(widget.area)
          : null;
    }
  }

  void _reload() => setState(() {
    _future = context.read<TreatmentProvider>().enrollments(widget.area);
  });
  @override
  Widget build(BuildContext context) {
    if (!context.read<TreatmentProvider>().canUse(widget.area)) {
      return Center(
        child: FilledButton(
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const LoginScreen()),
          ),
          child: const Text('Đăng nhập để xem lộ trình'),
        ),
      );
    }
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              Expanded(
                child: DropdownButton<String>(
                  isExpanded: true,
                  value: _status,
                  items:
                      [
                            'ALL',
                            'PENDING_INTAKE',
                            'ACTIVE',
                            'PAUSED',
                            'PENDING_REVIEW',
                            'COMPLETED',
                            'CANCELLED',
                            'EXPIRED',
                          ]
                          .map(
                            (status) => DropdownMenuItem(
                              value: status,
                              child: Text(
                                status == 'ALL'
                                    ? 'Tất cả trạng thái'
                                    : treatmentStatus(status),
                              ),
                            ),
                          )
                          .toList(),
                  onChanged: (value) => setState(() => _status = value!),
                ),
              ),
              IconButton(
                onPressed: _reload,
                icon: const Icon(Icons.refresh),
                tooltip: 'Cập nhật',
              ),
            ],
          ),
        ),
        Expanded(
          child: TreatmentAsync(
            future: _future,
            retry: _reload,
            builder: (allCourses) {
              final courses = allCourses
                  .where(
                    (course) => _status == 'ALL' || course.status == _status,
                  )
                  .toList();
              return RefreshIndicator(
                onRefresh: () async {
                  _reload();
                  await _future;
                },
                child: courses.isEmpty
                    ? const TreatmentEmpty(
                        'Chưa có hồ sơ lộ trình ở trạng thái này. Đơn chưa thanh toán chưa cấp hồ sơ.',
                      )
                    : ListView.builder(
                        itemCount: courses.length,
                        itemBuilder: (context, index) {
                          final course = courses[index];
                          return Card(
                            margin: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 6,
                            ),
                            child: ListTile(
                              title: Text(course.name),
                              subtitle: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if (widget.area != TreatmentArea.customer)
                                    Text(course.customerName),
                                  Text(
                                    '${course.completedSessions}/${course.totalSessions} buổi • ${treatmentStatus(course.status)}',
                                  ),
                                  const SizedBox(height: 6),
                                  LinearProgressIndicator(
                                    value: course.progress,
                                  ),
                                ],
                              ),
                              trailing: const Icon(Icons.chevron_right),
                              onTap: () async {
                                await Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) => TreatmentEnrollmentScreen(
                                      id: course.id,
                                      area: widget.area,
                                    ),
                                  ),
                                );
                                if (mounted) _reload();
                              },
                            ),
                          );
                        },
                      ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _ReportPanel extends StatefulWidget {
  const _ReportPanel();
  @override
  State<_ReportPanel> createState() => _ReportPanelState();
}

class _ReportPanelState extends State<_ReportPanel> {
  Future<JsonMap>? _future;
  @override
  void initState() {
    super.initState();
    _future = context.read<TreatmentProvider>().report();
  }

  void _reload() => setState(() {
    _future = context.read<TreatmentProvider>().report();
  });
  @override
  Widget build(BuildContext context) => TreatmentAsync(
    future: _future,
    retry: _reload,
    builder: (data) => RefreshIndicator(
      onRefresh: () async {
        _reload();
        await _future;
      },
      child: ListView(
        children: [
          const ListTile(
            title: Text('Báo cáo lộ trình'),
            subtitle: Text(
              'Số liệu hiện tại từ hệ thống; kéo xuống để cập nhật.',
            ),
          ),
          ...const {
            'totalCourses': 'Tổng hồ sơ',
            'pendingIntake': 'Chờ tiếp nhận',
            'activeCourses': 'Đang điều trị',
            'pausedCourses': 'Tạm dừng',
            'completedCourses': 'Hoàn thành',
            'expiredCourses': 'Hết hạn',
            'sessionsDue': 'Buổi đến hạn',
            'customersWithoutNextBooking': 'Khách chưa đặt buổi tiếp',
            'collectedAmount': 'Đã thu',
            'refundedAmount': 'Đã hoàn',
            'unusedEntitlementAmount': 'Quyền lợi chưa dùng',
          }.entries.map(
            (entry) => ListTile(
              title: Text(entry.value),
              trailing: Text(
                entry.key.endsWith('Amount')
                    ? AppFormatters.formatCurrency(
                        double.tryParse('${data[entry.key]}') ?? 0,
                      )
                    : '${data[entry.key] ?? 0}',
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class _TreatmentInbox extends StatefulWidget {
  const _TreatmentInbox();
  @override
  State<_TreatmentInbox> createState() => _TreatmentInboxState();
}

class _TreatmentInboxState extends State<_TreatmentInbox> {
  Future<List<JsonMap>>? _future;
  int? _generation;
  String? _error;
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    context.watch<AuthProvider>();
    final provider = context.read<TreatmentProvider>();
    if (_generation != provider.generation) {
      _generation = provider.generation;
      _future = provider.authenticated ? provider.notifications() : null;
    }
  }

  void _reload() => setState(() {
    _future = context.read<TreatmentProvider>().notifications();
  });
  @override
  Widget build(BuildContext context) {
    if (!context.read<TreatmentProvider>().authenticated) {
      return const Center(child: Text('Đăng nhập để nhận thông báo lộ trình.'));
    }
    return Column(
      children: [
        if (_error != null)
          Padding(padding: const EdgeInsets.all(8), child: Text(_error!)),
        Expanded(
          child: TreatmentAsync(
            future: _future,
            retry: _reload,
            builder: (notifications) => RefreshIndicator(
              onRefresh: () async {
                _reload();
                await _future;
              },
              child: notifications.isEmpty
                  ? const TreatmentEmpty('Chưa có thông báo điều trị.')
                  : ListView(
                      children: notifications
                          .map(
                            (notification) => ListTile(
                              leading: Icon(
                                notification['readAt'] == null
                                    ? Icons.notifications_active
                                    : Icons.notifications_none,
                              ),
                              title: Text('${notification['title']}'),
                              subtitle: Text(
                                '${notification['message']}\n${treatmentDate('${notification['createdAt']}')}',
                              ),
                              isThreeLine: true,
                              onTap: () async {
                                final provider = context
                                    .read<TreatmentProvider>();
                                try {
                                  if (notification['readAt'] == null) {
                                    await provider.write(
                                      '/api/v1/spa/treatment-notifications/${notification['id']}/read',
                                      {},
                                      put: true,
                                    );
                                  }
                                  if (!context.mounted) return;
                                  await Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                      builder: (_) => TreatmentEnrollmentScreen(
                                        id: treatmentInt(
                                          notification['courseId'],
                                        ),
                                      ),
                                    ),
                                  );
                                  if (mounted) _reload();
                                } catch (error) {
                                  if (mounted) {
                                    setState(() => _error = '$error');
                                  }
                                }
                              },
                            ),
                          )
                          .toList(),
                    ),
            ),
          ),
        ),
      ],
    );
  }
}
