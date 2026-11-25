import 'dart:convert';
import 'package:dio/dio.dart';
import '../constants/api_constants.dart';
import '../storage/storage_service.dart';

class ApiResponse<T> {
  final int status;
  final String? message;
  final T? data;

  ApiResponse({required this.status, this.message, this.data});

  bool get isSuccess => status >= 200 && status < 300;

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic json)? fromJsonT,
  ) {
    return ApiResponse<T>(
      status: json['status'] as int? ?? 200,
      message: json['message'] as String?,
      data: json['data'] != null && fromJsonT != null
          ? fromJsonT(json['data'])
          : json['data'] as T?,
    );
  }
}

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;

  final Dio _dio = Dio(
    BaseOptions(
      connectTimeout: ApiConstants.connectTimeout,
      receiveTimeout: ApiConstants.receiveTimeout,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ),
  );
  Future<void>? _refreshing;
  int? _refreshVersion;

  ApiClient._internal() {
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          final server = ApiConstants.baseUrl;
          if (options.extra['server'] case final String expected
              when expected != server) {
            return handler.reject(
              DioException(
                requestOptions: options,
                type: DioExceptionType.cancel,
              ),
            );
          }
          options.extra['server'] = server;
          options.baseUrl = server;
          final current = StorageService.sessionVersion;
          if (options.extra['session'] case final int session
              when session != current) {
            return handler.reject(
              DioException(
                requestOptions: options,
                type: DioExceptionType.cancel,
              ),
            );
          }
          options.extra['session'] = current;
          final tokens = StorageService.tokens;
          if (tokens != null &&
              tokens.server == options.baseUrl &&
              !_isAuth(options.path)) {
            options.headers['Authorization'] = 'Bearer ${tokens.access}';
          }
          if (options.data is FormData) options.headers.remove('Content-Type');
          handler.next(options);
        },
        onError: (error, handler) async {
          final request = error.requestOptions;
          final version = request.extra['session'] as int?;
          if (error.response?.statusCode != 401 ||
              _isAuth(request.path) ||
              request.extra['retried'] == true ||
              version != StorageService.sessionVersion) {
            return handler.next(error);
          }
          try {
            await _renewSession(request, version!);
            if (version != StorageService.sessionVersion ||
                StorageService.tokens?.server != request.baseUrl ||
                ApiConstants.baseUrl != request.baseUrl) {
              return handler.next(error);
            }
            final data = request.data;
            request.extra['retried'] = true;
            if (data is FormData) request.data = data.clone();
            handler.resolve(await _dio.fetch<dynamic>(request));
          } on DioException catch (retryError) {
            handler.next(retryError);
          } catch (_) {
            handler.next(error);
          }
        },
      ),
    );
  }

  bool _isAuth(String path) => const {
    ApiConstants.login,
    ApiConstants.refresh,
    ApiConstants.logout,
  }.contains(path);

  Future<void> _renewSession(RequestOptions request, int version) async {
    final tokens = StorageService.tokens;
    if (tokens == null || tokens.server != request.baseUrl) return;
    if (_refreshVersion == version && _refreshing != null) {
      await _refreshing;
      return;
    }
    if (request.headers['Authorization'] != 'Bearer ${tokens.access}') return;

    _refreshVersion = version;
    final pending = _rotate(tokens, version);
    _refreshing = pending;
    try {
      await pending;
    } finally {
      if (identical(_refreshing, pending)) _refreshing = null;
    }
  }

  Future<void> _rotate(SessionTokens tokens, int version) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        ApiConstants.refresh,
        data: {'refreshToken': tokens.refresh},
        options: Options(extra: {'session': version, 'server': tokens.server}),
      );
      if (version != StorageService.sessionVersion ||
          tokens.server != ApiConstants.baseUrl) {
        return;
      }
      final data = response.data?['data'] as Map<String, dynamic>?;
      final access = data?['accessToken'];
      final refresh = data?['refreshToken'];
      if (access is! String ||
          access.isEmpty ||
          refresh is! String ||
          refresh.isEmpty) {
        await StorageService.clearAuth();
        return;
      }
      await StorageService.setTokens((
        access: access,
        refresh: refresh,
        server: tokens.server,
      ));
    } on DioException catch (e) {
      if (version == StorageService.sessionVersion &&
          const {400, 401, 403}.contains(e.response?.statusCode)) {
        await StorageService.clearAuth();
        return;
      }
      rethrow;
    }
  }

  Future<void> finishPendingRefresh() async {
    try {
      await _refreshing;
    } catch (_) {
      // The original request already handles the renewal error.
    }
  }

  Future<ApiResponse<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    T Function(dynamic json)? fromJsonT,
  }) async {
    try {
      final response = await _dio.get(path, queryParameters: queryParameters);
      return _handleResponse(response, fromJsonT);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<ApiResponse<T>> post<T>(
    String path, {
    dynamic data,
    Options? options,
    T Function(dynamic json)? fromJsonT,
  }) async {
    try {
      final response = await _dio.post(path, data: data, options: options);
      return _handleResponse(response, fromJsonT);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<ApiResponse<T>> put<T>(
    String path, {
    dynamic data,
    Options? options,
    T Function(dynamic json)? fromJsonT,
  }) async {
    try {
      final response = await _dio.put(path, data: data, options: options);
      return _handleResponse<T>(response, fromJsonT);
    } on DioException catch (e) {
      throw _handleError(e);
    }
  }

  Future<ApiResponse<T>> patch<T>(
    String path, {
    dynamic data,
    Options? options,
    T Function(dynamic json)? fromJsonT,
  }) async {
    try {
      final response = await _dio.patch(path, data: data, options: options);
      return _handleResponse(response, fromJsonT);
    } on DioException catch (e) {
      throw _handleError(e);
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
      } catch (_) {}
    }
    if (raw is Map<String, dynamic>) {
      return ApiResponse.fromJson(raw, fromJsonT);
    }
    return ApiResponse<T>(status: response.statusCode ?? 200, data: raw as T?);
  }

  String _handleError(DioException e) {
    if (e.response?.data != null) {
      final data = e.response!.data;
      if (data is Map && data['message'] != null) {
        return data['message'].toString();
      }
    }
    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout) {
      return 'Hết thời gian chờ phản hồi từ máy chủ';
    }
    if (e.type == DioExceptionType.connectionError) {
      return 'Không thể kết nối đến máy chủ (${ApiConstants.baseUrl}). Kiểm tra địa chỉ backend hoặc kết nối mạng.';
    }
    return 'Lỗi kết nối máy chủ (${e.response?.statusCode ?? 'Unknown'})';
  }
}
