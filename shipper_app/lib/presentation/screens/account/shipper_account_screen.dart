import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/storage/shipper_settings_store.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';
import '../../../data/models/shipper_account_model.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/shipper_provider.dart';

class ShipperAccountScreen extends StatefulWidget {
  const ShipperAccountScreen({super.key});

  @override
  State<ShipperAccountScreen> createState() => _ShipperAccountScreenState();
}

class _ShipperAccountScreenState extends State<ShipperAccountScreen> {
  final ImagePicker _picker = ImagePicker();
  bool _uploadingAvatar = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _loadProfile();
    });
  }

  Future<void> _loadProfile() async {
    final shipper = context.read<ShipperProvider>();
    final auth = context.read<AuthProvider>();
    await shipper.fetchAccountProfile();
    if (!mounted) return;
    final fullName = shipper.userProfile?.fullName;
    if (fullName != null && fullName.isNotEmpty) {
      await auth.updateCachedFullName(fullName);
    }
  }

  Future<void> _pickAndUploadAvatar(ShipperUserProfile user) async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const ListTile(
              title: Text(
                'Đổi ảnh đại diện',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('Chụp ảnh mới'),
              onTap: () => Navigator.pop(ctx, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Chọn từ thư viện'),
              onTap: () => Navigator.pop(ctx, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null || !mounted) return;

    try {
      final picked = await _picker.pickImage(
        source: source,
        maxWidth: 1024,
        maxHeight: 1024,
        imageQuality: 85,
      );
      if (picked == null || !mounted) return;
      setState(() => _uploadingAvatar = true);
      final shipper = context.read<ShipperProvider>();
      final uploadedUrl = await shipper.uploadAvatar(picked);
      await shipper.updateUserProfile(
        fullName: user.fullName,
        phone: user.phone,
        avatarUrl: uploadedUrl,
        bio: user.bio,
        gender: user.gender,
        dateOfBirth: user.dateOfBirth,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã cập nhật ảnh đại diện')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e'), backgroundColor: AppColors.danger),
        );
      }
    } finally {
      if (mounted) setState(() => _uploadingAvatar = false);
    }
  }

  Future<void> _editPersonalInfo(ShipperUserProfile? current) async {
    final auth = context.read<AuthProvider>();
    final shipper = context.read<ShipperProvider>();
    final initial =
        current ??
        ShipperUserProfile(
          id: 0,
          username: auth.currentUsername ?? '',
          fullName: auth.currentFullName ?? '',
        );

    final updated = await showDialog<bool>(
      context: context,
      builder: (_) => _EditPersonalInfoDialog(
        initial: initial,
        onSave: (payload) async {
          final result = await shipper.updateUserProfile(
            fullName: payload.fullName,
            phone: payload.phone,
            avatarUrl: initial.rawAvatarUrl,
            bio: payload.bio,
            gender: payload.gender,
            dateOfBirth: payload.dateOfBirth,
          );
          await auth.updateCachedFullName(result.fullName);
        },
      ),
    );

    if (updated == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã lưu thông tin tài khoản cá nhân')),
      );
    }
  }

  Future<void> _editWorkInfo(ShipperWorkProfile? current) async {
    final shipper = context.read<ShipperProvider>();
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => _EditWorkInfoDialog(
        initial: current ?? const ShipperWorkProfile(),
        onSave: (payload) async {
          await shipper.updateWorkProfile(
            vehicleType: payload.vehicleType,
            vehiclePlate: payload.vehiclePlate,
            serviceArea: payload.serviceArea,
            emergencyContactName: payload.emergencyContactName,
            emergencyContactPhone: payload.emergencyContactPhone,
            workShift: payload.workShift,
          );
        },
      ),
    );

    if (saved == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã lưu thông tin tài xế & phương tiện')),
      );
    }
  }

  Future<void> _openChangePasswordDialog() async {
    final shipper = context.read<ShipperProvider>();
    final auth = context.read<AuthProvider>();

    final changed = await showDialog<bool>(
      context: context,
      builder: (_) => _ChangePasswordDialog(
        onSubmit: (currentPassword, newPassword) async {
          await shipper.changePassword(
            currentPassword: currentPassword,
            newPassword: newPassword,
          );
        },
      ),
    );

    if (changed == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Đã đổi mật khẩu. Vui lòng đăng nhập lại.'),
          backgroundColor: AppColors.success,
        ),
      );
      shipper.clearSession();
      await auth.logout();
    }
  }

  Future<void> _openDebugServerDialog() async {
    final controller = TextEditingController(text: ApiConstants.baseUrl);
    final updated = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Máy chủ kết nối (Debug)'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(
            labelText: 'Địa chỉ Backend URL',
            hintText: 'https://api.dermascan.world',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () {
              try {
                ApiConstants.setBaseUrl(controller.text);
                Navigator.pop(ctx, true);
              } on FormatException catch (e) {
                ScaffoldMessenger.of(
                  ctx,
                ).showSnackBar(SnackBar(content: Text(e.message)));
              }
            },
            child: const Text('Áp dụng'),
          ),
        ],
      ),
    );
    Future<void>.delayed(
      const Duration(milliseconds: 250),
      controller.dispose,
    );
    if (updated == true && mounted) {
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Đã đổi máy chủ: ${ApiConstants.baseUrl}')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final shipper = context.watch<ShipperProvider>();
    final user = shipper.userProfile;
    final work = shipper.workProfile;

    final displayName = (user?.fullName.isNotEmpty ?? false)
        ? user!.fullName
        : (auth.currentFullName ?? auth.currentUsername ?? 'Tài xế giao hàng');
    final displayUsername = (user?.username.isNotEmpty ?? false)
        ? user!.username
        : (auth.currentUsername ?? '');

    return RefreshIndicator(
      onRefresh: _loadProfile,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: ShipperLayout.pagePadding,
        children: [
          Text(
            'Tài khoản & Cài đặt',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.bold,
              color: AppColors.textMain,
            ),
          ),
          const SizedBox(height: 12),

          if (shipper.isProfileLoading && user == null)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 20),
              child: Center(child: CircularProgressIndicator()),
            ),

          if (shipper.profileError != null && user == null)
            Card(
              color: AppColors.dangerLight,
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Không tải được đầy đủ hồ sơ từ máy chủ: ${shipper.profileError}',
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.danger,
                      ),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: _loadProfile,
                      icon: const Icon(Icons.refresh_rounded, size: 16),
                      label: const Text('Thử lại'),
                    ),
                  ],
                ),
              ),
            ),

          // Hero Profile Banner
          _buildHeroCard(
            displayName: displayName,
            username: displayUsername,
            user: user,
          ),

          // Personal Account Information
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _SectionHeader(
                    icon: Icons.badge_outlined,
                    title: 'Thông tin tài khoản cá nhân',
                    actionLabel: 'Chỉnh sửa',
                    onAction: () => _editPersonalInfo(user),
                  ),
                  const SizedBox(height: 6),
                  _InfoRow(label: 'Họ và tên', value: displayName),
                  if (displayUsername.isNotEmpty)
                    _InfoRow(
                      label: 'Tên đăng nhập',
                      value: '@$displayUsername',
                    ),
                  _InfoRow(
                    label: 'Số điện thoại',
                    value: user?.phone,
                    emptyHint: 'Chưa bổ sung',
                  ),
                  _InfoRow(
                    label: 'Email',
                    value: user?.email,
                    emptyHint: 'Chưa cập nhật',
                  ),
                  _InfoRow(
                    label: 'Giới tính',
                    value: user?.gender != null ? user!.genderLabel : null,
                    emptyHint: 'Chưa cập nhật',
                  ),
                  _InfoRow(
                    label: 'Ngày sinh',
                    value: _formatBirthDate(user?.dateOfBirth),
                    emptyHint: 'Chưa cập nhật',
                  ),
                  _InfoRow(
                    label: 'Giới thiệu',
                    value: user?.bio,
                    emptyHint: 'Chưa có ghi chú cá nhân',
                  ),
                ],
              ),
            ),
          ),

          // Shipper Operational Info
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _SectionHeader(
                    icon: Icons.two_wheeler_outlined,
                    title: 'Thông tin tài xế & phương tiện',
                    actionLabel: (work?.hasAnyInfo ?? false)
                        ? 'Cập nhật'
                        : 'Bổ sung',
                    onAction: () => _editWorkInfo(work),
                  ),
                  if (!(work?.hasAnyInfo ?? false)) ...[
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppColors.primarySurface.withValues(alpha: 0.45),
                        borderRadius: BorderRadius.circular(
                          ShipperLayout.innerRadius,
                        ),
                      ),
                      child: const Text(
                        'Chưa thiết lập phương tiện, biển số xe hoặc khu vực giao. Nhấn "Bổ sung" để thêm thông tin shipper.',
                        style: TextStyle(
                          fontSize: 13,
                          color: AppColors.primaryDark,
                        ),
                      ),
                    ),
                  ] else ...[
                    const SizedBox(height: 6),
                    _InfoRow(
                      label: 'Loại phương tiện',
                      value: work?.vehicleType,
                      emptyHint: 'Chưa cập nhật',
                    ),
                    _InfoRow(
                      label: 'Biển số xe',
                      value: work?.vehiclePlate,
                      emptyHint: 'Chưa cập nhật',
                    ),
                    _InfoRow(
                      label: 'Khu vực giao phụ trách',
                      value: work?.serviceArea,
                      emptyHint: 'Chưa cập nhật',
                    ),
                    _InfoRow(
                      label: 'Ca làm việc',
                      value: work?.workShift,
                      emptyHint: 'Chưa cập nhật',
                    ),
                    _InfoRow(
                      label: 'Liên hệ khẩn cấp',
                      value: _formatEmergencyContact(work),
                      emptyHint: 'Chưa bổ sung',
                    ),
                  ],
                ],
              ),
            ),
          ),

          // Personal App Settings (live reactive)
          ListenableBuilder(
            listenable: ShipperSettingsStore.instance,
            builder: (context, _) {
              final settings = ShipperSettingsStore.instance;
              return Card(
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _SectionHeader(
                        icon: Icons.tune_rounded,
                        title: 'Cài đặt cá nhân',
                        actionLabel: 'Mặc định',
                        onAction: () async {
                          await settings.resetToDefaults();
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text('Đã khôi phục cài đặt mặc định'),
                              ),
                            );
                          }
                        },
                      ),
                      const SizedBox(height: 4),
                      SwitchListTile.adaptive(
                        contentPadding: EdgeInsets.zero,
                        title: const Text(
                          'Tự động làm mới đơn & thông báo',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        subtitle: const Text(
                          'Tự động kiểm tra đơn mới khi mở tab Đơn giao',
                          style: TextStyle(
                            fontSize: 12,
                            color: AppColors.textMuted,
                          ),
                        ),
                        value: settings.autoRefreshEnabled,
                        onChanged: settings.setAutoRefreshEnabled,
                      ),
                      if (settings.autoRefreshEnabled) ...[
                        const SizedBox(height: 6),
                        const Text(
                          'Chu kỳ làm mới',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: AppColors.textMuted,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Wrap(
                          spacing: 8,
                          runSpacing: 6,
                          children: [
                            for (final sec
                                in ShipperSettingsStore.supportedIntervals)
                              ChoiceChip(
                                label: Text('$sec giây'),
                                selected:
                                    settings.refreshIntervalSeconds == sec,
                                showCheckmark: false,
                                selectedColor: AppColors.primarySurface,
                                onSelected: (_) =>
                                    settings.setRefreshIntervalSeconds(sec),
                              ),
                          ],
                        ),
                      ],
                      const Divider(height: 22),
                      const Text(
                        'Cỡ chữ hiển thị',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: AppColors.textMain,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Wrap(
                        spacing: 8,
                        runSpacing: 6,
                        children: [
                          for (final (label, scale) in const [
                            ('Nhỏ', 0.95),
                            ('Chuẩn', 1.0),
                            ('Lớn', 1.15),
                          ])
                            ChoiceChip(
                              label: Text(label),
                              selected: settings.textScaleFactor == scale,
                              showCheckmark: false,
                              selectedColor: AppColors.primarySurface,
                              onSelected: (_) =>
                                  settings.setTextScaleFactor(scale),
                            ),
                        ],
                      ),
                      const Divider(height: 22),
                      const Text(
                        'Khi bấm "Chỉ đường"',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: AppColors.textMain,
                        ),
                      ),
                      const SizedBox(height: 8),
                      DropdownButtonFormField<ShipperMapPreference>(
                        initialValue: settings.mapPreference,
                        isExpanded: true,
                        decoration: const InputDecoration(isDense: true),
                        items: [
                          for (final mode in ShipperMapPreference.values)
                            DropdownMenuItem(
                              value: mode,
                              child: Text(
                                mode.label,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                        ],
                        onChanged: (mode) {
                          if (mode != null) settings.setMapPreference(mode);
                        },
                      ),
                      const SizedBox(height: 8),
                      SwitchListTile.adaptive(
                        contentPadding: EdgeInsets.zero,
                        title: const Text(
                          'Hỏi xác nhận trước khi "Bắt đầu giao"',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        subtitle: const Text(
                          'Tránh chạm nhầm trạng thái khi đang di chuyển',
                          style: TextStyle(
                            fontSize: 12,
                            color: AppColors.textMuted,
                          ),
                        ),
                        value: settings.confirmBeforeStart,
                        onChanged: settings.setConfirmBeforeStart,
                      ),
                    ],
                  ),
                ),
              );
            },
          ),

          // Security & Session Actions
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(
                    Icons.lock_reset_rounded,
                    color: AppColors.primary,
                  ),
                  title: const Text(
                    'Đổi mật khẩu',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: const Text(
                    'Bảo mật tài khoản tài xế',
                    style: TextStyle(fontSize: 12, color: AppColors.textMuted),
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: _openChangePasswordDialog,
                ),
                if (kDebugMode) ...[
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(
                      Icons.settings_ethernet,
                      color: AppColors.primary,
                    ),
                    title: const Text(
                      'Máy chủ kết nối (Debug)',
                      style: TextStyle(fontWeight: FontWeight.w600),
                    ),
                    subtitle: Text(
                      ApiConstants.baseUrl,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.textMuted,
                      ),
                    ),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: _openDebugServerDialog,
                  ),
                ],
              ],
            ),
          ),

          const SizedBox(height: 6),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: () => confirmAndLogout(context),
              style: FilledButton.styleFrom(
                backgroundColor: AppColors.danger,
                foregroundColor: Colors.white,
              ),
              icon: const Icon(Icons.logout_rounded),
              label: const Text('Đăng xuất khỏi ca làm việc'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeroCard({
    required String displayName,
    required String username,
    required ShipperUserProfile? user,
  }) {
    final avatarUrl = user?.avatarUrl;
    final initial = displayName.trim().isNotEmpty
        ? displayName.trim()[0].toUpperCase()
        : 'S';

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppColors.primaryDark, AppColors.primary],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              CircleAvatar(
                radius: 32,
                backgroundColor: Colors.white.withValues(alpha: 0.2),
                child: _uploadingAvatar
                    ? const SizedBox.square(
                        dimension: 24,
                        child: CircularProgressIndicator(
                          color: Colors.white,
                          strokeWidth: 2.5,
                        ),
                      )
                    : avatarUrl != null
                    ? ClipOval(
                        child: CachedNetworkImage(
                          imageUrl: avatarUrl,
                          width: 64,
                          height: 64,
                          fit: BoxFit.cover,
                          placeholder: (_, _) => Text(
                            initial,
                            style: const TextStyle(
                              fontSize: 22,
                              fontWeight: FontWeight.bold,
                              color: Colors.white,
                            ),
                          ),
                          errorWidget: (_, _, _) => Text(
                            initial,
                            style: const TextStyle(
                              fontSize: 22,
                              fontWeight: FontWeight.bold,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      )
                    : Text(
                        initial,
                        style: const TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
              ),
              if (user != null)
                Positioned(
                  right: -4,
                  bottom: -4,
                  child: Material(
                    color: AppColors.card,
                    shape: const CircleBorder(),
                    elevation: 1,
                    child: InkWell(
                      customBorder: const CircleBorder(),
                      onTap: _uploadingAvatar
                          ? null
                          : () => _pickAndUploadAvatar(user),
                      child: const Padding(
                        padding: EdgeInsets.all(6),
                        child: Icon(
                          Icons.camera_alt_rounded,
                          size: 14,
                          color: AppColors.primary,
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  displayName,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                if (username.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    '@$username',
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppColors.primarySurface,
                    ),
                  ),
                ],
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  runSpacing: 4,
                  children: [
                    _HeroBadge(label: 'Tài xế giao hàng'),
                    if (user?.status != null)
                      _HeroBadge(label: user!.statusLabel),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String? _formatBirthDate(String? isoDate) {
    if (isoDate == null || isoDate.isEmpty) return null;
    final parsed = DateTime.tryParse(isoDate);
    if (parsed == null) return isoDate;
    return '${parsed.day.toString().padLeft(2, '0')}/'
        '${parsed.month.toString().padLeft(2, '0')}/'
        '${parsed.year}';
  }

  String? _formatEmergencyContact(ShipperWorkProfile? work) {
    if (work == null) return null;
    final name = work.emergencyContactName;
    final phone = work.emergencyContactPhone;
    if (name == null && phone == null) return null;
    if (name != null && phone != null) return '$name ($phone)';
    return name ?? phone;
  }
}

class _HeroBadge extends StatelessWidget {
  const _HeroBadge({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({
    required this.icon,
    required this.title,
    required this.actionLabel,
    required this.onAction,
  });

  final IconData icon;
  final String title;
  final String actionLabel;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 20, color: AppColors.primary),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            title,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: AppColors.textMain,
            ),
          ),
        ),
        TextButton(onPressed: onAction, child: Text(actionLabel)),
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.label,
    required this.value,
    this.emptyHint = 'Chưa cập nhật',
  });

  final String label;
  final String? value;
  final String emptyHint;

  @override
  Widget build(BuildContext context) {
    final hasValue = value != null && value!.trim().isNotEmpty;
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(fontSize: 12, color: AppColors.textMuted),
          ),
          const SizedBox(height: 2),
          Text(
            hasValue ? value!.trim() : emptyHint,
            style: TextStyle(
              fontSize: 14,
              fontWeight: hasValue ? FontWeight.w600 : FontWeight.normal,
              color: hasValue ? AppColors.textMain : AppColors.textMuted,
              fontStyle: hasValue ? FontStyle.normal : FontStyle.italic,
            ),
          ),
        ],
      ),
    );
  }
}

