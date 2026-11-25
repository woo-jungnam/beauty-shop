import '../../core/constants/api_constants.dart';

class ShipperUserProfile {
  const ShipperUserProfile({
    required this.id,
    required this.username,
    required this.fullName,
    this.email,
    this.phone,
    this.avatarUrl,
    this.rawAvatarUrl,
    this.bio,
    this.gender,
    this.dateOfBirth,
    this.status,
    this.roles = const [],
  });

  final int id;
  final String username;
  final String fullName;
  final String? email;
  final String? phone;
  final String? avatarUrl;
  final String? rawAvatarUrl;
  final String? bio;
  final String? gender;
  final String? dateOfBirth;
  final String? status;
  final List<String> roles;

  factory ShipperUserProfile.fromJson(Map<String, dynamic> json) {
    final rawAvatar = json['avatarUrl']?.toString().trim();
    return ShipperUserProfile(
      id: (json['id'] as num?)?.toInt() ?? 0,
      username: json['username']?.toString() ?? '',
      fullName: json['fullName']?.toString() ?? '',
      email: _nonEmpty(json['email']),
      phone: _nonEmpty(json['phone']),
      rawAvatarUrl: (rawAvatar == null || rawAvatar.isEmpty) ? null : rawAvatar,
      avatarUrl: ApiConstants.resolveMediaUrl(rawAvatar),
      bio: _nonEmpty(json['bio']),
      gender: _nonEmpty(json['gender']),
      dateOfBirth: _nonEmpty(json['dateOfBirth']),
      status: _nonEmpty(json['status']),
      roles: (json['roles'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
    );
  }

  String get genderLabel => switch (gender) {
    'MALE' => 'Nam',
    'FEMALE' => 'Nữ',
    'OTHER' => 'Khác',
    _ => 'Chưa cập nhật',
  };

  String get statusLabel => switch (status) {
    'ACTIVE' => 'Đang hoạt động',
    'SUSPENDED' => 'Tạm đình chỉ',
    'BLOCKED' => 'Đã khóa',
    _ => status ?? 'Hoạt động',
  };
}

class ShipperWorkProfile {
  const ShipperWorkProfile({
    this.vehicleType,
    this.vehiclePlate,
    this.serviceArea,
    this.emergencyContactName,
    this.emergencyContactPhone,
    this.workShift,
    this.updatedAt,
  });

  final String? vehicleType;
  final String? vehiclePlate;
  final String? serviceArea;
  final String? emergencyContactName;
  final String? emergencyContactPhone;
  final String? workShift;
  final DateTime? updatedAt;

  factory ShipperWorkProfile.fromJson(Map<String, dynamic> json) {
    return ShipperWorkProfile(
      vehicleType: _nonEmpty(json['vehicleType']),
      vehiclePlate: _nonEmpty(json['vehiclePlate']),
      serviceArea: _nonEmpty(json['serviceArea']),
      emergencyContactName: _nonEmpty(json['emergencyContactName']),
      emergencyContactPhone: _nonEmpty(json['emergencyContactPhone']),
      workShift: _nonEmpty(json['workShift']),
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? ''),
    );
  }

  bool get hasAnyInfo =>
      vehicleType != null ||
      vehiclePlate != null ||
      serviceArea != null ||
      emergencyContactName != null ||
      emergencyContactPhone != null ||
      workShift != null;
}

String? _nonEmpty(Object? value) {
  final text = value?.toString().trim();
  return (text == null || text.isEmpty) ? null : text;
}

