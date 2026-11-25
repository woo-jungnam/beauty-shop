import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'shipper_settings_store.dart';

typedef SessionTokens = ({String access, String refresh, String server});

class StorageService {
  static const _keySession = 'shipper_session';
  static const _keyUsername = 'shipper_username';
  static const _keyFullName = 'shipper_fullname';
  static const _keyBaseUrl = 'shipper_base_url';
  static const _secure = FlutterSecureStorage();
  static final sessionEnded = ValueNotifier<int>(0);

  static SharedPreferences? _prefs;
  static SessionTokens? tokens;
  static int sessionVersion = 0;
  static Future<void> _lastWrite = Future.value();

  static Future<void> init() async {
    _prefs ??= await SharedPreferences.getInstance();
    final raw = await _secure.read(key: _keySession);
    if (raw != null) {
      final data = jsonDecode(raw) as Map<String, dynamic>;
      tokens = (
        access: data['access'] as String,
        refresh: data['refresh'] as String,
        server: data['server'] as String,
      );
    }
    await _prefs?.remove('shipper_access_token');
    ShipperSettingsStore.instance.reload();
  }

  static Future<void> setTokens(
    SessionTokens value, {
    bool newSession = false,
  }) {
    if (newSession) sessionVersion++;
    tokens = value;
    return _queueWrite(
      () => _secure.write(
        key: _keySession,
        value: jsonEncode({
          'access': value.access,
          'refresh': value.refresh,
          'server': value.server,
        }),
      ),
    );
  }

  static Future<void> _queueWrite(Future<void> Function() write) {
    final next = _lastWrite.then((_) => write(), onError: (_) => write());
    _lastWrite = next;
    return next;
  }

  static String? getUsername() => _prefs?.getString(_keyUsername);
  static String? getFullName() => _prefs?.getString(_keyFullName);

  static Future<void> setUserInfo(String username, String fullName) async {
    await _prefs?.setString(_keyUsername, username);
    await _prefs?.setString(_keyFullName, fullName);
  }

  static String? getBaseUrl() => _prefs?.getString(_keyBaseUrl);

  static Future<void> setBaseUrl(String url) async {
    await _prefs?.setString(_keyBaseUrl, url);
  }

  static bool? getBool(String key) => _prefs?.getBool(key);
  static Future<void> setBool(String key, bool value) async =>
      await _prefs?.setBool(key, value);

  static int? getInt(String key) => _prefs?.getInt(key);
  static Future<void> setInt(String key, int value) async =>
      await _prefs?.setInt(key, value);

  static double? getDouble(String key) => _prefs?.getDouble(key);
  static Future<void> setDouble(String key, double value) async =>
      await _prefs?.setDouble(key, value);

  static String? getString(String key) => _prefs?.getString(key);
  static Future<void> setString(String key, String value) async =>
      await _prefs?.setString(key, value);

  static Future<void> removeKey(String key) async => await _prefs?.remove(key);

  static Future<void> clearAuth() async {
    sessionVersion++;
    tokens = null;
    sessionEnded.value++;
    await _queueWrite(() => _secure.delete(key: _keySession));
  }
}
