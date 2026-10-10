import 'dart:convert';
import 'dart:async';
import 'package:dio/dio.dart';
import '../constants/api_constants.dart';
import '../storage/storage_service.dart';
import 'api_response.dart';

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;

  late Dio _dio;
  Completer<String?>? _refreshCompleter;

  ApiClient._internal() {
    _dio = Dio(
      BaseOptions(
        baseUrl: ApiConstants.baseUrl,
        connectTimeout: ApiConstants.connectTimeout,
        receiveTimeout: ApiConstants.receiveTimeout,
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
      ),
    );

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          options.baseUrl = ApiConstants.baseUrl;

          final token = StorageService.getAccessToken();
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }

          options.headers['X-Guest-Session-Id'] = StorageService.getGuestSessionId();

          return handler.next(options);
        },
        onError: (DioException error, handler) async {
          if (error.response?.statusCode == 401 &&
              error.requestOptions.path != ApiConstants.refreshToken &&
              error.requestOptions.extra['authRetried'] != true) {
            final newAccessToken = await _refreshAccessToken();
            if (newAccessToken != null) {
              final originalRequest = error.requestOptions;
              originalRequest.extra['authRetried'] = true;
              originalRequest.headers['Authorization'] = 'Bearer $newAccessToken';
              try {
                return handler.resolve(await _dio.fetch(originalRequest));
              } on DioException catch (retryError) {
                return handler.next(retryError);
              }
            }
          }

          return handler.next(error);
        },
      ),
    );
  }

  Future<String?> _refreshAccessToken() async {
    final existing = _refreshCompleter;
    if (existing != null) return existing.future;

    final completer = Completer<String?>();
    _refreshCompleter = completer;
    try {
      final refreshToken = StorageService.getRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        await StorageService.clearAuth();
        completer.complete(null);
        return completer.future;
      }

      final refreshResponse = await Dio(
        BaseOptions(
          connectTimeout: ApiConstants.connectTimeout,
          receiveTimeout: ApiConstants.receiveTimeout,
        ),
      ).post(
        '${ApiConstants.baseUrl}${ApiConstants.refreshToken}',
        data: {'refreshToken': refreshToken},
        options: Options(headers: {'Content-Type': 'application/json'}),
      );
      final envelope = refreshResponse.data as Map<String, dynamic>?;
      final data = envelope?['data'] as Map<String, dynamic>?;
      final accessToken = data?['accessToken'] as String?;
      if (accessToken == null || accessToken.isEmpty) {
        throw StateError('Refresh response does not contain an access token');
      }
      await StorageService.setAccessToken(accessToken);
      final rotatedRefreshToken = data?['refreshToken'] as String?;
      if (rotatedRefreshToken != null && rotatedRefreshToken.isNotEmpty) {
        await StorageService.setRefreshToken(rotatedRefreshToken);
      }
      completer.complete(accessToken);
    } catch (_) {
      await StorageService.clearAuth();
      completer.complete(null);
    } finally {
      _refreshCompleter = null;
    }
    return completer.future;
  }

  // GET
  Future<ApiResponse<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic json)? fromJsonT,
  }) async {
    try {
      final response = await _dio.get(
        path,
        queryParameters: queryParameters,
        options: Options(headers: headers),
      );
      return _handleResponse(response, fromJsonT);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  // POST
  Future<ApiResponse<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic json)? fromJsonT,
  }) async {
    try {
      final response = await _dio.post(
        path,
        data: data,
        queryParameters: queryParameters,
        options: Options(headers: headers),
      );
      return _handleResponse(response, fromJsonT);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  // PUT
  Future<ApiResponse<T>> put<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic json)? fromJsonT,
  }) async {
    try {
      final response = await _dio.put(
        path,
        data: data,
        queryParameters: queryParameters,
        options: Options(headers: headers),
      );
      return _handleResponse(response, fromJsonT);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  // DELETE
  Future<ApiResponse<T>> delete<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    T Function(dynamic json)? fromJsonT,
  }) async {
    try {
      final response = await _dio.delete(
        path,
        data: data,
        queryParameters: queryParameters,
        options: Options(headers: headers),
      );
      return _handleResponse(response, fromJsonT);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  // Server-Sent Events (SSE) Stream
  Stream<Map<String, dynamic>> postSse(
    String path, {
    required dynamic data,
  }) async* {
    try {
      final response = await _dio.post<ResponseBody>(
        path,
        data: data,
        options: Options(
          responseType: ResponseType.stream,
          headers: {'Accept': 'text/event-stream'},
          receiveTimeout: const Duration(minutes: 2),
        ),
      );
      final body = response.data;
      if (body == null) throw StateError('SSE response body is empty');
      await for (final line in body.stream
          .map<List<int>>((chunk) => chunk)
          .transform(utf8.decoder)
          .transform(const LineSplitter())) {
        if (!line.startsWith('data:')) continue;
        final payload = line.substring(5).trim();
        if (payload.isEmpty || payload == '[DONE]') continue;
        final decoded = jsonDecode(payload);
        if (decoded is Map<String, dynamic>) yield decoded;
      }
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  ApiResponse<T> _handleResponse<T>(
    Response response,
    T Function(dynamic json)? fromJsonT,
  ) {
    dynamic raw = response.data;
    if (raw is String) {
      try {
        raw = jsonDecode(raw);
      } on FormatException {
        // Plain-text body
      }
    }

    if (raw is Map<String, dynamic>) {
      return ApiResponse.fromJson(raw, fromJsonT);
    }

    return ApiResponse<T>(status: response.statusCode ?? 200, data: raw as T?);
  }

  ApiException _handleDioError(DioException e) {
    int statusCode = e.response?.statusCode ?? 500;
    String message = 'Đã có lỗi xảy ra trong kết nối máy chủ';
    String? errorCode;
    dynamic errors;

    if (e.response?.data != null) {
      dynamic data = e.response!.data;
      if (data is String) {
        try {
          data = jsonDecode(data);
        } on FormatException {}
      }

      if (data is Map<String, dynamic>) {
        message = data['message'] as String? ?? message;
        errorCode = data['errorCode'] as String?;
        errors = data['errors'];
      }
    } else if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout) {
      message = 'Hết thời gian chờ phản hồi từ máy chủ (Timeout)';
    } else if (e.type == DioExceptionType.connectionError) {
      message = 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra địa chỉ backend hoặc mạng internet.';
    }

    return ApiException(
      statusCode: statusCode,
      errorCode: errorCode,
      message: message,
      errors: errors,
    );
  }
}
