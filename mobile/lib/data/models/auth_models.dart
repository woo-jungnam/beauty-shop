class User {
  final int id;
  final String username;
  final String fullName;
  final String? email;
  final String? phone;
  final String? role;
  final List<String> roles;
  final List<String> permissions;
  final String? avatarUrl;
  final String? membershipTier;
  final int rewardPoints;

  User({
    required this.id,
    required this.username,
    required this.fullName,
    this.email,
    this.phone,
    this.role,
    this.roles = const [],
    this.permissions = const [],
    this.avatarUrl,
    this.membershipTier,
    this.rewardPoints = 0,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    String roleName(dynamic value) => value is Map
        ? (value['name'] ?? value['authority'] ?? value['role'] ?? '')
              .toString()
        : value?.toString() ?? '';
    final allRoles = <String>{
      if (json['role'] != null) roleName(json['role']),
      if (json['roles'] is List) ...(json['roles'] as List).map(roleName),
      if (json['authorities'] is List)
        ...(json['authorities'] as List).map(roleName),
    }..removeWhere((value) => value.isEmpty);
    return User(
      id: (json['id'] as num?)?.toInt() ?? 0,
      username: json['username']?.toString() ?? '',
      fullName:
          json['fullName']?.toString() ??
          json['name']?.toString() ??
          'Khách hàng',
      email: json['email']?.toString(),
      phone: json['phone']?.toString() ?? json['phoneNumber']?.toString(),
      role: allRoles.isEmpty ? 'ROLE_USER' : allRoles.first,
      roles: allRoles.toList(growable: false),
      permissions: json['permissions'] is List
          ? (json['permissions'] as List)
                .whereType<String>()
                .map((value) => value.trim().toUpperCase())
                .where((value) => value.isNotEmpty)
                .toSet()
                .toList(growable: false)
          : const [],
      avatarUrl: json['avatarUrl']?.toString(),
      membershipTier: json['membershipTier']?.toString(),
      rewardPoints:
          ((json['loyaltyPoints'] ?? json['rewardPoints']) as num?)?.toInt() ??
          0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'fullName': fullName,
      'email': email,
      'phone': phone,
      'role': role,
      'roles': roles,
      'permissions': permissions,
      'avatarUrl': avatarUrl,
      'membershipTier': membershipTier,
      'rewardPoints': rewardPoints,
    };
  }

  Set<String> get normalizedRoles => {...roles, if (role != null) role!}
      .map((value) => value.toUpperCase().replaceFirst(RegExp(r'^ROLE_'), ''))
      .map((value) => value == 'CUSTOMER' ? 'USER' : value)
      .toSet();

  bool get isAdmin => normalizedRoles.contains('ADMIN');
  bool get isStaff => normalizedRoles.contains('STAFF');
  bool hasPermission(String permission) =>
      isAdmin || (isStaff && permissions.contains(permission));
  bool get canOperateSpa =>
      hasPermission('SPA_RECEPTION') || hasPermission('SPA_EXECUTION');
}

class AuthResponse {
  final String accessToken;
  final String? refreshToken;
  final String? tokenType;
  final User? user;

  AuthResponse({
    required this.accessToken,
    this.refreshToken,
    this.tokenType = 'Bearer',
    this.user,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    final token =
        json['accessToken']?.toString() ?? json['token']?.toString() ?? '';
    final refresh = json['refreshToken']?.toString();
    User? parsedUser;
    if (json['user'] != null && json['user'] is Map<String, dynamic>) {
      parsedUser = User.fromJson(json['user']);
    } else if (json['id'] != null || json['username'] != null) {
      parsedUser = User.fromJson(json);
    }
    return AuthResponse(
      accessToken: token,
      refreshToken: refresh,
      tokenType: json['tokenType']?.toString() ?? 'Bearer',
      user: parsedUser,
    );
  }
}
