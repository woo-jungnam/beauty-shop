import 'dart:math';

class IdempotencyUtils {
  static String generateKey([String prefix = 'key']) {
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final random = Random().nextInt(999999).toString().padLeft(6, '0');
    return '$prefix-$timestamp-$random';
  }
}

String generateIdempotencyKey([String prefix = 'ord']) => IdempotencyUtils.generateKey(prefix);

