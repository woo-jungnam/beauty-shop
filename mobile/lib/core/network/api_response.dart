class ApiResponse<T> {
  final int status;
  final String? errorCode;
  final String? message;
  final T? data;
  final dynamic errors;
  final String? timestamp;
  final String? path;

  ApiResponse({
    this.status = 200,
    this.errorCode,
    this.message,
    this.data,
    this.errors,
    this.timestamp,
    this.path,
  });

  bool get isSuccess => status >= 200 && status < 300;

  factory ApiResponse.success(T? data, {String message = 'Thành công', int status = 200}) {
    return ApiResponse(
      status: status,
      message: message,
      data: data,
    );
  }

  factory ApiResponse.error(String message, {int status = 400}) {
    return ApiResponse(
      status: status,
      message: message,
    );
  }

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic json)? fromJsonT,
  ) {
    return ApiResponse<T>(
      status: (json['status'] as num?)?.toInt() ?? 200,
      errorCode: json['errorCode']?.toString(),
      message: json['message']?.toString(),
      data: json['data'] != null && fromJsonT != null
          ? fromJsonT(json['data'])
          : json['data'] as T?,
      errors: json['errors'],
      timestamp: json['timestamp']?.toString(),
      path: json['path']?.toString(),
    );
  }
}

class PageResponse<T> {
  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool last;

  PageResponse({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.last,
  });

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic json) fromJsonT,
  ) {
    final list = json['content'] as List<dynamic>? ?? [];
    return PageResponse<T>(
      content: list.map((item) => fromJsonT(item)).toList(),
      page: (json['page'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? list.length,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? list.length,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 1,
      last: json['last'] == true,
    );
  }
}

class ApiException implements Exception {
  final int statusCode;
  final String? errorCode;
  final String message;
  final dynamic errors;

  ApiException({
    required this.statusCode,
    this.errorCode,
    required this.message,
    this.errors,
  });

  @override
  String toString() => 'ApiException [$statusCode - $errorCode]: $message';
}
