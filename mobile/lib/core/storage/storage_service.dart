import 'dart:math';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class StorageService {
  static final ValueNotifier<int> authCleared = ValueNotifier<int>(0);
  static const String _keyAccessToken = 'auth_access_token';
  static const String _keyRefreshToken = 'auth_refresh_token';
  static const String _keyGuestSessionId = 'guest_session_id';
  static const String _keyUserData = 'auth_user_data';
  static const String _keyBaseUrl = 'custom_base_url';
  static const String _keyDeliveryAddress = 'delivery_address';
  static const String _keyDeliveryDetails = 'delivery_details';
  static const String _keyThemeMode = 'theme_mode';

  static SharedPreferences? _prefs;
  static const FlutterSecureStorage _secureStorage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );
  static String? _accessToken;
  static String? _refreshToken;

  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
    _accessToken = await _secureStorage.read(key: _keyAccessToken);
    _refreshToken = await _secureStorage.read(key: _keyRefreshToken);
    _accessToken ??= _prefs?.getString(_keyAccessToken);
    _refreshToken ??= _prefs?.getString(_keyRefreshToken);
  }

  static Future<void> setAccessToken(String? token) async {
    if (token == null) {
      await _secureStorage.delete(key: _keyAccessToken);
    } else {
      await _secureStorage.write(key: _keyAccessToken, value: token);
    }
    _accessToken = token;
  }

  static String? getAccessToken() => _accessToken;

  static Future<void> setRefreshToken(String? token) async {
    if (token == null) {
      await _secureStorage.delete(key: _keyRefreshToken);
    } else {
      await _secureStorage.write(key: _keyRefreshToken, value: token);
    }
    _refreshToken = token;
  }

  static String? getRefreshToken() => _refreshToken;

  static Future<void> saveToken(String token) async => setAccessToken(token);
  static Future<String?> getToken() async => _accessToken;

  static bool get isLoggedIn =>
      _accessToken != null && _accessToken!.isNotEmpty;

  static Future<void> clearAuth() async {
    _accessToken = null;
    _refreshToken = null;
    authCleared.value++;
    await _secureStorage.delete(key: _keyAccessToken);
    await _secureStorage.delete(key: _keyRefreshToken);
    await _prefs?.remove(_keyUserData);
  }

  static String getGuestSessionId() {
    String? id = _prefs?.getString(_keyGuestSessionId);
    if (id == null || id.isEmpty) {
      final random = Random.secure();
      final values = List<int>.generate(16, (i) => random.nextInt(256));
      id =
          'guest-${DateTime.now().millisecondsSinceEpoch}-${values.map((b) => b.toRadixString(16).padLeft(2, '0')).join()}';
      _prefs?.setString(_keyGuestSessionId, id);
    }
    return id;
  }

  static Future<void> saveUserData(String userJson) async {
    await _prefs?.setString(_keyUserData, userJson);
  }

  static String? getUserData() => _prefs?.getString(_keyUserData);

  static Future<void> setBaseUrl(String url) async {
    await _prefs?.setString(_keyBaseUrl, url);
  }

  static String? getBaseUrl() => _prefs?.getString(_keyBaseUrl);

  static String _deliveryKey(String base) {
    try {
      final raw = _prefs?.getString(_keyUserData);
      final user = raw == null ? null : jsonDecode(raw);
      final owner = isLoggedIn && user is Map
          ? 'user-${user['id'] ?? user['username']}'
          : getGuestSessionId();
      return '$base:$owner';
    } catch (_) {
      return '$base:${getGuestSessionId()}';
    }
  }

  static Future<void> setDeliveryAddress(String address) async {
    await _prefs?.setString(_deliveryKey(_keyDeliveryAddress), address);
  }

  static String getDeliveryAddress() {
    return _prefs?.getString(_deliveryKey(_keyDeliveryAddress)) ?? '';
  }

  static Map<String, String> getDeliveryDetails() {
    final raw = _prefs?.getString(_deliveryKey(_keyDeliveryDetails));
    if (raw == null) return {'address': getDeliveryAddress()};
    try {
      return (jsonDecode(raw) as Map<String, dynamic>).map(
        (key, value) => MapEntry(key, value.toString()),
      );
    } catch (_) {
      return {'address': getDeliveryAddress()};
    }
  }

  static Future<void> setDeliveryDetails(Map<String, String> details) async {
    await _prefs?.setString(
      _deliveryKey(_keyDeliveryDetails),
      jsonEncode(details),
    );
    await setDeliveryAddress(details['address'] ?? '');
  }

  static String getFullDeliveryAddress() =>
      ['address', 'ward', 'district', 'city']
          .map((key) => getDeliveryDetails()[key] ?? '')
          .where((part) => part.isNotEmpty)
          .join(', ');

  static List<int> getGuestOrderIds() {
    try {
      final raw = _prefs?.getString('guest-orders:${getGuestSessionId()}');
      return raw == null
          ? []
          : (jsonDecode(raw) as List)
                .whereType<num>()
                .map((id) => id.toInt())
                .toList();
    } catch (_) {
      return [];
    }
  }

  static Future<void> rememberGuestOrder(int id) async {
    final ids = [
      id,
      ...getGuestOrderIds().where((value) => value != id),
    ].take(100).toList();
    await _prefs?.setString(
      'guest-orders:${getGuestSessionId()}',
      jsonEncode(ids),
    );
  }

  static Future<void> setThemeMode(String mode) async {
    await _prefs?.setString(_keyThemeMode, mode);
  }

  static String getThemeMode() {
    return _prefs?.getString(_keyThemeMode) ?? 'system';
  }
}
