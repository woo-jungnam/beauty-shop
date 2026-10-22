import '../treatments/treatment_appointment_screen.dart';
import 'package:flutter/material.dart';
import '../../widgets/catalog_state.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/data/models/spa_model.dart';
import 'package:beautyshop_mobile/providers/spa_provider.dart';

class MyAppointmentsScreen extends StatefulWidget {
  const MyAppointmentsScreen({Key? key}) : super(key: key);

  @override
  State<MyAppointmentsScreen> createState() => _MyAppointmentsScreenState();
}

class _MyAppointmentsScreenState extends State<MyAppointmentsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final List<String> _tabs = ['Tất cả', 'Sắp tới', 'Hoàn thành', 'Đã hủy'];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SpaProvider>().fetchAppointments();
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Color _getStatusColor(String status) {
    switch (status) {
      case 'CONFIRMED':
      case 'COMPLETED':
        return AppColors.success;
      case 'PENDING':
        return Colors.orange;
      case 'IN_PROGRESS':
        return AppColors.primary;
      case 'CANCELLED':
        return AppColors.dealRed;
      default:
        return AppColors.textMuted;
    }
  }

  List<Appointment> _filterAppointments(List<Appointment> list, int tabIdx) {
    switch (tabIdx) {
      case 1:
        return list
            .where(
              (a) =>
                  a.status == 'PENDING' ||
                  a.status == 'CONFIRMED' ||
                  a.status == 'IN_PROGRESS',
            )
            .toList();
      case 2:
        return list.where((a) => a.status == 'COMPLETED').toList();
      case 3:
        return list.where((a) => a.status == 'CANCELLED').toList();
      default:
        return list;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Lịch Hẹn Spa & Làm Đẹp'),
        backgroundColor: AppColors.primary,
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: Colors.white,
          indicatorWeight: 3,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white70,
          labelStyle: const TextStyle(
            fontWeight: FontWeight.w800,
            fontSize: 13,
          ),
          tabs: _tabs.map((t) => Tab(text: t)).toList(),
        ),
      ),
      body: Consumer<SpaProvider>(
        builder: (context, provider, _) {
          if (provider.isLoading && provider.appointments.isEmpty) {
            return const Center(
              child: CircularProgressIndicator(color: AppColors.primary),
            );
          }

          final allAppointments = provider.appointments;

          return TabBarView(
            controller: _tabController,
            children: List.generate(_tabs.length, (tabIdx) {
              final filtered = _filterAppointments(allAppointments, tabIdx);
              if (provider.loadingAppointments)
                return const Center(child: CircularProgressIndicator());
              if (provider.appointmentsError != null)
                return CatalogState(
                  message: provider.appointmentsError!,
                  onRetry: provider.fetchAppointments,
                );

              if (filtered.isEmpty) {
                return Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.calendar_month_outlined,
                        size: 56,
                        color: AppColors.textLight.withOpacity(0.5),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'Chưa có lịch hẹn nào',
                        style: AppTypography.titleMedium,
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Đặt lịch dịch vụ chăm sóc da tại mục Spa & Làm Đẹp',
                        style: AppTypography.bodySmall,
                      ),
                    ],
                  ),
                );
              }

              return RefreshIndicator(
                color: AppColors.primary,
                onRefresh: () => provider.fetchAppointments(),
                child: ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: filtered.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, idx) {
                    final appointment = filtered[idx];
                    return _buildAppointmentCard(
                      context,
                      appointment,
                      provider,
                    );
                  },
                ),
              );
            }),
          );
        },
      ),
    );
  }

  Widget _buildAppointmentCard(
    BuildContext context,
    Appointment appointment,
    SpaProvider provider,
  ) {
    final statusColor = _getStatusColor(appointment.status);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.borderSubtle),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(
                    Icons.spa_outlined,
                    size: 18,
                    color: AppColors.primary,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    'Mã lịch #${appointment.id}',
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 13,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: statusColor.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  appointment.statusLabel,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    color: statusColor,
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 18, color: AppColors.borderSubtle),

          Text(
            appointment.serviceName,
            style: TextStyle(
              fontSize: 14.5,
              fontWeight: FontWeight.w800,
              color: AppColors.getTextMain(context),
            ),
          ),
          const SizedBox(height: 8),

          if (appointment.branchName.isNotEmpty)
            Row(
              children: [
                const Icon(
                  Icons.location_on_outlined,
                  size: 15,
                  color: AppColors.textMuted,
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    appointment.branchName,
                    style: AppTypography.bodySmall,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          const SizedBox(height: 4),
          Row(
            children: [
              const Icon(
                Icons.access_time,
                size: 15,
                color: AppColors.textMuted,
              ),
              const SizedBox(width: 6),
              Text(
                appointment.appointmentDate == null ||
                        appointment.timeSlot.isEmpty
                    ? 'Chưa cập nhật thời gian hẹn'
                    : '${appointment.timeSlot} — ${AppFormatters.formatDate(appointment.appointmentDate)}',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          if (appointment.notes != null && appointment.notes!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              'Ghi chú: ${appointment.notes}',
              style: const TextStyle(
                fontSize: 11.5,
                fontStyle: FontStyle.italic,
                color: AppColors.textMuted,
              ),
            ),
          ],

          OutlinedButton(
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => TreatmentAppointmentScreen(
                    id: appointment.id,
                    operate: false,
                  ),
                ),
              );
              if (context.mounted) await provider.fetchAppointments();
            },
            child: const Text('Chi tiết, đổi lịch & phiếu chuẩn bị'),
          ),
          if (appointment.canCancel) ...[
            const Divider(height: 18, color: AppColors.borderSubtle),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: () async {
                  final confirm = await showDialog<bool>(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: const Text('Hủy lịch hẹn?'),
                      content: Text(
                        'Bạn có chắc chắn muốn hủy lịch hẹn ngày ${AppFormatters.formatDate(appointment.appointmentDate)} lúc ${appointment.timeSlot} không?',
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(ctx, false),
                          child: const Text('Không'),
                        ),
                        TextButton(
                          onPressed: () => Navigator.pop(ctx, true),
                          child: const Text(
                            'Xác nhận hủy',
                            style: TextStyle(color: AppColors.dealRed),
                          ),
                        ),
                      ],
                    ),
                  );
                  if (confirm == true) {
                    final ok = await provider.cancelAppointment(appointment.id);
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            ok
                                ? 'Đã hủy lịch hẹn thành công'
                                : 'Không thể hủy lịch hẹn',
                          ),
                          backgroundColor: ok
                              ? AppColors.primary
                              : AppColors.dealRed,
                        ),
                      );
                    }
                  }
                },
                icon: const Icon(
                  Icons.cancel_outlined,
                  size: 16,
                  color: AppColors.dealRed,
                ),
                label: const Text(
                  'HỦY LỊCH HẸN',
                  style: TextStyle(
                    color: AppColors.dealRed,
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
