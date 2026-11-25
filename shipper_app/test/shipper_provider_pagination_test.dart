import 'dart:convert';
import 'dart:io';

import 'package:beautyshop_shipper/core/constants/api_constants.dart';
import 'package:beautyshop_shipper/core/storage/storage_service.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttp extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  HttpOverrides.global = _RealHttp();

  test(
    'ShipperProvider paginates, filters by status and keyword, resizes pages, and clamps out-of-range pages',
    () async {
      SharedPreferences.setMockInitialValues({});
      FlutterSecureStorage.setMockInitialValues({});
      await StorageService.init();

      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final origin = 'http://127.0.0.1:${server.port}';
      ApiConstants.setBaseUrl(origin);
      await StorageService.setTokens((
        access: 'valid-token',
        refresh: 'refresh-token',
        server: origin,
      ), newSession: true);

      final allOrders = List.generate(25, (index) {
        final number = index + 1;
        final code = number.toString().padLeft(2, '0');
        final status = switch (index % 3) {
          0 => 'SHIPPED',
          1 => 'DELIVERED',
          _ => 'DELIVERY_FAILED',
        };
        return {
          'id': number,
          'orderNumber': 'ORD-HTTP-$code',
          'customerName': 'Khách $code',
          'customerPhone': '09010000$code',
          'shippingAddress': '$code Lê Lợi',
          'totalAmount': 200000,
          'codAmount': status == 'SHIPPED' ? 200000 : 0,
          'paymentMethod': 'COD',
          'paymentStatus': 'PENDING',
          'status': status,
        };
      });

      final requestedQueries = <Map<String, String>>[];
      server.listen((request) async {
        final response = request.response;
        response.headers.contentType = ContentType.json;
        if (request.uri.path == ApiConstants.shipperOrders) {
          final q = request.uri.queryParameters;
          requestedQueries.add(q);
          final page = int.tryParse(q['page'] ?? '0') ?? 0;
          final size = int.tryParse(q['size'] ?? '10') ?? 10;
          final status = q['status'];
          final keyword = (q['keyword'] ?? '').toLowerCase();
          final filtered = allOrders.where((row) {
            final matchesStatus = status == null || row['status'] == status;
            final matchesKw =
                keyword.isEmpty ||
                (row['orderNumber'] as String).toLowerCase().contains(keyword) ||
                (row['customerPhone'] as String).contains(keyword);
            return matchesStatus && matchesKw;
          }).toList();
          final totalElements = filtered.length;
          final totalPages =
              totalElements == 0 ? 0 : ((totalElements + size - 1) ~/ size);
          final start = page * size;
          final end = (start + size).clamp(0, totalElements);
          final slice = start >= totalElements
              ? <Map<String, dynamic>>[]
              : filtered.sublist(start, end);
          response.write(
            jsonEncode({
              'status': 200,
              'data': {
                'content': slice,
                'page': page,
                'size': size,
                'totalElements': totalElements,
                'totalPages': totalPages,
                'last': totalPages == 0 || page + 1 >= totalPages,
              },
            }),
          );
        } else {
          response.statusCode = 404;
        }
        await response.close();
      });

      final provider = ShipperProvider();
      addTearDown(provider.dispose);

      // Page 0, default size 10
      await provider.fetchOrders();
      expect(provider.currentPage, 0);
      expect(provider.pageSize, 10);
      expect(provider.totalElements, 25);
      expect(provider.totalPages, 3);
      expect(provider.hasPreviousPage, isFalse);
      expect(provider.hasNextPage, isTrue);
      expect(provider.orders.length, 10);
      expect(provider.orders.first.orderNumber, 'ORD-HTTP-01');

      // Navigate to page 1 and page 2
      await provider.nextPage();
      expect(provider.currentPage, 1);
      expect(provider.hasPreviousPage, isTrue);
      expect(provider.orders.first.orderNumber, 'ORD-HTTP-11');

      await provider.goToPage(2);
      expect(provider.currentPage, 2);
      expect(provider.orders.length, 5);
      expect(provider.hasNextPage, isFalse);

      // Filter by status resets to page 0 and queries status
      await provider.setFilter('SHIPPED');
      expect(provider.currentFilter, 'SHIPPED');
      expect(provider.currentPage, 0);
      expect(provider.totalElements, 9);
      expect(provider.totalPages, 1);
      expect(requestedQueries.last['status'], 'SHIPPED');

      // Reset status filter, jump to page 2, then resize page -> 20 shrinks totalPages to 2
      await provider.setFilter('ALL');
      await provider.goToPage(2);
      expect(provider.currentPage, 2);

      // Request out-of-range page directly while status returns fewer pages -> auto clamps
      await provider.fetchOrders(page: 5);
      expect(provider.currentPage, 2);
      expect(provider.orders.length, 5);

      await provider.setPageSize(20);
      expect(provider.pageSize, 20);
      expect(provider.currentPage, 0);
      expect(provider.totalPages, 2);
      expect(provider.orders.length, 20);

      // Keyword search forwards keyword param and resets page
      await provider.goToPage(1);
      await provider.setSearchKeyword('0901000024');
      expect(provider.currentPage, 0);
      expect(provider.totalElements, 1);
      expect(provider.orders.single.orderNumber, 'ORD-HTTP-24');
      expect(requestedQueries.last['keyword'], '0901000024');

      provider.clearSession();
      expect(provider.currentPage, 0);
      expect(provider.totalElements, 0);
      expect(provider.totalPages, 0);
      expect(provider.currentFilter, 'ALL');
      expect(provider.searchKeyword, '');
      expect(provider.pageSize, ShipperProvider.defaultPageSize);
    },
  );
}

