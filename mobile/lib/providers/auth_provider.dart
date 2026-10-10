import 'dart:convert';
import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/storage/storage_service.dart';
import '../data/models/auth_models.dart';

class AuthProvider extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();

  User? _currentUser;
  bool _isLoading = false;
  String? _errorMessage;

  User? get currentUser => _currentUser;
  bool get isAuthenticated => StorageService.isLoggedIn && _currentUser != null;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;

  AuthProvider() {
    _loadSavedUser();
  }

  Future<void> _loadSavedUser() async {
    final token = StorageService.getAccessToken();
    if (token != null && token.isNotEmpty) {
      final userJson = StorageService.getUserData();
      if (userJson != null) {
        try {
          _currentUser = User.fromJson(jsonDecode(userJson));
          notifyListeners();
        } catch (_) {}
      }
      fetchCurrentUser();
    }
  }

  Future<bool> login(String usernameOrEmail, String password) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final response = await _apiClient.post<AuthResponse>(
        ApiConstants.login,
        data: {'usernameOrEmail': usernameOrEmail.trim(), 'password': password},
        fromJsonT: (json) => AuthResponse.fromJson(json as Map<String, dynamic>),
      );

      if (response.isSuccess && response.data != null) {
        final auth = response.data!;
        await StorageService.setAccessToken(auth.accessToken);
        if (auth.refreshToken != null) {
          await StorageService.setRefreshToken(auth.refreshToken);
        }

        if (auth.user != null) {
          _currentUser = auth.user;
          await StorageService.saveUserData(jsonEncode(_currentUser!.toJson()));
        } else {
          await fetchCurrentUser();
        }

        // Merge guest cart if available
        final guestSessionId = StorageService.getGuestSessionId();
        if (guestSessionId.isNotEmpty) {
          try {
            await _apiClient.post(
              ApiConstants.mergeCart,
              queryParameters: {'sessionId': guestSessionId},
            );
          } catch (_) {}
        }

        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _errorMessage = response.message ?? 'Đăng nhập không thành công';
      }
    } catch (e) {
      _errorMessage = 'Tên đăng nhập hoặc mật khẩu không chính xác';
    }

    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<bool> register({
    required String fullName,
    required String email,
    required String phone,
    required String username,
    required String password,
  }) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final response = await _apiClient.post<AuthResponse>(
        ApiConstants.register,
        data: {
          'fullName': fullName.trim(),
          'email': email.trim(),
          'phone': phone.trim(),
          'username': username.trim(),
          'password': password,
        },
        fromJsonT: (json) => AuthResponse.fromJson(json as Map<String, dynamic>),
      );

      if (response.isSuccess && response.data != null) {
        final auth = response.data!;
        await StorageService.setAccessToken(auth.accessToken);
        if (auth.refreshToken != null) {
          await StorageService.setRefreshToken(auth.refreshToken);
        }

        if (auth.user != null) {
          _currentUser = auth.user;
          await StorageService.saveUserData(jsonEncode(_currentUser!.toJson()));
        } else {
          await fetchCurrentUser();
        }

        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _errorMessage = response.message ?? 'Đăng ký không thành công';
      }
    } catch (e) {
      _errorMessage = 'Không thể đăng ký. Tên đăng nhập hoặc email đã tồn tại';
    }

    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<void> fetchCurrentUser() async {
    try {
      final response = await _apiClient.get<User>(
        ApiConstants.currentUserProfile,
        fromJsonT: (json) => User.fromJson(json as Map<String, dynamic>),
      );
      if (response.isSuccess && response.data != null) {
        _currentUser = response.data;
        await StorageService.saveUserData(jsonEncode(_currentUser!.toJson()));
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<void> logout() async {
    try {
      final refreshToken = StorageService.getRefreshToken();
      if (refreshToken != null && refreshToken.isNotEmpty) {
        await _apiClient.post(
          ApiConstants.logout,
          data: {'refreshToken': refreshToken},
        );
      }
    } catch (_) {}
    await StorageService.clearAuth();
    _currentUser = null;
    notifyListeners();
  }
}