class _PersonalFormPayload {
  const _PersonalFormPayload({
    required this.fullName,
    required this.phone,
    required this.bio,
    this.gender,
    this.dateOfBirth,
  });

  final String fullName;
  final String phone;
  final String bio;
  final String? gender;
  final String? dateOfBirth;
}

class _EditPersonalInfoDialog extends StatefulWidget {
  const _EditPersonalInfoDialog({
    required this.initial,
    required this.onSave,
  });

  final ShipperUserProfile initial;
  final Future<void> Function(_PersonalFormPayload payload) onSave;

  @override
  State<_EditPersonalInfoDialog> createState() =>
      _EditPersonalInfoDialogState();
}

class _EditPersonalInfoDialogState extends State<_EditPersonalInfoDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _fullNameController;
  late final TextEditingController _phoneController;
  late final TextEditingController _bioController;
  String? _gender;
  String? _dateOfBirth;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fullNameController = TextEditingController(text: widget.initial.fullName);
    _phoneController = TextEditingController(text: widget.initial.phone ?? '');
    _bioController = TextEditingController(text: widget.initial.bio ?? '');
    _gender = widget.initial.gender;
    _dateOfBirth = widget.initial.dateOfBirth;
  }

  @override
  void dispose() {
    _fullNameController.dispose();
    _phoneController.dispose();
    _bioController.dispose();
    super.dispose();
  }

  Future<void> _pickBirthDate() async {
    final now = DateTime.now();
    final yesterday = DateTime(now.year, now.month, now.day - 1);
    final initialDate = DateTime.tryParse(_dateOfBirth ?? '') ??
        DateTime(now.year - 25, now.month, now.day);
    final picked = await showDatePicker(
      context: context,
      initialDate: initialDate.isAfter(yesterday) ? yesterday : initialDate,
      firstDate: DateTime(1950),
      lastDate: yesterday,
    );
    if (picked != null && mounted) {
      setState(() {
        _dateOfBirth =
            '${picked.year.toString().padLeft(4, '0')}-'
            '${picked.month.toString().padLeft(2, '0')}-'
            '${picked.day.toString().padLeft(2, '0')}';
      });
    }
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.onSave(
        _PersonalFormPayload(
          fullName: _fullNameController.text.trim(),
          phone: _phoneController.text.trim(),
          bio: _bioController.text.trim(),
          gender: _gender,
          dateOfBirth: _dateOfBirth,
        ),
      );
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _saving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Chỉnh sửa thông tin cá nhân'),
      content: SizedBox(
        width: 440,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_error != null) ...[
                  Text(
                    _error!,
                    style: const TextStyle(
                      color: AppColors.danger,
                      fontSize: 13,
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
                TextFormField(
                  controller: _fullNameController,
                  maxLength: 100,
                  decoration: const InputDecoration(labelText: 'Họ và tên *'),
                  validator: (val) => val == null || val.trim().isEmpty
                      ? 'Vui lòng nhập họ tên'
                      : null,
                ),
                const SizedBox(height: 10),
                TextFormField(
                  controller: _phoneController,
                  maxLength: 20,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(
                    labelText: 'Số điện thoại',
                    hintText: '0901234567',
                  ),
                  validator: (val) {
                    final clean = val?.trim() ?? '';
                    if (clean.isEmpty) return null;
                    if (!RegExp(r'^(0|\+84)[35789]\d{8}$').hasMatch(clean)) {
                      return 'Số điện thoại di động VN không hợp lệ';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 8),
                const Text(
                  'Giới tính',
                  style: TextStyle(fontSize: 12, color: AppColors.textMuted),
                ),
                const SizedBox(height: 6),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final (code, label) in const [
                      ('MALE', 'Nam'),
                      ('FEMALE', 'Nữ'),
                      ('OTHER', 'Khác'),
                    ])
                      ChoiceChip(
                        label: Text(label),
                        selected: _gender == code,
                        showCheckmark: false,
                        selectedColor: AppColors.primarySurface,
                        onSelected: (selected) {
                          setState(() => _gender = selected ? code : null);
                        },
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                const Text(
                  'Ngày sinh',
                  style: TextStyle(fontSize: 12, color: AppColors.textMuted),
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _pickBirthDate,
                        icon: const Icon(Icons.cake_outlined, size: 18),
                        label: Text(
                          _dateOfBirth ?? 'Chọn ngày sinh',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ),
                    if (_dateOfBirth != null) ...[
                      const SizedBox(width: 6),
                      IconButton(
                        tooltip: 'Xóa ngày sinh',
                        onPressed: () => setState(() => _dateOfBirth = null),
                        icon: const Icon(Icons.clear_rounded, size: 18),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _bioController,
                  maxLength: 500,
                  maxLines: 3,
                  decoration: const InputDecoration(
                    labelText: 'Giới thiệu / Ghi chú',
                    hintText: 'Kinh nghiệm giao hàng, lưu ý liên hệ…',
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _saving ? null : () => Navigator.pop(context, false),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: _saving ? null : _submit,
          child: Text(_saving ? 'Đang lưu…' : 'Lưu thay đổi'),
        ),
      ],
    );
  }
}

class _WorkFormPayload {
  const _WorkFormPayload({
    required this.vehicleType,
    required this.vehiclePlate,
    required this.serviceArea,
    required this.emergencyContactName,
    required this.emergencyContactPhone,
    required this.workShift,
  });

  final String vehicleType;
  final String vehiclePlate;
  final String serviceArea;
  final String emergencyContactName;
  final String emergencyContactPhone;
  final String workShift;
}

class _EditWorkInfoDialog extends StatefulWidget {
  const _EditWorkInfoDialog({required this.initial, required this.onSave});

  final ShipperWorkProfile initial;
  final Future<void> Function(_WorkFormPayload payload) onSave;

  @override
  State<_EditWorkInfoDialog> createState() => _EditWorkInfoDialogState();
}

class _EditWorkInfoDialogState extends State<_EditWorkInfoDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _vehicleTypeController;
  late final TextEditingController _vehiclePlateController;
  late final TextEditingController _serviceAreaController;
  late final TextEditingController _workShiftController;
  late final TextEditingController _emergencyNameController;
  late final TextEditingController _emergencyPhoneController;
  bool _saving = false;
  String? _error;

  static const _vehiclePresets = [
    'Xe máy',
    'Xe tay ga',
    'Ô tô tải nhỏ',
    'Xe đạp điện',
  ];

  static const _shiftPresets = [
    'Sáng (08:00 - 12:00)',
    'Chiều (13:00 - 17:30)',
    'Cả ngày',
    'Linh hoạt',
  ];

  @override
  void initState() {
    super.initState();
    _vehicleTypeController = TextEditingController(
      text: widget.initial.vehicleType ?? '',
    );
    _vehiclePlateController = TextEditingController(
      text: widget.initial.vehiclePlate ?? '',
    );
    _serviceAreaController = TextEditingController(
      text: widget.initial.serviceArea ?? '',
    );
    _workShiftController = TextEditingController(
      text: widget.initial.workShift ?? '',
    );
    _emergencyNameController = TextEditingController(
      text: widget.initial.emergencyContactName ?? '',
    );
    _emergencyPhoneController = TextEditingController(
      text: widget.initial.emergencyContactPhone ?? '',
    );
  }

  @override
  void dispose() {
    _vehicleTypeController.dispose();
    _vehiclePlateController.dispose();
    _serviceAreaController.dispose();
    _workShiftController.dispose();
    _emergencyNameController.dispose();
    _emergencyPhoneController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.onSave(
        _WorkFormPayload(
          vehicleType: _vehicleTypeController.text.trim(),
          vehiclePlate: _vehiclePlateController.text.trim(),
          serviceArea: _serviceAreaController.text.trim(),
          emergencyContactName: _emergencyNameController.text.trim(),
          emergencyContactPhone: _emergencyPhoneController.text.trim(),
          workShift: _workShiftController.text.trim(),
        ),
      );
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _saving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Thông tin tài xế & phương tiện'),
      content: SizedBox(
        width: 440,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_error != null) ...[
                  Text(
                    _error!,
                    style: const TextStyle(
                      color: AppColors.danger,
                      fontSize: 13,
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final preset in _vehiclePresets)
                      ActionChip(
                        label: Text(preset, style: const TextStyle(fontSize: 12)),
                        onPressed: () {
                          _vehicleTypeController.text = preset;
                        },
                      ),
                  ],
                ),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _vehicleTypeController,
                  maxLength: 40,
                  decoration: const InputDecoration(
                    labelText: 'Loại phương tiện',
                    hintText: 'Ví dụ: Xe máy',
                  ),
                ),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _vehiclePlateController,
                  maxLength: 30,
                  textCapitalization: TextCapitalization.characters,
                  decoration: const InputDecoration(
                    labelText: 'Biển số xe',
                    hintText: 'Ví dụ: 59X1-123.45',
                  ),
                ),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _serviceAreaController,
                  maxLength: 150,
                  decoration: const InputDecoration(
                    labelText: 'Khu vực giao phụ trách',
                    hintText: 'Ví dụ: Quận 1, Quận 3, Bình Thạnh',
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final preset in _shiftPresets)
                      ActionChip(
                        label: Text(preset, style: const TextStyle(fontSize: 12)),
                        onPressed: () {
                          _workShiftController.text = preset;
                        },
                      ),
                  ],
                ),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _workShiftController,
                  maxLength: 60,
                  decoration: const InputDecoration(labelText: 'Ca làm việc'),
                ),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _emergencyNameController,
                  maxLength: 100,
                  decoration: const InputDecoration(
                    labelText: 'Tên liên hệ khẩn cấp',
                  ),
                ),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _emergencyPhoneController,
                  maxLength: 20,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(
                    labelText: 'SĐT liên hệ khẩn cấp',
                    hintText: '0912345678',
                  ),
                  validator: (val) {
                    final clean = val?.trim() ?? '';
                    if (clean.isEmpty) return null;
                    if (!RegExp(r'^(0|\+84)[35789]\d{8}$').hasMatch(clean)) {
                      return 'Số điện thoại không hợp lệ';
                    }
                    return null;
                  },
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _saving ? null : () => Navigator.pop(context, false),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: _saving ? null : _submit,
          child: Text(_saving ? 'Đang lưu…' : 'Lưu thông tin'),
        ),
      ],
    );
  }
}

