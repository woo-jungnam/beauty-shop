import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/utils/image_utils.dart';
import '../../../data/models/treatment_model.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/treatment_provider.dart';

class TreatmentThumbImage extends StatelessWidget {
  const TreatmentThumbImage({
    super.key,
    required this.url,
    this.width,
    this.height,
    this.borderRadius = 10,
    this.placeholderIcon = Icons.spa_outlined,
  });
  final String? url;
  final double? width;
  final double? height;
  final double borderRadius;
  final IconData placeholderIcon;

  @override
  Widget build(BuildContext context) {
    final resolved = ImageUtils.resolveUrl(url);
    if (resolved == null || resolved.isEmpty) {
      return Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(borderRadius),
        ),
        child: Icon(
          placeholderIcon,
          color: Theme.of(context).colorScheme.primary,
        ),
      );
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: Image.network(
        resolved,
        width: width,
        height: height,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => Container(
          width: width,
          height: height,
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          child: Icon(
            placeholderIcon,
            color: Theme.of(context).colorScheme.primary,
          ),
        ),
      ),
    );
  }
}

String treatmentAdvisoryGap(int minGapDays, int? maxGapDays) {
  if (minGapDays <= 0 && maxGapDays == null) return '';
  return maxGapDays != null
      ? 'Giãn cách tham khảo: $minGapDays–$maxGapDays ngày (không bắt buộc)'
      : 'Giãn cách tham khảo: $minGapDays+ ngày (không bắt buộc)';
}

class TreatmentAsync<T> extends StatelessWidget {
  const TreatmentAsync({
    super.key,
    required this.future,
    required this.retry,
    required this.builder,
  });
  final Future<T>? future;
  final VoidCallback retry;
  final Widget Function(T) builder;
  @override
  Widget build(BuildContext context) => FutureBuilder<T>(
    future: future,
    builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) {
        return const Center(child: CircularProgressIndicator());
      }
      if (snapshot.hasError) {
        return Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(snapshot.error.toString(), textAlign: TextAlign.center),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: retry,
                  icon: const Icon(Icons.refresh),
                  label: const Text('Thử lại'),
                ),
              ],
            ),
          ),
        );
      }
      return builder(snapshot.data as T);
    },
  );
}

class TreatmentStatusChip extends StatelessWidget {
  const TreatmentStatusChip(this.status, {super.key});
  final String status;
  @override
  Widget build(BuildContext context) => Chip(
    visualDensity: VisualDensity.compact,
    label: Text(treatmentStatus(status), style: const TextStyle(fontSize: 12)),
  );
}

class TreatmentEmpty extends StatelessWidget {
  const TreatmentEmpty(this.message, {super.key});
  final String message;
  @override
  Widget build(BuildContext context) => ListView(
    children: [
      Padding(
        padding: const EdgeInsets.all(32),
        child: Text(message, textAlign: TextAlign.center),
      ),
    ],
  );
}

String treatmentDate(String? value) {
  if (value == null || value.isEmpty) return 'Chưa xác định';
  final parsed = DateTime.tryParse(value);
  if (parsed == null) return value;
  final local = value.endsWith('Z') || RegExp(r'[+-]\d\d:\d\d$').hasMatch(value)
      ? parsed.toUtc().add(const Duration(hours: 7))
      : parsed;
  return '${local.day.toString().padLeft(2, '0')}/${local.month.toString().padLeft(2, '0')}/${local.year}'
      '${value.contains('T') ? ' ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}' : ''}';
}

Future<Map<String, String>?> treatmentTextForm(
  BuildContext context, {
  required String title,
  required Map<String, String> fields,
  Set<String> optional = const {},
  String submit = 'Gửi',
}) async {
  final generation = context.read<TreatmentProvider>().generation;
  final controllers = fields.map(
    (key, label) => MapEntry(key, TextEditingController()),
  );
  final key = GlobalKey<FormState>();
  final result = await showDialog<Map<String, String>>(
    context: context,
    builder: (context) {
      context.watch<AuthProvider>();
      if (generation != context.read<TreatmentProvider>().generation) {
        return AlertDialog(
          title: const Text('Phiên đăng nhập đã thay đổi'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Đóng'),
            ),
          ],
        );
      }
      return AlertDialog(
        title: Text(title),
        content: SingleChildScrollView(
          child: Form(
            key: key,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: fields.entries
                  .map(
                    (entry) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: TextFormField(
                        controller: controllers[entry.key],
                        decoration: InputDecoration(labelText: entry.value),
                        maxLines:
                            entry.key == 'extensionDays' ||
                                entry.key == 'amount' ||
                                entry.key == 'reference'
                            ? 1
                            : 3,
                        keyboardType:
                            entry.key == 'extensionDays' ||
                                entry.key == 'amount'
                            ? TextInputType.number
                            : TextInputType.multiline,
                        validator: (value) =>
                            !optional.contains(entry.key) &&
                                (value == null || value.trim().isEmpty)
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
            onPressed: () => Navigator.pop(context),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () {
              if (key.currentState!.validate()) {
                Navigator.pop(
                  context,
                  controllers.map(
                    (key, value) => MapEntry(key, value.text.trim()),
                  ),
                );
              }
            },
            child: Text(submit),
          ),
        ],
      );
    },
  );
  Future<void>.delayed(const Duration(milliseconds: 300), () {
    for (final controller in controllers.values) {
      controller.dispose();
    }
  });
  return result;
}
