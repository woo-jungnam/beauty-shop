import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/constants/api_constants.dart';
import 'package:beautyshop_mobile/core/network/api_client.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/data/models/spa_model.dart';
import 'package:beautyshop_mobile/providers/spa_provider.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/auth/login_screen.dart';
import '../../widgets/catalog_state.dart';
import '../treatments/treatment_hub_screen.dart';

class SpaBookingScreen extends StatefulWidget {
  final SpaService service;
  final int? ticketId;

  const SpaBookingScreen({Key? key, required this.service, this.ticketId})
    : super(key: key);

  @override
  State<SpaBookingScreen> createState() => _SpaBookingScreenState();
}

class _SpaBookingScreenState extends State<SpaBookingScreen> {
  DateTime _selectedDate = DateTime.now().add(const Duration(days: 1));
  String _selectedTime = '';
  final TextEditingController _noteController = TextEditingController();
  final ApiClient _apiClient = ApiClient();

  List<String> _availableSlots = [];
  bool _loadingSlots = false;
  String? _slotsError;
  int _slotsRevision = 0;
  Map<String, dynamic>? _financialPolicy;
  bool _financialAccepted = false;
  int? _ticketId;
  String? _policyError;
  bool get _requiresFinancialAcceptance => [
    'depositAmount',
    'cancellationFee',
    'noShowFee',
  ].any((key) => (num.tryParse('${_financialPolicy?[key] ?? 0}') ?? 0) > 0);

  Future<void> _loadFinancialPolicy() async {
    try {
      final response = await _apiClient.get<Map<String, dynamic>>(
        '/api/v1/spa/financial-policy',
        fromJsonT: (json) => Map<String, dynamic>.from(json as Map),
      );
      if (!mounted) return;
      if (!response.isSuccess || response.data == null)
        throw StateError('Missing policy');
      setState(() {
        _financialPolicy = response.data;
        _policyError = null;
      });
    } catch (_) {
      if (mounted)
        setState(
          () => _policyError =
              'Không tải được chính sách cọc. Thử lại trước khi đặt.',
        );
    }
  }

