import 'package:flutter/material.dart';
import 'package:dio/dio.dart';

import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/storage/storage_service.dart';

class AuthProvider extends ChangeNotifier {
  bool _isLoading = false;
  String? _errorMessage;

  AuthProvider() {
    StorageService.sessionEnded.addListener(_sessionEnded);
  }

  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  String? get currentUsername => StorageService.getUsername();
  String? get currentFullName => StorageService.getFullName();
  bool get isAuthenticated =>
      StorageService.tokens?.server == ApiConstants.baseUrl;

  void _sessionEnded() {
    _errorMessage = 'Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.';
    notifyListeners();
  }

  Future<bool> login(String username, String password) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    final server = ApiConstants.baseUrl;
    try {
      final response = await ApiClient().post<Map<String, dynamic>>(
        ApiConstants.login,
        data: {'username': username.trim(), 'password': password},
        options: Options(extra: {'server': server}),
      );
      final data = response.data;
      final access = data?['accessToken'];
      final refresh = data?['refreshToken'];
      if (!response.isSuccess ||
          access is! String ||
          access.isEmpty ||
          refresh is! String ||
          refresh.isEmpty ||
          server != ApiConstants.baseUrl) {
        _errorMessage = response.message ?? 'Đăng nhập không thành công';
        return false;
      }
      final user = data?['user'] as Map<String, dynamic>?;
      await StorageService.setUserInfo(
        (user?['username'] ?? data?['username'])?.toString() ?? username,
        (user?['fullName'] ?? data?['fullName'])?.toString() ?? username,
      );
      await StorageService.setTokens((
        access: access,
        refresh: refresh,
        server: server,
      ), newSession: true);
      return true;
    } catch (e) {
      if (StorageService.tokens?.server == server) {
        await StorageService.clearAuth();
      }
      _errorMessage = e.toString();
      return false;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> logout() async {
    await ApiClient().finishPendingRefresh();
    final tokens = StorageService.tokens;
    await StorageService.clearAuth();
    if (tokens == null) return;
    try {
      await ApiClient().post(
        ApiConstants.logout,
        data: {'refreshToken': tokens.refresh},
        options: Options(extra: {'server': tokens.server}),
      );
    } catch (_) {
      // Local sign-out also works while offline.
    }
  }

  @override
  void dispose() {
    StorageService.sessionEnded.removeListener(_sessionEnded);
    super.dispose();
  }
}
