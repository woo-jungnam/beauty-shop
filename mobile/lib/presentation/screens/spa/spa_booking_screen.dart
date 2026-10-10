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

class SpaBookingScreen extends StatefulWidget {
  final SpaService service;

  const SpaBookingScreen({Key? key, required this.service}) : super(key: key);

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

  @override
  void initState() {
    super.initState();
    _fetchAvailableSlots();
  }

  Future<void> _fetchAvailableSlots() async {
    setState(() => _loadingSlots = true);
    final dateStr =
        '${_selectedDate.year.toString().padLeft(4, '0')}-${_selectedDate.month.toString().padLeft(2, '0')}-${_selectedDate.day.toString().padLeft(2, '0')}';
    try {
      final response = await _apiClient.get<List<String>>(
        '${ApiConstants.spaServices}/${widget.service.id}/available-slots',
        queryParameters: {'date': dateStr},
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list.map((e) => e.toString().length >= 5 ? e.toString().substring(0, 5) : e.toString()).toList();
        },
      );
      if (response.isSuccess && response.data != null && response.data!.isNotEmpty) {
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
      setState(() {
        _availableSlots = [];
        _selectedTime = '';
      });
    } finally {
      if (mounted) setState(() => _loadingSlots = false);
    }
  }

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.canvas,
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
                color: Colors.white,
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
                    child: const Icon(Icons.spa, color: HasakiColors.primary, size: 28),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.service.name,
                          style: HasakiTypography.titleMedium.copyWith(fontWeight: FontWeight.w800),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          HasakiFormatters.formatCurrency(widget.service.price),
                          style: HasakiTypography.priceMedium,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Clinic Location
            _buildSectionTitle('1. Cơ sở điều trị:'),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: HasakiColors.border),
              ),
              child: const Row(
                children: [
                  Icon(Icons.location_on, color: HasakiColors.primary, size: 20),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'BeautyShop Clinic & Spa (123 Đồng Khởi, Q.1, TP.HCM)',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
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
                  lastDate: DateTime.now().add(const Duration(days: 30)),
                );
                if (picked != null) {
                  setState(() => _selectedDate = picked);
                  _fetchAvailableSlots();
                }
              },
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: HasakiColors.border),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(HasakiFormatters.formatDate(_selectedDate), style: HasakiTypography.bodyMedium),
                    const Icon(Icons.calendar_today, size: 18, color: HasakiColors.primary),
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
                    backgroundColor: Colors.white,
                    labelStyle: TextStyle(
                      fontSize: 12,
                      fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                      color: isSelected ? HasakiColors.primary : HasakiColors.textMain,
                    ),
                    side: BorderSide(color: isSelected ? HasakiColors.primary : HasakiColors.border),
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
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: () async {
                  if (_selectedTime.isEmpty) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Vui lòng chọn khung giờ trống khả dụng.')),
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
                            Icon(Icons.lock_outline, color: HasakiColors.primary),
                            SizedBox(width: 8),
                            Text('Yêu cầu đăng nhập'),
                          ],
                        ),
                        content: const Text('Bạn cần đăng nhập tài khoản BeautyShop để xác nhận và quản lý lịch hẹn Spa & Làm Đẹp.'),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(ctx, false),
                            child: const Text('Để sau'),
                          ),
                          ElevatedButton(
                            style: ElevatedButton.styleFrom(backgroundColor: HasakiColors.primary),
                            onPressed: () => Navigator.pop(ctx, true),
                            child: const Text('Đăng nhập ngay'),
                          ),
                        ],
                      ),
                    );
                    if (proceed == true) {
                      Navigator.push(context, MaterialPageRoute(builder: (_) => const LoginScreen()));
                    }
                    return;
                  }

                  final success = await context.read<SpaProvider>().bookAppointment(
                        serviceId: widget.service.id,
                        serviceName: widget.service.name,
                        date: _selectedDate,
                        timeSlot: _selectedTime,
                        notes: _noteController.text,
                      );

                  if (!mounted) return;
                  if (success) {
                    showDialog(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        title: const Row(
                          children: [
                            Icon(Icons.check_circle, color: HasakiColors.success),
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
                    final err = context.read<SpaProvider>().errorMessage ?? 'Không thể đặt lịch hẹn lúc này';
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
        style: HasakiTypography.titleMedium.copyWith(fontSize: 13.5, fontWeight: FontWeight.w700),
      ),
    );
  }
}
