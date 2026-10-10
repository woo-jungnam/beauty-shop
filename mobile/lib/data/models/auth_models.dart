class User {
  final int id;
  final String username;
  final String fullName;
  final String? email;
  final String? phone;
  final String? role;
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
    this.avatarUrl,
    this.membershipTier = 'SILVER',
    this.rewardPoints = 0,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: (json['id'] as num?)?.toInt() ?? 0,
      username: json['username']?.toString() ?? '',
      fullName: json['fullName']?.toString() ?? json['name']?.toString() ?? 'Khách hàng',
      email: json['email']?.toString(),
      phone: json['phone']?.toString() ?? json['phoneNumber']?.toString(),
      role: json['role']?.toString() ?? (json['roles'] is List && (json['roles'] as List).isNotEmpty ? json['roles'][0].toString() : 'CUSTOMER'),
      avatarUrl: json['avatarUrl']?.toString(),
      membershipTier: json['membershipTier']?.toString() ?? 'SILVER',
      rewardPoints: ((json['loyaltyPoints'] ?? json['rewardPoints']) as num?)?.toInt() ?? 0,
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
      'avatarUrl': avatarUrl,
      'membershipTier': membershipTier,
      'rewardPoints': rewardPoints,
    };
  }
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
    final token = json['accessToken']?.toString() ?? json['token']?.toString() ?? '';
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