class _ChangePasswordDialog extends StatefulWidget {
  const _ChangePasswordDialog({required this.onSubmit});

  final Future<void> Function(String currentPassword, String newPassword)
  onSubmit;

  @override
  State<_ChangePasswordDialog> createState() => _ChangePasswordDialogState();
}

class _ChangePasswordDialogState extends State<_ChangePasswordDialog> {
  final _formKey = GlobalKey<FormState>();
  final _currentController = TextEditingController();
  final _newController = TextEditingController();
  final _confirmController = TextEditingController();
  bool _obscureCurrent = true;
  bool _obscureNew = true;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _currentController.dispose();
    _newController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.onSubmit(_currentController.text, _newController.text);
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _saving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Đổi mật khẩu'),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (_error != null) ...[
                  Text(
                    _error!,
                    style: const TextStyle(
                      color: AppColors.danger,
                      fontSize: 13,
                    ),
                  ),
                  const SizedBox(height: 10),
                ],
                TextFormField(
                  controller: _currentController,
                  obscureText: _obscureCurrent,
                  maxLength: 128,
                  decoration: InputDecoration(
                    labelText: 'Mật khẩu hiện tại *',
                    suffixIcon: IconButton(
                      icon: Icon(
                        _obscureCurrent
                            ? Icons.visibility_off
                            : Icons.visibility,
                      ),
                      onPressed: () =>
                          setState(() => _obscureCurrent = !_obscureCurrent),
                    ),
                  ),
                  validator: (val) => (val == null || val.isEmpty)
                      ? 'Nhập mật khẩu hiện tại'
                      : null,
                ),
                const SizedBox(height: 10),
                TextFormField(
                  controller: _newController,
                  obscureText: _obscureNew,
                  maxLength: 128,
                  decoration: InputDecoration(
                    labelText: 'Mật khẩu mới (tối thiểu 8 ký tự) *',
                    suffixIcon: IconButton(
                      icon: Icon(
                        _obscureNew ? Icons.visibility_off : Icons.visibility,
                      ),
                      onPressed: () =>
                          setState(() => _obscureNew = !_obscureNew),
                    ),
                  ),
                  validator: (val) {
                    if (val == null || val.length < 8) {
                      return 'Mật khẩu mới cần ít nhất 8 ký tự';
                    }
                    if (val == _currentController.text) {
                      return 'Mật khẩu mới phải khác mật khẩu hiện tại';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 10),
                TextFormField(
                  controller: _confirmController,
                  obscureText: _obscureNew,
                  maxLength: 128,
                  decoration: const InputDecoration(
                    labelText: 'Xác nhận mật khẩu mới *',
                  ),
                  validator: (val) => val != _newController.text
                      ? 'Mật khẩu xác nhận không khớp'
                      : null,
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _saving ? null : () => Navigator.pop(context, false),
          child: const Text('Hủy'),
        ),
        FilledButton(
          onPressed: _saving ? null : _submit,
          child: Text(_saving ? 'Đang cập nhật…' : 'Xác nhận đổi'),
        ),
      ],
    );
  }
}

