import '../constants/api_constants.dart';

class ImageUtils {
  /// Chuẩn hóa đường dẫn hình ảnh (tương đối /uploads, localhost, //...)
  /// thành URL tuyệt đối hợp lệ để CachedNetworkImage hoặc Image.network hiển thị được trên mobile.
  static String? resolveUrl(String? url) {
    if (url == null) return null;
    final trimmed = url.trim();
    if (trimmed.isEmpty) return null;

    // Protocol-relative: //example.com/image.png -> https://example.com/image.png
    if (trimmed.startsWith('//')) {
      return 'https:$trimmed';
    }

    final baseUrl = ApiConstants.baseUrl.replaceAll(RegExp(r'/+$'), '');

    // Đường dẫn tương đối: /uploads/... hoặc uploads/... hoặc /images/...
    if (trimmed.startsWith('/')) {
      return '$baseUrl$trimmed';
    }
    if (!trimmed.startsWith('http://') && !trimmed.startsWith('https://')) {
      return '$baseUrl/$trimmed';
    }

    // Thay thế localhost/127.0.0.1 bằng host thật từ BACKEND_URL (ví dụ 10.0.2.2 trên Android Emulator)
    try {
      final uri = Uri.parse(trimmed);
      if (uri.host == 'localhost' || uri.host == '127.0.0.1') {
        final baseUri = Uri.parse(baseUrl);
        final resolved = uri.replace(
          scheme: baseUri.scheme,
          host: baseUri.host,
          port: baseUri.hasPort ? baseUri.port : null,
        );
        return resolved.toString();
      }
    } catch (_) {}

    return trimmed;
  }
}
