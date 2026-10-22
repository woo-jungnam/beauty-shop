import 'package:flutter/material.dart';
import '../../widgets/catalog_state.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/data/models/spa_model.dart';
import 'package:beautyshop_mobile/providers/spa_provider.dart';
import 'spa_ticket_detail_screen.dart';
import 'service_packages_screen.dart';

class SpaTicketsScreen extends StatefulWidget {
  const SpaTicketsScreen({Key? key}) : super(key: key);

  @override
  State<SpaTicketsScreen> createState() => _SpaTicketsScreenState();
}

class _SpaTicketsScreenState extends State<SpaTicketsScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<SpaProvider>().fetchMyTickets();
    });
  }

  void _showTicketQrDialog(BuildContext context, UserServiceTicket ticket) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            const Icon(
              Icons.confirmation_number_outlined,
              color: AppColors.primary,
            ),
            const SizedBox(width: 8),
            Text(
              ticket.ticketCode.isEmpty
                  ? 'Vé #${ticket.id}'
                  : ticket.ticketCode,
              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                border: Border.all(color: AppColors.border),
                borderRadius: BorderRadius.circular(8),
              ),
              child: ticket.qrCodeUrl?.isNotEmpty == true
                  ? Image.network(
                      ticket.qrCodeUrl!,
                      width: 160,
                      height: 160,
                      errorBuilder: (_, _, _) =>
                          const Text('Không thể tải mã QR'),
                    )
                  : Text('Số vé #${ticket.id}'),
            ),
            const SizedBox(height: 12),
            Text(
              ticket.qrCodeUrl?.isNotEmpty == true
                  ? 'Xuất trình mã QR tại quầy tiếp tân để check-in dịch vụ.'
                  : 'Xuất trình số vé và thông tin tài khoản để nhân viên kiểm tra quyền lợi tại quầy.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text(
              'ĐÓNG',
              style: TextStyle(
                color: AppColors.primary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Ví Vé Liệu Trình Spa & Thư Giãn'),
        actions: [
          IconButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ServicePackagesScreen()),
            ),
            icon: const Icon(Icons.add_shopping_cart),
            tooltip: 'Mua gói dịch vụ',
          ),
        ],
        backgroundColor: AppColors.primary,
      ),
      body: Consumer<SpaProvider>(
        builder: (context, provider, _) {
          if (provider.loadingTickets)
            return const Center(child: CircularProgressIndicator());
          if (provider.ticketsError != null)
            return CatalogState(
              message: provider.ticketsError!,
              onRetry: provider.fetchMyTickets,
            );
          if (provider.isLoading && provider.myTickets.isEmpty) {
            return const Center(
              child: CircularProgressIndicator(color: AppColors.primary),
            );
          }

          final tickets = provider.myTickets;

          if (tickets.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.card_giftcard_outlined,
                    size: 60,
                    color: AppColors.textLight.withOpacity(0.5),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Chưa có vé liệu trình nào',
                    style: AppTypography.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Vé được cấp khi thanh toán gói dịch vụ. Dịch vụ lẻ thanh toán theo hóa đơn buổi hẹn.',
                    style: AppTypography.bodySmall,
                  ),
                ],
              ),
            );
          }

          return RefreshIndicator(
            color: AppColors.primary,
            onRefresh: () => provider.fetchMyTickets(),
            child: ListView.separated(
              padding: const EdgeInsets.all(14),
              itemCount: tickets.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, idx) {
                final ticket = tickets[idx];
                return _buildTicketCard(context, ticket);
              },
            ),
          );
        },
      ),
    );
  }

  Widget _buildTicketCard(BuildContext context, UserServiceTicket ticket) {
    final isValid = ticket.isValid;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.getSurface(context),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderSubtle),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.primaryLight,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  ticket.ticketCode.isEmpty
                      ? 'Vé #${ticket.id}'
                      : ticket.ticketCode,
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 12,
                    color: AppColors.primary,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isValid
                      ? AppColors.successLight
                      : AppColors.dealRedLight,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  isValid ? 'CÒN HIỆU LỰC' : 'KHÔNG KHẢ DỤNG',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    color: isValid
                        ? AppColors.success
                        : AppColors.dealRed,
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 20, color: AppColors.borderSubtle),

          if (ticket.reservedSessions > 0)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(
                'Đang đặt lịch: ${ticket.reservedSessions} buổi',
                style: const TextStyle(fontSize: 12, color: AppColors.primary),
              ),
            ),
          ...ticket.services.map(
            (s) => Padding(
              padding: const EdgeInsets.only(bottom: 2),
              child: Text(
                '${s['serviceName']}: Còn ${s['available']} buổi',
                style: const TextStyle(fontSize: 12, color: AppColors.textMuted),
              ),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => SpaTicketDetailScreen(ticket: ticket),
              ),
            ),
            child: const Text('Chi tiết quyền lợi & lịch sử / đặt lịch'),
          ),

          Text(
            ticket.serviceName,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w800,
              color: AppColors.getTextMain(context),
            ),
          ),
          const SizedBox(height: 8),

          Row(
            children: [
              const Icon(
                Icons.confirmation_number_outlined,
                size: 16,
                color: AppColors.textMuted,
              ),
              const SizedBox(width: 6),
              Text(
                'Số buổi còn lại: ${ticket.remainingQuantity} / ${ticket.totalQuantity} buổi',
                style: const TextStyle(
                  fontWeight: FontWeight.w700,
                  fontSize: 13,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          if (ticket.expiresAt != null) ...[
            const SizedBox(height: 4),
            Row(
              children: [
                const Icon(
                  Icons.event_available,
                  size: 16,
                  color: AppColors.textMuted,
                ),
                const SizedBox(width: 6),
                Text(
                  'Hạn sử dụng: ${ticket.expiresAt}',
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textMuted,
                  ),
                ),
              ],
            ),
          ],
          const Divider(height: 20, color: AppColors.borderSubtle),

          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              OutlinedButton.icon(
                onPressed: () => _showTicketQrDialog(context, ticket),
                icon: const Icon(
                  Icons.qr_code,
                  size: 16,
                  color: AppColors.primary,
                ),
                label: const Text(
                  'THÔNG TIN VÉ',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                    color: AppColors.primary,
                  ),
                ),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: AppColors.primary),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
