import 'package:flutter/material.dart';
import '../../core/storage/storage_service.dart';

class DeliveryAddressDialog extends StatefulWidget {
  const DeliveryAddressDialog({super.key});
  @override
  State<DeliveryAddressDialog> createState() => _DeliveryAddressDialogState();
}

class _DeliveryAddressDialogState extends State<DeliveryAddressDialog> {
  final _form = GlobalKey<FormState>();
  final Map<String, TextEditingController> _fields = {};
  bool _saving = false;
  static const _labels = {
    'address': 'Số nhà, tên đường',
    'ward': 'Phường/Xã',
    'district': 'Quận/Huyện',
    'city': 'Tỉnh/Thành phố',
  };
  @override
  void initState() {
    super.initState();
    final saved = StorageService.getDeliveryDetails();
    for (final key in _labels.keys) {
      _fields[key] = TextEditingController(text: saved[key] ?? '');
    }
  }

  @override
  void dispose() {
    for (final field in _fields.values) {
      field.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Địa chỉ nhận hàng'),
    content: SingleChildScrollView(
      child: Form(
        key: _form,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: _labels.entries
              .map(
                (entry) => Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: TextFormField(
                    controller: _fields[entry.key],
                    decoration: InputDecoration(labelText: entry.value),
                    validator: (value) => value == null || value.trim().isEmpty
                        ? 'Vui lòng nhập ${entry.value.toLowerCase()}'
                        : null,
                  ),
                ),
              )
              .toList(),
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: _saving ? null : () => Navigator.pop(context, false),
        child: const Text('Đóng'),
      ),
      FilledButton(
        onPressed: _saving
            ? null
            : () async {
                if (!_form.currentState!.validate()) return;
                setState(() => _saving = true);
                await StorageService.setDeliveryDetails(
                  _fields.map((key, value) => MapEntry(key, value.text.trim())),
                );
                if (context.mounted) Navigator.pop(context, true);
              },
        child: const Text('Lưu'),
      ),
    ],
  );
}
