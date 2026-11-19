import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

class CheckoutAddressFields extends StatefulWidget {
  const CheckoutAddressFields({
    super.key,
    required this.city,
    required this.ward,
    required this.district,
  });

  final TextEditingController city;
  final TextEditingController ward;
  final TextEditingController district;

  @override
  State<CheckoutAddressFields> createState() => _CheckoutAddressFieldsState();
}

class _CheckoutAddressFieldsState extends State<CheckoutAddressFields> {
  static const _api = 'https://www.tinhthanhpho.com/api/v1/new-provinces';
  static final _directory = rootBundle
      .loadString('assets/vietnam-addresses.json')
      .then((value) => jsonDecode(value) as Map<String, dynamic>);

  final _client = http.Client();
  final _liveWards = <String, List<dynamic>>{};
  final _requestedWards = <String>{};
  List<dynamic> _provinces = [];
  Map<String, dynamic> _localWards = {};
  bool _manual = false;

  @override
  void initState() {
    super.initState();
    widget.city.addListener(_loadWards);
    _loadDirectory();
  }

  Future<List<dynamic>?> _lookup(String url) async {
    try {
      final response = await _client
          .get(Uri.parse(url))
          .timeout(const Duration(seconds: 5));
      if (response.statusCode != 200) return null;
      final payload = jsonDecode(utf8.decode(response.bodyBytes));
      final rows = payload['data'];
      return rows is List &&
              rows.isNotEmpty &&
              rows.length == payload['metadata']?['total']
          ? rows
          : null;
    } catch (_) {
      return null;
    }
  }

  Future<void> _loadDirectory() async {
    try {
      final directory = await _directory;
      if (!mounted) return;
      setState(() {
        _provinces = directory['provinces'] as List<dynamic>;
        _localWards = directory['wards'] as Map<String, dynamic>;
      });
      _loadWards();
    } catch (_) {
      // Live lookup and manual entry remain available.
    }
    if (!mounted) return;
    final provinces = await _lookup('$_api?limit=100');
    if (!mounted || provinces == null) return;
    setState(() => _provinces = provinces);
    _loadWards();
  }

  String? get _provinceCode {
    for (final province in _provinces) {
      if (province['name'] == widget.city.text) {
        return province['code'].toString();
      }
    }
    return null;
  }

  Future<void> _loadWards() async {
    final code = _provinceCode;
    if (_manual || code == null || !_requestedWards.add(code)) return;
    final wards = await _lookup(
      '$_api/${Uri.encodeComponent(code)}/wards?limit=250',
    );
    if (mounted && wards != null) setState(() => _liveWards[code] = wards);
  }

  @override
  void dispose() {
    widget.city.removeListener(_loadWards);
    _client.close();
    super.dispose();
  }

  Widget _entry({
    required String label,
    required TextEditingController controller,
    required Iterable<String> options,
    required ValueChanged<String> onChange,
    required String hint,
  }) {
    final decoration = InputDecoration(labelText: label, isDense: true);
    if (_manual) {
      return TextField(controller: controller, decoration: decoration);
    }
    final values = {
      ...options,
      if (controller.text.isNotEmpty) controller.text,
    };
    return InputDecorator(
      decoration: decoration,
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          isDense: true,
          isExpanded: true,
          value: controller.text.isEmpty ? null : controller.text,
          hint: Text(hint, overflow: TextOverflow.ellipsis),
          items: [
            for (final value in values)
              DropdownMenuItem(
                value: value,
                child: Text(value, overflow: TextOverflow.ellipsis),
              ),
          ],
          onChanged: values.isEmpty
              ? null
              : (value) {
                  if (value != null && value != controller.text) {
                    onChange(value);
                  }
                },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([widget.city, widget.ward, widget.district]),
      builder: (context, _) {
        final code = _provinceCode;
        final wards =
            _liveWards[code] ?? _localWards[code] as List<dynamic>? ?? [];
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _entry(
              label: 'Tỉnh/Thành phố *',
              controller: widget.city,
              options: _provinces.map((row) => row['name'].toString()),
              hint: 'Chọn tỉnh / thành',
              onChange: (value) {
                widget.ward.clear();
                widget.district.clear();
                widget.city.text = value;
              },
            ),
            const SizedBox(height: 10),
            _entry(
              label: 'Phường/Xã/Đặc khu *',
              controller: widget.ward,
              options: wards.map((row) => "${row['type']} ${row['name']}"),
              hint: 'Chọn phường / xã / đặc khu',
              onChange: (value) {
                widget.ward.text = value;
                widget.district.clear();
              },
            ),
            if (_manual || widget.district.text.isNotEmpty) ...[
              const SizedBox(height: 10),
              TextField(
                controller: widget.district,
                decoration: const InputDecoration(
                  labelText: 'Quận/Huyện (địa chỉ cũ)',
                  isDense: true,
                ),
              ),
            ],
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(
                onPressed: () {
                  setState(() => _manual = !_manual);
                  _loadWards();
                },
                child: Text(
                  _manual ? 'Chọn từ danh mục' : 'Nhập địa chỉ thủ công',
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}
