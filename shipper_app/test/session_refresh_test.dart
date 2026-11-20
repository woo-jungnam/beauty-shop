import 'dart:convert';
import 'dart:io';

import 'package:beautyshop_shipper/core/constants/api_constants.dart';
import 'package:beautyshop_shipper/core/network/api_client.dart';
import 'package:beautyshop_shipper/core/storage/storage_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttp extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  HttpOverrides.global = _RealHttp();

  test(
    'Concurrent expiry rotates once and retries multipart with its photo',
    () async {
      SharedPreferences.setMockInitialValues({});
      FlutterSecureStorage.setMockInitialValues({});
      await StorageService.init();
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final origin = 'http://127.0.0.1:${server.port}';
      ApiConstants.setBaseUrl(origin);
      await StorageService.setTokens((
        access: 'expired',
        refresh: 'original',
        server: origin,
      ), newSession: true);

      var rotations = 0;
      var rejectRefresh = false;
      String? retriedUpload;
      server.listen((request) async {
        final body = await utf8.decoder.bind(request).join();
        final response = request.response;
        final bearer = request.headers.value(HttpHeaders.authorizationHeader);
        response.headers.contentType = ContentType.json;
        if (request.uri.path == ApiConstants.refresh) {
          rotations++;
          await Future<void>.delayed(const Duration(milliseconds: 40));
          if (rejectRefresh) {
            response.statusCode = 401;
          } else {
            expect(jsonDecode(body), {'refreshToken': 'original'});
            response.write(
              jsonEncode({
                'status': 200,
                'data': {'accessToken': 'renewed', 'refreshToken': 'rotated'},
              }),
            );
          }
        } else if (bearer == 'Bearer renewed' && !rejectRefresh) {
          if (request.method == 'POST') retriedUpload = body;
          response.write('{"status":200,"data":{}}');
        } else {
          response.statusCode = 401;
        }
        await response.close();
      });

      final client = ApiClient();
      await Future.wait([
        client.get<dynamic>(ApiConstants.shipperOrders),
        client.post<dynamic>(
          ApiConstants.shipperCompleteDelivery(1),
          data: FormData.fromMap({
            'proofImage': MultipartFile.fromString(
              'photo-content',
              filename: 'photo.jpg',
            ),
          }),
        ),
      ]);
      expect(rotations, 1);
      expect(retriedUpload, contains('photo-content'));
      expect(StorageService.tokens?.refresh, 'rotated');

      rejectRefresh = true;
      await expectLater(
        client.get<dynamic>(ApiConstants.shipperOrders),
        throwsA(isA<String>()),
      );
      expect(StorageService.tokens, isNull);
      expect(rotations, 2);
    },
  );
}
