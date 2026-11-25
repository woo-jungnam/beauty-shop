import 'package:flutter/foundation.dart';

import 'storage_service.dart';

enum ShipperMapPreference {
  googleMaps('google_maps', 'Mở Google Maps'),
  mapsAndCopy('maps_and_copy', 'Mở bản đồ & sao chép địa chỉ'),
  copyOnly('copy_only', 'Chỉ sao chép địa chỉ');

  const ShipperMapPreference(this.code, this.label);

  final String code;
  final String label;

  static ShipperMapPreference fromCode(String? code) {
    return ShipperMapPreference.values.firstWhere(
      (item) => item.code == code,
      orElse: () => ShipperMapPreference.googleMaps,
    );
  }
}

class ShipperSettingsStore extends ChangeNotifier {
  ShipperSettingsStore._();

  static final ShipperSettingsStore instance = ShipperSettingsStore._();

  static const _keyAutoRefresh = 'shipper_pref_auto_refresh';
  static const _keyRefreshInterval = 'shipper_pref_refresh_interval';
  static const _keyTextScale = 'shipper_pref_text_scale';
  static const _keyMapPreference = 'shipper_pref_map_mode';
  static const _keyConfirmBeforeStart = 'shipper_pref_confirm_before_start';

  static const List<int> supportedIntervals = [15, 30, 60];
  static const List<double> supportedTextScales = [0.95, 1.0, 1.15];

  bool _autoRefreshEnabled = true;
  int _refreshIntervalSeconds = 30;
  double _textScaleFactor = 1.0;
  ShipperMapPreference _mapPreference = ShipperMapPreference.googleMaps;
  bool _confirmBeforeStart = false;

  bool get autoRefreshEnabled => _autoRefreshEnabled;
  int get refreshIntervalSeconds => _refreshIntervalSeconds;
  double get textScaleFactor => _textScaleFactor;
  ShipperMapPreference get mapPreference => _mapPreference;
  bool get confirmBeforeStart => _confirmBeforeStart;

  void reload() {
    _autoRefreshEnabled = StorageService.getBool(_keyAutoRefresh) ?? true;
    final storedInterval = StorageService.getInt(_keyRefreshInterval) ?? 30;
    _refreshIntervalSeconds = supportedIntervals.contains(storedInterval)
        ? storedInterval
        : 30;
    final storedScale = StorageService.getDouble(_keyTextScale) ?? 1.0;
    _textScaleFactor = supportedTextScales.contains(storedScale)
        ? storedScale
        : 1.0;
    _mapPreference = ShipperMapPreference.fromCode(
      StorageService.getString(_keyMapPreference),
    );
    _confirmBeforeStart = StorageService.getBool(_keyConfirmBeforeStart) ?? false;
    notifyListeners();
  }

  Future<void> setAutoRefreshEnabled(bool enabled) async {
    if (_autoRefreshEnabled == enabled) return;
    _autoRefreshEnabled = enabled;
    notifyListeners();
    await StorageService.setBool(_keyAutoRefresh, enabled);
  }

  Future<void> setRefreshIntervalSeconds(int seconds) async {
    if (!supportedIntervals.contains(seconds) ||
        _refreshIntervalSeconds == seconds) {
      return;
    }
    _refreshIntervalSeconds = seconds;
    notifyListeners();
    await StorageService.setInt(_keyRefreshInterval, seconds);
  }

  Future<void> setTextScaleFactor(double factor) async {
    if (!supportedTextScales.contains(factor) || _textScaleFactor == factor) {
      return;
    }
    _textScaleFactor = factor;
    notifyListeners();
    await StorageService.setDouble(_keyTextScale, factor);
  }

  Future<void> setMapPreference(ShipperMapPreference preference) async {
    if (_mapPreference == preference) return;
    _mapPreference = preference;
    notifyListeners();
    await StorageService.setString(_keyMapPreference, preference.code);
  }

  Future<void> setConfirmBeforeStart(bool enabled) async {
    if (_confirmBeforeStart == enabled) return;
    _confirmBeforeStart = enabled;
    notifyListeners();
    await StorageService.setBool(_keyConfirmBeforeStart, enabled);
  }

  Future<void> resetToDefaults() async {
    _autoRefreshEnabled = true;
    _refreshIntervalSeconds = 30;
    _textScaleFactor = 1.0;
    _mapPreference = ShipperMapPreference.googleMaps;
    _confirmBeforeStart = false;
    notifyListeners();
    await Future.wait([
      StorageService.removeKey(_keyAutoRefresh),
      StorageService.removeKey(_keyRefreshInterval),
      StorageService.removeKey(_keyTextScale),
      StorageService.removeKey(_keyMapPreference),
      StorageService.removeKey(_keyConfirmBeforeStart),
    ]);
  }
}