  @override
  void initState() {
    super.initState();
    _ticketId = widget.ticketId;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && context.read<AuthProvider>().isAuthenticated)
        context.read<SpaProvider>().fetchMyTickets();
    });
    _loadFinancialPolicy();
    if (!widget.service.isTreatmentCourse) _fetchAvailableSlots();
  }

  Future<void> _fetchAvailableSlots() async {
    final revision = ++_slotsRevision;
    setState(() {
      _loadingSlots = true;
      _slotsError = null;
      _availableSlots = [];
      _selectedTime = '';
    });
    final dateStr =
        '${_selectedDate.year.toString().padLeft(4, '0')}-${_selectedDate.month.toString().padLeft(2, '0')}-${_selectedDate.day.toString().padLeft(2, '0')}';
    try {
      final response = await _apiClient.get<List<String>>(
        '${ApiConstants.spaServices}/${widget.service.id}/available-slots',
        queryParameters: {'date': dateStr},
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list
              .map(
                (e) => e.toString().length >= 5
                    ? e.toString().substring(0, 5)
                    : e.toString(),
              )
              .toList();
        },
      );
      if (!mounted || revision != _slotsRevision) return;
      if (!response.isSuccess || response.data == null)
        throw StateError('Missing slots');
      if (response.data!.isNotEmpty) {
        setState(() {
          _availableSlots = response.data!;
          if (!_availableSlots.contains(_selectedTime)) {
            _selectedTime = _availableSlots.first;
          }
        });
      } else {
        setState(() {
          _availableSlots = [];
          _selectedTime = '';
        });
      }
    } catch (_) {
      if (!mounted || revision != _slotsRevision) return;
      setState(() {
        _slotsError = 'Không thể tải khung giờ trống. Vui lòng thử lại.';
        _availableSlots = [];
        _selectedTime = '';
      });
    } finally {
      if (mounted && revision == _slotsRevision)
        setState(() => _loadingSlots = false);
    }
  }

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final spa = context.watch<SpaProvider>();
    final eligible = spa.myTickets
        .where(
          (t) =>
              t.isValid &&
              t.benefitType != 'TREATMENT_COURSE' &&
              (t.remainingByService[widget.service.id] ?? 0) > 0,
        )
        .toList();
    final selectedTicket = eligible.any((t) => t.id == _ticketId)
        ? _ticketId
        : null;
    if (widget.service.isTreatmentCourse)
      return Scaffold(
        appBar: AppBar(title: const Text('Điều trị theo lộ trình')),
        body: Center(
          child: FilledButton(
            onPressed: () => Navigator.pushReplacement(
              context,
              MaterialPageRoute(builder: (_) => const TreatmentHubScreen()),
            ),
            child: const Text('Chọn lộ trình trước khi đặt từng buổi'),
          ),
        ),
      );
    return Scaffold(
      backgroundColor: HasakiColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Đặt Lịch Hẹn Spa & Làm Đẹp'),
        backgroundColor: HasakiColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Service Summary Card
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: HasakiColors.getSurface(context),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: HasakiColors.borderSubtle),
              ),
              child: Row(
                children: [
                  Container(
                    width: 50,
                    height: 50,
                    decoration: BoxDecoration(
                      color: HasakiColors.primaryLight,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(
                      Icons.spa,
                      color: HasakiColors.primary,
                      size: 28,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.service.name,
                          style: HasakiTypography.titleMedium.copyWith(
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          selectedTicket != null
                              ? 'Dùng lượt đã mua — không tính lại phí dịch vụ'
                              : HasakiFormatters.formatCurrency(
                                  widget.service.price,
                                ),
                          style: HasakiTypography.priceMedium,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            if (context.watch<AuthProvider>().isAuthenticated) ...[
              if (spa.loadingTickets) const LinearProgressIndicator(),
              if (spa.ticketsError != null)
                TextButton(
                  onPressed: () => spa.fetchMyTickets(),
                  child: Text('${spa.ticketsError} · Thử tải vé'),
                ),
              DropdownButtonFormField<int>(
                key: ValueKey('ticket-$selectedTicket-${eligible.length}'),
                initialValue: selectedTicket,
                decoration: const InputDecoration(
                  labelText: 'Vé sử dụng cho dịch vụ này',
                ),
                items: [
                  const DropdownMenuItem<int>(
                    value: null,
                    child: Text('Thanh toán dịch vụ lẻ tại quầy'),
                  ),
                  ...eligible.map(
                    (t) => DropdownMenuItem<int>(
                      value: t.id,
                      child: Text(
                        'Vé #${t.id} · Còn ${t.remainingByService[widget.service.id]} lượt',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                ],
                onChanged: (id) => setState(() => _ticketId = id),
              ),
              const SizedBox(height: 16),
            ],

            // Clinic Location
            _buildSectionTitle('1. Cơ sở điều trị:'),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: HasakiColors.getSurface(context),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: HasakiColors.border),
              ),
              child: const Row(
                children: [
                  Icon(
                    Icons.location_on,
                    color: HasakiColors.primary,
                    size: 20,
                  ),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Chưa cập nhật địa điểm cung cấp dịch vụ',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Select Date
            _buildSectionTitle('2. Chọn ngày hẹn:'),
            InkWell(
              onTap: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: _selectedDate,
                  firstDate: DateTime.now(),
                  lastDate: DateTime(2100),
                );
                if (picked != null) {
                  setState(() => _selectedDate = picked);
                  _fetchAvailableSlots();
                }
              },
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 12,
                ),
                decoration: BoxDecoration(
                  color: HasakiColors.getSurface(context),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: HasakiColors.border),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      HasakiFormatters.formatDate(_selectedDate),
                      style: HasakiTypography.bodyMedium,
                    ),
                    const Icon(
                      Icons.calendar_today,
                      size: 18,
                      color: HasakiColors.primary,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Select Time Slot - Dynamic from Backend
            _buildSectionTitle('3. Chọn khung giờ khả dụng:'),
            if (_loadingSlots)
              const Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: CircularProgressIndicator(color: HasakiColors.primary),
                ),
              )
            else if (_slotsError != null)
              CatalogState(message: _slotsError!, onRetry: _fetchAvailableSlots)
            else if (_availableSlots.isEmpty)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFFBEB),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFFFDE68A)),
                ),
                child: const Text(
                  'Không có khung giờ trống trong ngày này. Vui lòng chọn ngày khác.',
                  style: TextStyle(fontSize: 12.5, color: Color(0xFF92400E)),
                  textAlign: TextAlign.center,
                ),
              )
            else
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _availableSlots.map((slot) {
                  final isSelected = _selectedTime == slot;
                  return ChoiceChip(
                    label: Text(slot),
                    selected: isSelected,
                    onSelected: (_) => setState(() => _selectedTime = slot),
                    selectedColor: HasakiColors.primaryLight,
                    backgroundColor: HasakiColors.getSurface(context),
                    labelStyle: TextStyle(
                      fontSize: 12,
                      fontWeight: isSelected
                          ? FontWeight.w800
                          : FontWeight.w600,
                      color: isSelected
                          ? HasakiColors.primary
                          : HasakiColors.getTextMain(context),
                    ),
                    side: BorderSide(
                      color: isSelected
                          ? HasakiColors.primary
                          : HasakiColors.border,
                    ),
                  );
                }).toList(),
              ),
            const SizedBox(height: 16),

            // Note Input
            _buildSectionTitle('4. Ghi chú tình trạng da (tùy chọn):'),
            TextField(
              controller: _noteController,
              maxLines: 2,
              decoration: const InputDecoration(
                hintText: 'Ví dụ: Da mụn ẩn vùng chữ T, nhạy cảm sau peel...',
              ),
            ),
            const SizedBox(height: 24),

            // Submit Button
            if (_policyError != null) ...[
              Text(_policyError!),
              TextButton(
                onPressed: _loadFinancialPolicy,
                child: const Text('Tải lại chính sách'),
              ),
            ],
            if (_financialPolicy != null && _requiresFinancialAcceptance)
              CheckboxListTile(
                value: _financialAccepted,
                onChanged: (value) =>
                    setState(() => _financialAccepted = value ?? false),
                title: const Text('Đồng ý chính sách cọc, hủy và vắng hẹn'),
                subtitle: Text(
                  'Phiên bản ${_financialPolicy!['version']}: cọc tối đa ${_financialPolicy!['depositAmount']} VND; phí hủy sau hạn ${_financialPolicy!['cancellationFee']} VND; phí vắng hẹn ${_financialPolicy!['noShowFee']} VND. Nộp cọc tại Spa để xác nhận lịch; cọc trừ hóa đơn hoặc hoàn sau đối soát.',
                ),
              ),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: () async {
                  if (_financialPolicy == null ||
                      (_requiresFinancialAcceptance && !_financialAccepted)) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text(
                          'Cần tải và xác nhận chính sách cọc trước khi đặt.',
                        ),
                      ),
                    );
                    return;
                  }
                  if (_selectedTime.isEmpty) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text(
                          'Vui lòng chọn khung giờ trống khả dụng.',
                        ),
                      ),
                    );
                    return;
                  }

                  final auth = context.read<AuthProvider>();
                  if (!auth.isAuthenticated) {
                    final proceed = await showDialog<bool>(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: const Row(
                          children: [
                            Icon(
                              Icons.lock_outline,
                              color: HasakiColors.primary,
                            ),
                            SizedBox(width: 8),
                            Text('Yêu cầu đăng nhập'),
                          ],
                        ),
                        content: const Text(
                          'Bạn cần đăng nhập tài khoản BeautyShop để xác nhận và quản lý lịch hẹn Spa & Làm Đẹp.',
                        ),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(ctx, false),
                            child: const Text('Để sau'),
                          ),
                          ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: HasakiColors.primary,
                            ),
                            onPressed: () => Navigator.pop(ctx, true),
                            child: const Text('Đăng nhập ngay'),
                          ),
                        ],
                      ),
                    );
                    if (proceed == true) {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const LoginScreen()),
                      );
                    }
                    return;
                  }

                  if (_ticketId != null && selectedTicket == null) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text(
                          'Vé đã chọn chưa khả dụng. Tải lại vé hoặc chủ động chọn thanh toán dịch vụ lẻ.',
                        ),
                      ),
                    );
                    return;
                  }
                  final success = await context
                      .read<SpaProvider>()
                      .bookAppointment(
                        serviceId: widget.service.id,
                        ticketId: selectedTicket,
                        serviceName: widget.service.name,
                        date: _selectedDate,
                        timeSlot: _selectedTime,
                        notes: _noteController.text,
                        acceptedFinancialPolicy: _financialAccepted,
                        financialPolicyVersion:
                            (_financialPolicy!['version'] as num).toInt(),
                      );

                  if (!mounted || !context.mounted) return;
                  if (success) {
                    showDialog(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: const Row(
                          children: [
                            Icon(
                              Icons.check_circle,
                              color: HasakiColors.success,
                            ),
                            SizedBox(width: 8),
                            Text('Đặt Lịch Thành Công!'),
                          ],
                        ),
                        content: Text(
                          'Lịch hẹn liệu trình "${widget.service.name}" lúc $_selectedTime ngày ${HasakiFormatters.formatDate(_selectedDate)} đã được gửi thành công.',
                        ),
                        actions: [
                          TextButton(
                            onPressed: () {
                              Navigator.pop(ctx);
                              Navigator.pop(context);
                            },
                            child: const Text('Hoàn tất'),
                          ),
                        ],
                      ),
                    );
                  } else {
                    final err =
                        context.read<SpaProvider>().errorMessage ??
                        'Không thể đặt lịch hẹn lúc này';
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(err),
                        backgroundColor: HasakiColors.dealRed,
                      ),
                    );
                  }
                },
                child: const Text('XÁC NHẬN ĐẶT HẸN'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        title,
        style: HasakiTypography.titleMedium.copyWith(
          fontSize: 13.5,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
