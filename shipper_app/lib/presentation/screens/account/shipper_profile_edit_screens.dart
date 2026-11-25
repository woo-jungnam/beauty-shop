import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';
import '../../../data/models/shipper_account_model.dart';

class PersonalFormPayload {
  const PersonalFormPayload({
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

class WorkFormPayload {
  const WorkFormPayload({
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

class ShipperEditPersonalScreen extends StatefulWidget {
  const ShipperEditPersonalScreen({
    super.key,
    required this.initial,
    required this.onSave,
    this.onChangeAvatar,
  });

  final ShipperUserProfile initial;
  final Future<void> Function(PersonalFormPayload payload) onSave;
  final Future<String?> Function()? onChangeAvatar;

  @override
  State<ShipperEditPersonalScreen> createState() =>
      _ShipperEditPersonalScreenState();
}

class _ShipperEditPersonalScreenState extends State<ShipperEditPersonalScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _fullNameController;
  late final TextEditingController _phoneController;
  late final TextEditingController _bioController;
  String? _gender;
  String? _dateOfBirth;
  String? _avatarUrl;
  bool _saving = false;
  bool _uploadingAvatar = false;
  bool _savedPop = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fullNameController = TextEditingController(text: widget.initial.fullName);
    _phoneController = TextEditingController(text: widget.initial.phone ?? '');
    _bioController = TextEditingController(text: widget.initial.bio ?? '');
    _gender = widget.initial.gender;
    _dateOfBirth = widget.initial.dateOfBirth;
    _avatarUrl = widget.initial.avatarUrl;
  }

  @override
  void dispose() {
    _fullNameController.dispose();
    _phoneController.dispose();
    _bioController.dispose();
    super.dispose();
  }

  bool get _hasChanges =>
      _fullNameController.text.trim() != widget.initial.fullName.trim() ||
      _phoneController.text.trim() != (widget.initial.phone ?? '').trim() ||
      _bioController.text.trim() != (widget.initial.bio ?? '').trim() ||
      _gender != widget.initial.gender ||
      _dateOfBirth != widget.initial.dateOfBirth;

  Future<void> _handleAvatarTap() async {
    if (widget.onChangeAvatar == null || _uploadingAvatar || _saving) return;
    setState(() {
      _uploadingAvatar = true;
      _error = null;
    });
    try {
      final newUrl = await widget.onChangeAvatar!.call();
      if (mounted && newUrl != null && newUrl.isNotEmpty) {
        setState(() => _avatarUrl = newUrl);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = e.toString());
      }
    } finally {
      if (mounted) setState(() => _uploadingAvatar = false);
    }
  }

  Future<void> _pickBirthDate() async {
    final now = DateTime.now();
    final yesterday = DateTime(now.year, now.month, now.day - 1);
    final initialDate =
        DateTime.tryParse(_dateOfBirth ?? '') ??
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

  String _formatDateLabel(String? isoDate) {
    if (isoDate == null || isoDate.isEmpty) return 'Chọn ngày sinh';
    final parsed = DateTime.tryParse(isoDate);
    if (parsed == null) return isoDate;
    return '${parsed.day.toString().padLeft(2, '0')}/'
        '${parsed.month.toString().padLeft(2, '0')}/'
        '${parsed.year}';
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.onSave(
        PersonalFormPayload(
          fullName: _fullNameController.text.trim(),
          phone: _phoneController.text.trim(),
          bio: _bioController.text.trim(),
          gender: _gender,
          dateOfBirth: _dateOfBirth,
        ),
      );
      if (mounted) {
        _savedPop = true;
        Navigator.pop(context, true);
      }
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
    final displayName = _fullNameController.text.trim().isNotEmpty
        ? _fullNameController.text.trim()
        : widget.initial.fullName;
    final avatarInitial = displayName.trim().isNotEmpty
        ? displayName.trim()[0].toUpperCase()
        : 'S';

    return _FullScreenEditScaffold(
      title: 'Chỉnh sửa thông tin cá nhân',
      formKey: _formKey,
      saving: _saving,
      hasUnsavedChanges: !_savedPop && _hasChanges,
      errorMessage: _error,
      submitLabel: 'Lưu thay đổi',
      onSubmit: _submit,
      sections: [
        _AccountIdentityBanner(
          avatarUrl: _avatarUrl,
          avatarInitial: avatarInitial,
          displayName: displayName.isNotEmpty ? displayName : 'Tài xế BeautyShip',
          username: widget.initial.username,
          email: widget.initial.email,
          uploadingAvatar: _uploadingAvatar,
          onTapAvatar: widget.onChangeAvatar != null ? _handleAvatarTap : null,
        ),
        _FormSectionCard(
          icon: Icons.badge_outlined,
          title: 'Thông tin cơ bản',
          subtitle: 'Hiển thị trên hồ sơ shipper và biên bản giao nhận',
          children: [
            _ResponsiveFieldPair(
              first: TextFormField(
                controller: _fullNameController,
                maxLength: 100,
                textInputAction: TextInputAction.next,
                autovalidateMode: AutovalidateMode.onUserInteraction,
                decoration: const InputDecoration(
                  labelText: 'Họ và tên *',
                  prefixIcon: Icon(Icons.person_outline_rounded),
                ),
                onChanged: (_) => setState(() {}),
                validator: (val) => val == null || val.trim().isEmpty
                    ? 'Vui lòng nhập họ tên'
                    : null,
              ),
              second: TextFormField(
                controller: _phoneController,
                maxLength: 20,
                keyboardType: TextInputType.phone,
                textInputAction: TextInputAction.next,
                autovalidateMode: AutovalidateMode.onUserInteraction,
                decoration: const InputDecoration(
                  labelText: 'Số điện thoại',
                  hintText: '0901234567',
                  prefixIcon: Icon(Icons.phone_outlined),
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
            ),
          ],
        ),
        _FormSectionCard(
          icon: Icons.person_search_outlined,
          title: 'Hồ sơ bổ sung',
          subtitle: 'Thông tin cá nhân tùy chọn giúp bộ phận điều phối liên hệ thuận tiện',
          children: [
            _ResponsiveFieldPair(
              first: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Giới tính',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textMain,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
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
                ],
              ),
              second: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Ngày sinh',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textMain,
                    ),
                  ),
                  const SizedBox(height: 8),
                  _ResponsiveDatePickerField(
                    label: _formatDateLabel(_dateOfBirth),
                    hasValue: _dateOfBirth != null,
                    onPick: _pickBirthDate,
                    onClear: _dateOfBirth != null
                        ? () => setState(() => _dateOfBirth = null)
                        : null,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _bioController,
              maxLength: 500,
              minLines: 3,
              maxLines: 5,
              textInputAction: TextInputAction.newline,
              decoration: const InputDecoration(
                labelText: 'Giới thiệu / Ghi chú cá nhân',
                hintText: 'Kinh nghiệm giao hàng, khu vực quen thuộc, lưu ý liên hệ…',
                alignLabelWithHint: true,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class ShipperEditWorkScreen extends StatefulWidget {
  const ShipperEditWorkScreen({
    super.key,
    required this.initial,
    required this.onSave,
  });

  final ShipperWorkProfile initial;
  final Future<void> Function(WorkFormPayload payload) onSave;

  @override
  State<ShipperEditWorkScreen> createState() => _ShipperEditWorkScreenState();
}

class _ShipperEditWorkScreenState extends State<ShipperEditWorkScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _vehicleTypeController;
  late final TextEditingController _vehiclePlateController;
  late final TextEditingController _serviceAreaController;
  late final TextEditingController _workShiftController;
  late final TextEditingController _emergencyNameController;
  late final TextEditingController _emergencyPhoneController;
  bool _saving = false;
  bool _savedPop = false;
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

  bool get _hasChanges =>
      _vehicleTypeController.text.trim() !=
          (widget.initial.vehicleType ?? '').trim() ||
      _vehiclePlateController.text.trim().toUpperCase() !=
          (widget.initial.vehiclePlate ?? '').trim().toUpperCase() ||
      _serviceAreaController.text.trim() !=
          (widget.initial.serviceArea ?? '').trim() ||
      _workShiftController.text.trim() !=
          (widget.initial.workShift ?? '').trim() ||
      _emergencyNameController.text.trim() !=
          (widget.initial.emergencyContactName ?? '').trim() ||
      _emergencyPhoneController.text.trim() !=
          (widget.initial.emergencyContactPhone ?? '').trim();

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.onSave(
        WorkFormPayload(
          vehicleType: _vehicleTypeController.text.trim(),
          vehiclePlate: _vehiclePlateController.text.trim(),
          serviceArea: _serviceAreaController.text.trim(),
          emergencyContactName: _emergencyNameController.text.trim(),
          emergencyContactPhone: _emergencyPhoneController.text.trim(),
          workShift: _workShiftController.text.trim(),
        ),
      );
      if (mounted) {
        _savedPop = true;
        Navigator.pop(context, true);
      }
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
    return _FullScreenEditScaffold(
      title: 'Thông tin tài xế & phương tiện',
      formKey: _formKey,
      saving: _saving,
      hasUnsavedChanges: !_savedPop && _hasChanges,
      errorMessage: _error,
      submitLabel: 'Lưu thông tin',
      onSubmit: _submit,
      sections: [
        _FormSectionCard(
          icon: Icons.two_wheeler_outlined,
          title: 'Phương tiện giao hàng',
          subtitle: 'Chọn nhanh hoặc tự nhập loại xe và biển số đang sử dụng',
          children: [
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final preset in _vehiclePresets)
                  ChoiceChip(
                    label: Text(preset),
                    selected: _vehicleTypeController.text.trim() == preset,
                    showCheckmark: false,
                    selectedColor: AppColors.primarySurface,
                    onSelected: (_) {
                      setState(() {
                        _vehicleTypeController.text = preset;
                      });
                    },
                  ),
              ],
            ),
            const SizedBox(height: 12),
            _ResponsiveFieldPair(
              first: TextFormField(
                controller: _vehicleTypeController,
                maxLength: 40,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Loại phương tiện',
                  hintText: 'Ví dụ: Xe máy',
                  prefixIcon: Icon(Icons.two_wheeler_outlined),
                ),
                onChanged: (_) => setState(() {}),
              ),
              second: TextFormField(
                controller: _vehiclePlateController,
                maxLength: 30,
                textCapitalization: TextCapitalization.characters,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Biển số xe',
                  hintText: 'Ví dụ: 59X1-123.45',
                  prefixIcon: Icon(Icons.pin_outlined),
                ),
              ),
            ),
          ],
        ),
        _FormSectionCard(
          icon: Icons.map_outlined,
          title: 'Khu vực & Ca hoạt động',
          subtitle: 'Giúp điều phối đơn giao phù hợp tuyến đường và khung giờ trực',
          children: [
            TextFormField(
              controller: _serviceAreaController,
              maxLength: 150,
              minLines: 1,
              maxLines: 2,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(
                labelText: 'Khu vực giao phụ trách',
                hintText: 'Ví dụ: Quận 1, Quận 3, Bình Thạnh',
                prefixIcon: Icon(Icons.location_on_outlined),
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Gợi ý ca làm việc',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.textMain,
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final preset in _shiftPresets)
                  ChoiceChip(
                    label: Text(preset),
                    selected: _workShiftController.text.trim() == preset,
                    showCheckmark: false,
                    selectedColor: AppColors.primarySurface,
                    onSelected: (_) {
                      setState(() {
                        _workShiftController.text = preset;
                      });
                    },
                  ),
              ],
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _workShiftController,
              maxLength: 60,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(
                labelText: 'Ca làm việc',
                hintText: 'Nhập khung giờ hoặc chọn gợi ý phía trên',
                prefixIcon: Icon(Icons.schedule_outlined),
              ),
              onChanged: (_) => setState(() {}),
            ),
          ],
        ),
        _FormSectionCard(
          icon: Icons.contact_emergency_outlined,
          title: 'Liên hệ khẩn cấp',
          subtitle: 'Thông tin người thân hoặc đầu mối hỗ trợ khi xảy ra sự cố trên đường',
          children: [
            _ResponsiveFieldPair(
              first: TextFormField(
                controller: _emergencyNameController,
                maxLength: 100,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Tên liên hệ khẩn cấp',
                  prefixIcon: Icon(Icons.person_pin_outlined),
                ),
              ),
              second: TextFormField(
                controller: _emergencyPhoneController,
                maxLength: 20,
                keyboardType: TextInputType.phone,
                textInputAction: TextInputAction.done,
                autovalidateMode: AutovalidateMode.onUserInteraction,
                decoration: const InputDecoration(
                  labelText: 'SĐT liên hệ khẩn cấp',
                  hintText: '0912345678',
                  prefixIcon: Icon(Icons.phone_in_talk_outlined),
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
            ),
          ],
        ),
      ],
    );
  }
}

class ShipperChangePasswordScreen extends StatefulWidget {
  const ShipperChangePasswordScreen({super.key, required this.onSubmit});

  final Future<void> Function(String currentPassword, String newPassword)
  onSubmit;

  @override
  State<ShipperChangePasswordScreen> createState() =>
      _ShipperChangePasswordScreenState();
}

class _ShipperChangePasswordScreenState
    extends State<ShipperChangePasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _currentController = TextEditingController();
  final _newController = TextEditingController();
  final _confirmController = TextEditingController();
  bool _obscureCurrent = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;
  bool _saving = false;
  bool _savedPop = false;
  String? _error;

  @override
  void dispose() {
    _currentController.dispose();
    _newController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  bool get _hasChanges =>
      _currentController.text.isNotEmpty ||
      _newController.text.isNotEmpty ||
      _confirmController.text.isNotEmpty;

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate() || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.onSubmit(_currentController.text, _newController.text);
      if (mounted) {
        _savedPop = true;
        Navigator.pop(context, true);
      }
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
    return _FullScreenEditScaffold(
      title: 'Đổi mật khẩu',
      formKey: _formKey,
      saving: _saving,
      hasUnsavedChanges: !_savedPop && _hasChanges,
      errorMessage: _error,
      submitLabel: 'Xác nhận đổi',
      onSubmit: _submit,
      sections: [
        _FormSectionCard(
          icon: Icons.lock_reset_rounded,
          title: 'Bảo mật tài khoản tài xế',
          subtitle: 'Sau khi đổi mật khẩu thành công, bạn sẽ cần đăng nhập lại trên thiết bị.',
          children: [
            TextFormField(
              controller: _currentController,
              obscureText: _obscureCurrent,
              maxLength: 128,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: 'Mật khẩu hiện tại *',
                prefixIcon: const Icon(Icons.lock_outline_rounded),
                suffixIcon: IconButton(
                  tooltip: _obscureCurrent ? 'Hiện mật khẩu' : 'Ẩn mật khẩu',
                  icon: Icon(
                    _obscureCurrent ? Icons.visibility_off : Icons.visibility,
                  ),
                  onPressed: () =>
                      setState(() => _obscureCurrent = !_obscureCurrent),
                ),
              ),
              validator: (val) => (val == null || val.isEmpty)
                  ? 'Nhập mật khẩu hiện tại'
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _newController,
              obscureText: _obscureNew,
              maxLength: 128,
              textInputAction: TextInputAction.next,
              decoration: InputDecoration(
                labelText: 'Mật khẩu mới (tối thiểu 8 ký tự) *',
                prefixIcon: const Icon(Icons.vpn_key_outlined),
                suffixIcon: IconButton(
                  tooltip: _obscureNew ? 'Hiện mật khẩu' : 'Ẩn mật khẩu',
                  icon: Icon(
                    _obscureNew ? Icons.visibility_off : Icons.visibility,
                  ),
                  onPressed: () => setState(() => _obscureNew = !_obscureNew),
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
            const SizedBox(height: 12),
            TextFormField(
              controller: _confirmController,
              obscureText: _obscureConfirm,
              maxLength: 128,
              textInputAction: TextInputAction.done,
              decoration: InputDecoration(
                labelText: 'Xác nhận mật khẩu mới *',
                prefixIcon: const Icon(Icons.check_circle_outline_rounded),
                suffixIcon: IconButton(
                  tooltip: _obscureConfirm ? 'Hiện mật khẩu' : 'Ẩn mật khẩu',
                  icon: Icon(
                    _obscureConfirm ? Icons.visibility_off : Icons.visibility,
                  ),
                  onPressed: () =>
                      setState(() => _obscureConfirm = !_obscureConfirm),
                ),
              ),
              validator: (val) => val != _newController.text
                  ? 'Mật khẩu xác nhận không khớp'
                  : null,
            ),
          ],
        ),
      ],
    );
  }
}

class _FullScreenEditScaffold extends StatelessWidget {
  const _FullScreenEditScaffold({
    required this.title,
    required this.formKey,
    required this.saving,
    required this.hasUnsavedChanges,
    required this.submitLabel,
    required this.onSubmit,
    required this.sections,
    this.errorMessage,
  });

  final String title;
  final GlobalKey<FormState> formKey;
  final bool saving;
  final bool hasUnsavedChanges;
  final String submitLabel;
  final VoidCallback onSubmit;
  final List<Widget> sections;
  final String? errorMessage;

  Future<bool> _confirmDiscard(BuildContext context) async {
    if (!hasUnsavedChanges || saving) return true;
    final discard = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Bỏ thay đổi chưa lưu?'),
        content: const Text(
          'Các thông tin bạn vừa chỉnh sửa sẽ không được lưu nếu thoát khỏi màn hình này.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Tiếp tục sửa'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.danger,
              foregroundColor: Colors.white,
            ),
            child: const Text('Bỏ thay đổi'),
          ),
        ],
      ),
    );
    return discard == true;
  }

  Future<void> _handleBackOrCancel(BuildContext context) async {
    if (saving) return;
    final shouldPop = await _confirmDiscard(context);
    if (shouldPop && context.mounted) {
      Navigator.pop(context, false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !hasUnsavedChanges && !saving,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        await _handleBackOrCancel(context);
      },
      child: Scaffold(
        backgroundColor: AppColors.background,
        appBar: AppBar(
          leading: IconButton(
            tooltip: 'Quay lại',
            icon: const Icon(Icons.arrow_back_rounded),
            onPressed: saving ? null : () => _handleBackOrCancel(context),
          ),
          title: Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
          ),
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(1),
            child: Container(height: 1, color: AppColors.border),
          ),
        ),
        body: SafeArea(
          child: Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: const EdgeInsets.fromLTRB(16, 14, 16, 20),
                  child: ShipperPageContainer(
                    child: Form(
                      key: formKey,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          if (errorMessage != null) ...[
                            Container(
                              padding: const EdgeInsets.all(12),
                              margin: const EdgeInsets.only(bottom: 12),
                              decoration: BoxDecoration(
                                color: AppColors.dangerLight,
                                borderRadius: BorderRadius.circular(
                                  ShipperLayout.innerRadius,
                                ),
                                border: Border.all(
                                  color: AppColors.danger.withValues(
                                    alpha: 0.35,
                                  ),
                                ),
                              ),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Icon(
                                    Icons.error_outline_rounded,
                                    color: AppColors.danger,
                                    size: 18,
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      errorMessage!,
                                      style: const TextStyle(
                                        color: AppColors.danger,
                                        fontSize: 13,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                          for (var i = 0; i < sections.length; i++) ...[
                            sections[i],
                            if (i < sections.length - 1)
                              const SizedBox(height: 12),
                          ],
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              _StickyFooterActions(
                saving: saving,
                submitLabel: submitLabel,
                onCancel: () => _handleBackOrCancel(context),
                onSubmit: onSubmit,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StickyFooterActions extends StatelessWidget {
  const _StickyFooterActions({
    required this.saving,
    required this.submitLabel,
    required this.onCancel,
    required this.onSubmit,
  });

  final bool saving;
  final String submitLabel;
  final VoidCallback onCancel;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.card,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
      child: ShipperPageContainer(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final scaledFont = MediaQuery.textScalerOf(context).scale(14);
            final stackVertically =
                constraints.maxWidth < 330 ||
                (constraints.maxWidth < 390 && scaledFont >= 20);

            final submitButton = FilledButton.icon(
              onPressed: saving ? null : onSubmit,
              icon: saving
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.check_rounded, size: 18),
              label: Text(
                saving ? 'Đang lưu…' : submitLabel,
                textAlign: TextAlign.center,
                maxLines: 2,
              ),
            );

            final cancelButton = OutlinedButton(
              onPressed: saving ? null : onCancel,
              child: const Text(
                'Hủy',
                textAlign: TextAlign.center,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            );

            if (stackVertically) {
              return Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  submitButton,
                  const SizedBox(height: 8),
                  cancelButton,
                ],
              );
            }

            return Row(
              children: [
                Expanded(flex: 1, child: cancelButton),
                const SizedBox(width: 12),
                Expanded(flex: 2, child: submitButton),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _AccountIdentityBanner extends StatelessWidget {
  const _AccountIdentityBanner({
    required this.avatarUrl,
    required this.avatarInitial,
    required this.displayName,
    required this.username,
    required this.email,
    required this.uploadingAvatar,
    this.onTapAvatar,
  });

  final String? avatarUrl;
  final String avatarInitial;
  final String displayName;
  final String username;
  final String? email;
  final bool uploadingAvatar;
  final VoidCallback? onTapAvatar;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
        border: Border.all(color: AppColors.border),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 300;
          final avatarWidget = CircleAvatar(
            radius: 26,
            backgroundColor: AppColors.primarySurface,
            child: uploadingAvatar
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : avatarUrl != null
                ? ClipOval(
                    child: CachedNetworkImage(
                      imageUrl: avatarUrl!,
                      width: 52,
                      height: 52,
                      fit: BoxFit.cover,
                      placeholder: (_, _) => Text(
                        avatarInitial,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          color: AppColors.primaryDark,
                        ),
                      ),
                      errorWidget: (_, _, _) => Text(
                        avatarInitial,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          color: AppColors.primaryDark,
                        ),
                      ),
                    ),
                  )
                : Text(
                    avatarInitial,
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: AppColors.primaryDark,
                    ),
                  ),
          );

          final metaColumn = Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                displayName,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textMain,
                ),
              ),
              if (username.isNotEmpty || (email != null && email!.isNotEmpty))
                Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Text(
                    [
                      if (username.isNotEmpty) '@$username',
                      if (email != null && email!.isNotEmpty) email!,
                    ].join(' · '),
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.textMuted,
                    ),
                  ),
                ),
              const SizedBox(height: 4),
              const Text(
                'Tên đăng nhập và email do quản trị viên quản lý.',
                style: TextStyle(fontSize: 11.5, color: AppColors.textMuted),
              ),
            ],
          );

          if (compact) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 10,
                  runSpacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    avatarWidget,
                    if (onTapAvatar != null)
                      OutlinedButton.icon(
                        onPressed: uploadingAvatar ? null : onTapAvatar,
                        icon: const Icon(Icons.camera_alt_outlined, size: 16),
                        label: const Text('Đổi ảnh'),
                      ),
                  ],
                ),
                const SizedBox(height: 10),
                metaColumn,
              ],
            );
          }

          return Row(
            children: [
              avatarWidget,
              const SizedBox(width: 12),
              Expanded(child: metaColumn),
              if (onTapAvatar != null) ...[
                const SizedBox(width: 8),
                IconButton(
                  tooltip: 'Đổi ảnh đại diện',
                  onPressed: uploadingAvatar ? null : onTapAvatar,
                  icon: const Icon(
                    Icons.camera_alt_outlined,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _FormSectionCard extends StatelessWidget {
  const _FormSectionCard({
    required this.icon,
    required this.title,
    required this.children,
    this.subtitle,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 19, color: AppColors.primary),
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
            ],
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 4),
            Text(
              subtitle!,
              style: const TextStyle(fontSize: 12.5, color: AppColors.textMuted),
            ),
          ],
          const SizedBox(height: 14),
          ...children,
        ],
      ),
    );
  }
}

