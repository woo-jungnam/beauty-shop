import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';
import 'package:beautyshop_mobile/providers/theme_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/auth/login_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/orders/order_history_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/spa/my_appointments_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/spa/spa_tickets_screen.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.canvas,
      appBar: AppBar(
        title: const Text('Tài Khoản & Hội Viên'),
        backgroundColor: HasakiColors.primary,
        elevation: 0,
      ),
      body: Consumer<AuthProvider>(
        builder: (context, auth, _) {
          final user = auth.currentUser;

          return SingleChildScrollView(
            child: Column(
              children: [
                // 1. User Header & Member Tier Card (Hasaki Style)
                Container(
                  color: HasakiColors.primary,
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 20),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 30,
                            backgroundColor: Colors.white24,
                            child: const Icon(Icons.person, color: Colors.white, size: 36),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  auth.isAuthenticated ? (user?.fullName ?? user?.username ?? '') : 'Khách vãng lai',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 17,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  auth.isAuthenticated ? (user?.email ?? user?.phone ?? 'Thành viên VIP') : 'Đăng nhập để nhận ưu đãi thành viên',
                                  style: TextStyle(
                                    color: Colors.white.withOpacity(0.85),
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          if (!auth.isAuthenticated)
                            ElevatedButton(
                              onPressed: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(builder: (_) => const LoginScreen()),
                                );
                              },
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Colors.white,
                                foregroundColor: HasakiColors.primary,
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                              ),
                              child: const Text('Đăng nhập'),
                            ),
                        ],
                      ),
                      const SizedBox(height: 16),

                      // VIP Points / Tier Banner Card
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: HasakiColors.primaryDark,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: HasakiColors.gold.withOpacity(0.4)),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          children: [
                            _buildProfileStat('Hạng Thành Viên', user?.membershipTier ?? 'Bạc (Silver)', Icons.workspace_premium),
                            Container(width: 1, height: 30, color: Colors.white24),
                            _buildProfileStat('Điểm Tích Lũy', '${user?.rewardPoints ?? 150} Đ', Icons.stars),
                            Container(width: 1, height: 30, color: Colors.white24),
                            InkWell(
                              onTap: () => Navigator.push(
                                context,
                                MaterialPageRoute(builder: (_) => const SpaTicketsScreen()),
                              ),
                              child: _buildProfileStat('Ví Thẻ Spa', 'Xem thẻ', Icons.card_giftcard),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 10),

                // 2. Order Status 5 Stages (Hasaki Standard)
                Container(
                  color: Colors.white,
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('Đơn Hàng Của Tôi', style: HasakiTypography.titleMedium),
                          InkWell(
                            onTap: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(builder: (_) => const OrderHistoryScreen()),
                              );
                            },
                            child: const Row(
                              children: [
                                Text('Xem lịch sử', style: TextStyle(fontSize: 11.5, color: HasakiColors.textMuted)),
                                Icon(Icons.chevron_right, size: 16, color: HasakiColors.textMuted),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const Divider(height: 20, color: HasakiColors.borderSubtle),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceAround,
                        children: [
                          _buildOrderStatusItem(
                            Icons.payment,
                            'Chờ thanh toán',
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(builder: (_) => const OrderHistoryScreen(initialTabIndex: 1)),
                            ),
                          ),
                          _buildOrderStatusItem(
                            Icons.inventory_2_outlined,
                            'Chờ xử lý',
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(builder: (_) => const OrderHistoryScreen(initialTabIndex: 1)),
                            ),
                          ),
                          _buildOrderStatusItem(
                            Icons.local_shipping_outlined,
                            'Đang giao',
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(builder: (_) => const OrderHistoryScreen(initialTabIndex: 2)),
                            ),
                          ),
                          _buildOrderStatusItem(
                            Icons.check_circle_outline,
                            'Đã giao',
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(builder: (_) => const OrderHistoryScreen(initialTabIndex: 3)),
                            ),
                          ),
                          _buildOrderStatusItem(
                            Icons.rate_review_outlined,
                            'Đánh giá',
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(builder: (_) => const OrderHistoryScreen(initialTabIndex: 3)),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 10),

                // 3. Quick Utility Menu List
                Material(
                  color: Colors.white,
                  child: Column(
                    children: [
                      _buildMenuItem(
                        Icons.spa_outlined,
                        'Lịch Hẹn Spa & Làm Đẹp Đã Đặt',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const MyAppointmentsScreen()),
                        ),
                      ),
                      _buildMenuItem(
                        Icons.card_giftcard,
                        'Ví Vé Liệu Trình Spa & Làm Đẹp',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const SpaTicketsScreen()),
                        ),
                      ),
                      _buildMenuItem(Icons.confirmation_number_outlined, 'Ví Voucher & Khuyến Mãi', () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Mã voucher áp dụng tại giỏ hàng: HASAKI100, FREESHIP'),
                            backgroundColor: HasakiColors.primary,
                          ),
                        );
                      }),
                      _buildMenuItem(Icons.support_agent_outlined, 'Tổng Đài Hỗ Trợ Khách Hàng (1800 6324)', () {}),
                      Consumer<ThemeProvider>(
                        builder: (context, theme, _) {
                          String themeLabel;
                          IconData themeIcon;
                          if (theme.themeMode == ThemeMode.dark) {
                            themeLabel = 'Tối (Dark Mode)';
                            themeIcon = Icons.dark_mode_outlined;
                          } else if (theme.themeMode == ThemeMode.light) {
                            themeLabel = 'Sáng (Hồng Phấn)';
                            themeIcon = Icons.light_mode_outlined;
                          } else {
                            themeLabel = 'Theo hệ thống';
                            themeIcon = Icons.brightness_auto_outlined;
                          }

                          return _buildMenuItem(
                            themeIcon,
                            'Giao diện: $themeLabel',
                            () => _showThemeDialog(context, theme),
                          );
                        },
                      ),
                      _buildMenuItem(Icons.settings_outlined, 'Cài Đặt Ứng Dụng', () {}),
                    ],
                  ),
                ),

                const SizedBox(height: 14),

                // 4. Logout Button
                if (auth.isAuthenticated)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    child: SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: () => auth.logout(),
                        icon: const Icon(Icons.logout, color: HasakiColors.dealRed),
                        label: const Text('ĐĂNG XUẤT TÀI KHOẢN', style: TextStyle(color: HasakiColors.dealRed)),
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: HasakiColors.dealRedBorder),
                        ),
                      ),
                    ),
                  ),
                const SizedBox(height: 30),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildProfileStat(String label, String value, IconData icon) {
    return Column(
      children: [
        Row(
          children: [
            Icon(icon, color: HasakiColors.gold, size: 14),
            const SizedBox(width: 4),
            Text(value, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13)),
          ],
        ),
        const SizedBox(height: 2),
        Text(label, style: const TextStyle(color: Colors.white70, fontSize: 10.5)),
      ],
    );
  }

  Widget _buildOrderStatusItem(IconData icon, String label, {VoidCallback? onTap}) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
        child: Column(
          children: [
            Icon(icon, color: HasakiColors.primary, size: 24),
            const SizedBox(height: 6),
            Text(label, style: const TextStyle(fontSize: 10.5, color: HasakiColors.textMain, fontWeight: FontWeight.w500)),
          ],
        ),
      ),
    );
  }

  Widget _buildMenuItem(IconData icon, String title, VoidCallback onTap) {
    return ListTile(
      leading: Icon(icon, color: HasakiColors.primary, size: 22),
      title: Text(title, style: HasakiTypography.bodyMedium.copyWith(fontWeight: FontWeight.w600)),
      trailing: const Icon(Icons.chevron_right, size: 18, color: HasakiColors.textLight),
      onTap: onTap,
    );
  }

  void _showThemeDialog(BuildContext context, ThemeProvider theme) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Chọn giao diện ứng dụng', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            RadioListTile<ThemeMode>(
              title: const Text('Theo hệ thống'),
              subtitle: const Text('Tự động theo cài đặt thiết bị', style: TextStyle(fontSize: 11)),
              value: ThemeMode.system,
              groupValue: theme.themeMode,
              activeColor: HasakiColors.primary,
              onChanged: (val) {
                if (val != null) theme.setThemeMode(val);
                Navigator.pop(ctx);
              },
            ),
            RadioListTile<ThemeMode>(
              title: const Text('Chế độ Sáng'),
              subtitle: const Text('Tông hồng phấn tươi sáng', style: TextStyle(fontSize: 11)),
              value: ThemeMode.light,
              groupValue: theme.themeMode,
              activeColor: HasakiColors.primary,
              onChanged: (val) {
                if (val != null) theme.setThemeMode(val);
                Navigator.pop(ctx);
              },
            ),
            RadioListTile<ThemeMode>(
              title: const Text('Chế độ Tối'),
              subtitle: const Text('Tông đen huyền bí ánh hồng', style: TextStyle(fontSize: 11)),
              value: ThemeMode.dark,
              groupValue: theme.themeMode,
              activeColor: HasakiColors.primary,
              onChanged: (val) {
                if (val != null) theme.setThemeMode(val);
                Navigator.pop(ctx);
              },
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('ĐÓNG'),
          ),
        ],
      ),
    );
  }
}
