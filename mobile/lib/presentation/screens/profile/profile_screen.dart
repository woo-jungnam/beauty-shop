import 'package:dio/dio.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../../../core/utils/formatters.dart';
import 'customer_account_screen.dart';
import 'account_help_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'package:beautyshop_mobile/providers/theme_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/auth/login_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/orders/order_history_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/orders/order_lookup_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/spa/my_appointments_screen.dart';
import 'package:beautyshop_mobile/presentation/screens/spa/spa_tickets_screen.dart';
import '../../../data/models/treatment_model.dart';
import '../treatments/treatment_hub_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key, this.apiClient});

  final ApiClient? apiClient;

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map<String, dynamic>? _orderStatistics;
  int? _statsUserId;
  int _statsRequest = 0;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final auth = context.watch<AuthProvider>();
    final uid = auth.isAuthenticated ? auth.currentUser?.id : null;
    if (uid == _statsUserId) return;
    _statsUserId = uid;
    _orderStatistics = null;
    _statsRequest++;
    if (uid != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _statsUserId == uid) _fetchOrderStatistics(uid);
      });
    }
  }

  Future<void> _fetchOrderStatistics(int userId) async {
    final request = ++_statsRequest;
    try {
      final response = await (widget.apiClient ?? ApiClient())
          .get<dynamic>('/api/v1/users/order-statistics');
      if (mounted &&
          request == _statsRequest &&
          _statsUserId == userId &&
          response.isSuccess &&
          response.data is Map) {
        setState(() {
          _orderStatistics = Map<String, dynamic>.from(response.data as Map);
        });
      }
    } catch (_) {
      // Keep the last successful statistics for the current account.
    }
  }

  Widget _transactionStat(String label, String key, {bool currency = true}) {
    final value = _orderStatistics?[key];
    return ListTile(
      dense: true,
      contentPadding: EdgeInsets.zero,
      title: Text(label),
      trailing: Text(value is num
          ? (currency ? AppFormatters.formatCurrency(value) : value.toInt().toString())
          : '—'),
    );
  }

  Future<void> _openOrderHistory(OrderHistoryScreen screen) async {
    await Navigator.push<void>(
      context,
      MaterialPageRoute(builder: (_) => screen),
    );
    final userId = _statsUserId;
    if (mounted && userId != null) await _fetchOrderStatistics(userId);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Tài Khoản & Hội Viên'),
        backgroundColor: AppColors.primary,
        elevation: 0,
      ),
      body: Consumer<AuthProvider>(
        builder: (context, auth, _) {
          final user = auth.currentUser;

          return SingleChildScrollView(
            child: Column(
              children: [
                Container(
                  color: AppColors.primary,
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 20),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          const CircleAvatar(
                            radius: 30,
                            backgroundColor: Colors.white24,
                            child: Icon(
                              Icons.person,
                              color: Colors.white,
                              size: 36,
                            ),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  auth.isAuthenticated
                                      ? (user?.fullName ?? user?.username ?? '')
                                      : 'Khách vãng lai',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 17,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  auth.isAuthenticated
                                      ? (user?.email ??
                                            user?.phone ??
                                            'Thành viên')
                                      : 'Đăng nhập để nhận ưu đãi thành viên',
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
                                  MaterialPageRoute(
                                    builder: (_) => const LoginScreen(),
                                  ),
                                );
                              },
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppColors.getSurface(
                                  context,
                                ),
                                foregroundColor: AppColors.primary,
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 14,
                                  vertical: 8,
                                ),
                              ),
                              child: const Text('Đăng nhập'),
                            ),
                        ],
                      ),
                      const SizedBox(height: 16),

                      if (auth.isAuthenticated)
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppColors.primaryDark,
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: AppColors.gold.withOpacity(0.4),
                            ),
                          ),
                          child: Row(
                            children: [
                              Expanded(
                                child: _buildProfileStat(
                                  'Hạng Thành Viên',
                                  _formatTierName(user?.membershipTier),
                                  Icons.workspace_premium,
                                ),
                              ),
                              Container(
                                width: 1,
                                height: 30,
                                color: Colors.white24,
                              ),
                              Expanded(
                                child: _buildProfileStat(
                                  'Chi Tiêu Tính Điểm',
                                  _orderStatistics?['loyaltyEligibleSpent'] is num
                                      ? AppFormatters.formatCurrency(
                                          (_orderStatistics!['loyaltyEligibleSpent'] as num).toDouble(),
                                        )
                                      : '--',
                                  Icons.account_balance_wallet_outlined,
                                ),
                              ),
                              Container(
                                width: 1,
                                height: 30,
                                color: Colors.white24,
                              ),
                              Expanded(
                                child: InkWell(
                                  onTap: () => Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                      builder: (_) => const SpaTicketsScreen(),
                                    ),
                                  ),
                                  child: _buildProfileStat(
                                    'Ví Thẻ Spa',
                                    'Xem thẻ',
                                    Icons.card_giftcard,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),

                if (auth.isAuthenticated) ...[
                  const SizedBox(height: 10),
                  Container(
                    color: AppColors.getSurface(context),
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Thống kê giao dịch', style: AppTypography.titleMedium),
                        const Divider(),
                        _transactionStat('Tổng số đơn', 'totalOrders', currency: false),
                        _transactionStat('Tổng tiền đã thanh toán', 'totalPaid'),
                        _transactionStat('Tổng tiền đã hoàn', 'totalRefunded'),
                      ],
                    ),
                  ),
                ],

                const SizedBox(height: 10),

                Container(
                  color: AppColors.getSurface(context),
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              'Đơn Hàng Của Tôi',
                              style: AppTypography.titleMedium,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Flexible(
                            child: InkWell(
                              onTap: () => _openOrderHistory(
                                const OrderHistoryScreen(),
                              ),
                              child: const Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Flexible(
                                    child: Text(
                                      'Xem lịch sử',
                                      style: TextStyle(
                                        fontSize: 11.5,
                                        color: AppColors.textMuted,
                                      ),
                                    ),
                                  ),
                                  Icon(
                                    Icons.chevron_right,
                                    size: 16,
                                    color: AppColors.textMuted,
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                      const Divider(
                        height: 20,
                        color: AppColors.borderSubtle,
                      ),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildOrderStatusItem(
                            Icons.payment,
                            'Chờ thanh toán',
                            onTap: () => _openOrderHistory(
                              const OrderHistoryScreen(unpaidOnly: true),
                            ),
                          ),
                          _buildOrderStatusItem(
                            Icons.inventory_2_outlined,
                            'Chờ xử lý',
                            onTap: () => _openOrderHistory(
                              const OrderHistoryScreen(initialTabIndex: 1),
                            ),
                          ),
                          _buildOrderStatusItem(
                            Icons.local_shipping_outlined,
                            'Đang giao',
                            onTap: () => _openOrderHistory(
                              const OrderHistoryScreen(initialTabIndex: 2),
                            ),
                          ),
                          _buildOrderStatusItem(
                            Icons.check_circle_outline,
                            'Đã giao',
                            onTap: () => _openOrderHistory(
                              const OrderHistoryScreen(initialTabIndex: 3),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 10),

                if (auth.isAuthenticated)
                  Column(
                    children: [
                      for (final entry in <String, String>{
                        'profile': 'Thông tin & bảo mật',
                        'skin': 'Hồ sơ làn da',
                        'addresses': 'Sổ địa chỉ',
                        'wishlist': 'Sản phẩm yêu thích',
                        'notifications': 'Thông báo',
                      }.entries)
                        _buildMenuItem(
                          Icons.manage_accounts_outlined,
                          entry.value,
                          () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) =>
                                  CustomerAccountScreen(area: entry.key),
                            ),
                          ),
                        ),
                    ],
                  ),
                Material(
                  color: AppColors.getSurface(context),
                  child: Column(
                    children: [
                      _buildMenuItem(
                        Icons.route_outlined,
                        'Điều Trị Theo Lộ Trình',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const TreatmentHubScreen(),
                          ),
                        ),
                      ),
                      if (auth.isAuthenticated && user?.canOperateSpa == true)
                        _buildMenuItem(
                          Icons.assignment_ind_outlined,
                          'Công Việc Điều Trị',
                          () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => const TreatmentHubScreen(
                                initialArea: TreatmentArea.staff,
                              ),
                            ),
                          ),
                        ),
                      if (auth.isAuthenticated && user?.isAdmin == true)
                        _buildMenuItem(
                          Icons.admin_panel_settings_outlined,
                          'Quản Trị Lộ Trình Spa',
                          () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => const TreatmentHubScreen(
                                initialArea: TreatmentArea.admin,
                              ),
                            ),
                          ),
                        ),
                      _buildMenuItem(
                        Icons.spa_outlined,
                        'Lịch Hẹn Spa & Làm Đẹp Đã Đặt',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const MyAppointmentsScreen(),
                          ),
                        ),
                      ),
                      _buildMenuItem(
                        Icons.storefront_outlined,
                        'Hệ thống chi nhánh',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const AccountHelpScreen(showBranches: true)),
                        ),
                      ),
                      _buildMenuItem(
                        Icons.verified_outlined,
                        'Chính sách cam kết',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const AccountHelpScreen(showBranches: false)),
                        ),
                      ),
                      _buildMenuItem(
                        Icons.search_outlined,
                        'Tra Cứu Đơn Hàng (Không Cần Đăng Nhập)',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const OrderLookupScreen(),
                          ),
                        ),
                      ),
                      _buildMenuItem(
                        Icons.card_giftcard,
                        'Ví Vé Liệu Trình Spa & Làm Đẹp',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const SpaTicketsScreen(),
                          ),
                        ),
                      ),
                      _buildMenuItem(
                        Icons.confirmation_number_outlined,
                        'Mã Ưu Đãi & Khuyến Mãi',
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                const CustomerAccountScreen(area: 'vouchers'),
                          ),
                        ),
                      ),
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
                      _buildMenuItem(
                        Icons.dns_outlined,
                        'Máy chủ kết nối: ${ApiConstants.baseUrl}',
                        () => _showServerDialog(context),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 14),

                if (auth.isAuthenticated)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    child: SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: () => auth.logout(),
                        icon: const Icon(
                          Icons.logout,
                          color: AppColors.dealRed,
                        ),
                        label: const Text(
                          'ĐĂNG XUẤT TÀI KHOẢN',
                          style: TextStyle(color: AppColors.dealRed),
                        ),
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(
                            color: AppColors.dealRedBorder,
                          ),
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

  String _formatTierName(String? tier) {
    switch (tier?.toUpperCase()) {
      case 'MEMBER':
        return 'Thành viên';
      case 'SILVER':
        return 'Hạng Bạc';
      case 'GOLD':
        return 'Hạng Vàng';
      case 'PLATINUM':
        return 'Hạng Bạch kim';
      default:
        return tier ?? 'Chưa cập nhật';
    }
  }

  Widget _buildProfileStat(String label, String value, IconData icon) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
      child: Column(
        children: [
          Icon(icon, color: AppColors.gold, size: 14),
          const SizedBox(height: 4),
          Text(
            value,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w800,
              fontSize: 13,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.white70, fontSize: 10.5),
          ),
        ],
      ),
    );
  }

  Widget _buildOrderStatusItem(
    IconData icon,
    String label, {
    VoidCallback? onTap,
  }) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
          child: Column(
            children: [
              Icon(icon, color: AppColors.primary, size: 24),
              const SizedBox(height: 6),
              Text(
                label,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 10.5,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMenuItem(IconData icon, String title, VoidCallback onTap) {
    return ListTile(
      leading: Icon(icon, color: AppColors.primary, size: 22),
      title: Text(
        title,
        style: AppTypography.bodyMedium.copyWith(
          fontWeight: FontWeight.w600,
        ),
      ),
      trailing: const Icon(
        Icons.chevron_right,
        size: 18,
        color: AppColors.textLight,
      ),
      onTap: onTap,
    );
  }

  void _showThemeDialog(BuildContext context, ThemeProvider theme) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text(
          'Chọn giao diện ứng dụng',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            RadioListTile<ThemeMode>(
              title: const Text('Theo hệ thống'),
              subtitle: const Text(
                'Tự động theo cài đặt thiết bị',
                style: TextStyle(fontSize: 11),
              ),
              value: ThemeMode.system,
              groupValue: theme.themeMode,
              activeColor: AppColors.primary,
              onChanged: (val) {
                if (val != null) theme.setThemeMode(val);
                Navigator.pop(ctx);
              },
            ),
            RadioListTile<ThemeMode>(
              title: const Text('Chế độ Sáng'),
              subtitle: const Text(
                'Tông hồng phấn tươi sáng',
                style: TextStyle(fontSize: 11),
              ),
              value: ThemeMode.light,
              groupValue: theme.themeMode,
              activeColor: AppColors.primary,
              onChanged: (val) {
                if (val != null) theme.setThemeMode(val);
                Navigator.pop(ctx);
              },
            ),
            RadioListTile<ThemeMode>(
              title: const Text('Chế độ Tối'),
              subtitle: const Text(
                'Tông đen huyền bí ánh hồng',
                style: TextStyle(fontSize: 11),
              ),
              value: ThemeMode.dark,
              groupValue: theme.themeMode,
              activeColor: AppColors.primary,
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

  void _showServerDialog(BuildContext context) {
    final controller = TextEditingController(text: ApiConstants.baseUrl);
    bool testing = false;
    String? testResult;
    bool? isSuccess;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => AlertDialog(
          title: const Row(
            children: [
              Icon(Icons.dns, color: AppColors.primary),
              SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Cấu hình máy chủ Backend',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ),
            ],
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Địa chỉ máy chủ (Base URL):',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 6),
                TextField(
                  controller: controller,
                  decoration: const InputDecoration(
                    isDense: true,
                    hintText: 'https://...',
                  ),
                ),
                const SizedBox(height: 10),
                const Text(
                  'Gợi ý kết nối:',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    if (ApiConstants.configuredBaseUrl.isNotEmpty)
                      ActionChip(
                        label: const Text('Production Cloud', style: TextStyle(fontSize: 11)),
                        onPressed: () => setState(() => controller.text = ApiConstants.configuredBaseUrl),
                      ),
                    if (ApiConstants.emulatorBaseUrl.isNotEmpty)
                      ActionChip(
                        label: const Text('Android Emulator', style: TextStyle(fontSize: 11)),
                        onPressed: () => setState(() => controller.text = ApiConstants.emulatorBaseUrl),
                      ),
                    if (ApiConstants.localBaseUrl.isNotEmpty)
                      ActionChip(
                        label: const Text('Local', style: TextStyle(fontSize: 11)),
                        onPressed: () => setState(() => controller.text = ApiConstants.localBaseUrl),
                      ),
                  ],
                ),
                const SizedBox(height: 14),
                if (testing)
                  const Row(
                    children: [
                      SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                      SizedBox(width: 8),
                      Text('Đang kiểm tra kết nối...', style: TextStyle(fontSize: 12)),
                    ],
                  )
                else if (testResult != null)
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: isSuccess == true ? AppColors.primaryLight : AppColors.dealRedLight,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(
                        color: isSuccess == true ? AppColors.primary : AppColors.dealRedBorder,
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          isSuccess == true ? Icons.check_circle : Icons.error,
                          size: 16,
                          color: isSuccess == true ? AppColors.primary : AppColors.dealRed,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            testResult!,
                            style: TextStyle(
                              fontSize: 12,
                              color: isSuccess == true ? AppColors.primaryDark : AppColors.dealRedDark,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.network_check, size: 18),
                    label: const Text('Kiểm tra kết nối ngay'),
                    onPressed: testing
                        ? null
                        : () async {
                            setState(() {
                              testing = true;
                              testResult = null;
                              isSuccess = null;
                            });
                            final targetUrl = controller.text.trim();
                            final stopwatch = Stopwatch()..start();
                            try {
                              final dio = Dio(
                                BaseOptions(
                                  connectTimeout: const Duration(seconds: 6),
                                  receiveTimeout: const Duration(seconds: 6),
                                ),
                              );
                              final res = await dio.get('$targetUrl/api/v1/system-configs/public');
                              stopwatch.stop();
                              setState(() {
                                testing = false;
                                isSuccess = res.statusCode == 200;
                                testResult = 'Kết nối thành công (200 OK) • ${stopwatch.elapsedMilliseconds}ms';
                              });
                            } catch (_) {
                              stopwatch.stop();
                              try {
                                final dio = Dio(
                                  BaseOptions(
                                    connectTimeout: const Duration(seconds: 6),
                                    receiveTimeout: const Duration(seconds: 6),
                                  ),
                                );
                                final res = await dio.get('$targetUrl/api/v1/categories');
                                setState(() {
                                  testing = false;
                                  isSuccess = res.statusCode == 200;
                                  testResult = 'Kết nối thành công (200 OK) • ${stopwatch.elapsedMilliseconds}ms';
                                });
                              } catch (e2) {
                                setState(() {
                                  testing = false;
                                  isSuccess = false;
                                  testResult = 'Không thể kết nối đến máy chủ: $targetUrl';
                                });
                              }
                            }
                          },
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('HỦY'),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
              onPressed: () {
                final newUrl = controller.text.trim();
                if (newUrl.isNotEmpty) {
                  ApiConstants.setCustomBaseUrl(newUrl);
                }
                Navigator.pop(ctx);
              },
              child: const Text('LƯU & ÁP DỤNG'),
            ),
          ],
        ),
      ),
    );
  }
}