class _ResponsiveFieldPair extends StatelessWidget {
  const _ResponsiveFieldPair({
    required this.first,
    required this.second,
  });

  final Widget first;
  final Widget second;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= 520) {
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: first),
              const SizedBox(width: 12),
              Expanded(child: second),
            ],
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [first, const SizedBox(height: 12), second],
        );
      },
    );
  }
}

class _ResponsiveDatePickerField extends StatelessWidget {
  const _ResponsiveDatePickerField({
    required this.label,
    required this.hasValue,
    required this.onPick,
    this.onClear,
  });

  final String label;
  final bool hasValue;
  final VoidCallback onPick;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.background,
      borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
      child: InkWell(
        onTap: onPick,
        borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
        child: Container(
          constraints: const BoxConstraints(minHeight: 48),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: [
              const Icon(
                Icons.cake_outlined,
                size: 18,
                color: AppColors.primary,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  label,
                  style: TextStyle(
                    fontSize: 14,
                    color: hasValue ? AppColors.textMain : AppColors.textMuted,
                    fontWeight: hasValue ? FontWeight.w500 : FontWeight.normal,
                  ),
                ),
              ),
              if (onClear != null)
                IconButton(
                  tooltip: 'Xóa ngày sinh',
                  visualDensity: VisualDensity.compact,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                  padding: EdgeInsets.zero,
                  onPressed: onClear,
                  icon: const Icon(Icons.clear_rounded, size: 18),
                )
              else
                const Icon(
                  Icons.calendar_today_outlined,
                  size: 16,
                  color: AppColors.textMuted,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

