import 'package:flutter_test/flutter_test.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:beautyshop_mobile/data/models/cart_model.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/data/models/auth_models.dart';
import 'package:beautyshop_mobile/data/models/spa_model.dart';

void main() {
  group('Beauty Shop Model JSON Parsing Tests', () {
    test('ProductItem and PageResponse parse properly', () {
      final json = {
        'status': 200,
        'message': 'Success',
        'data': {
          'content': [
            {
              'id': 101,
              'name': 'Kem Chống Nắng La Roche-Posay',
              'slug': 'kem-chong-nang-la-roche-posay',
              'basePrice': 425000,
              'status': 'ACTIVE',
              'averageRating': 4.9,
            },
          ],
          'page': 0,
          'size': 20,
          'totalElements': 1,
          'totalPages': 1,
          'last': true,
        },
      };

      final apiResponse = ApiResponse<PageResponse<ProductItem>>.fromJson(
        json,
        (data) => PageResponse.fromJson(
          data as Map<String, dynamic>,
          (item) => ProductItem.fromJson(item as Map<String, dynamic>),
        ),
      );

      expect(apiResponse.isSuccess, isTrue);
      expect(apiResponse.data!.content.length, equals(1));
      final product = apiResponse.data!.content.first;
      expect(product.id, equals(101));
      expect(product.name, equals('Kem Chống Nắng La Roche-Posay'));
      expect(product.basePrice, equals(425000.0));
    });

    test(
      'PageResponse handles null and missing fields gracefully without crash',
      () {
        final json = <String, dynamic>{
          'content': null,
          'page': null,
          'size': null,
          'totalElements': null,
          'totalPages': null,
          'last': null,
        };

        final page = PageResponse<ProductItem>.fromJson(
          json,
          (item) => ProductItem.fromJson(item as Map<String, dynamic>),
        );

        expect(page.content, isEmpty);
        expect(page.page, equals(0));
        expect(page.size, equals(0));
        expect(page.totalElements, equals(0));
        expect(page.totalPages, equals(1));
        expect(page.last, isFalse);
      },
    );

    test('Cart and CartItem parse properly', () {
      final cartJson = {
        'id': 1,
        'totalPrice': 850000,
        'items': [
          {
            'id': 10,
            'variantId': 201,
            'sku': 'SKU-001',
            'variantName': 'Chai 50ml',
            'quantity': 2,
            'price': 425000,
            'available': false,
          },
        ],
      };

      final cart = Cart.fromJson(cartJson);
      expect(cart.id, equals(1));
      expect(cart.totalItemCount, equals(2));
      expect(cart.items.first.subtotal, equals(850000.0));
      expect(cart.items.first.available, isFalse);
    });

    test(
      'Product variant uses discount price and ignores inactive variants',
      () {
        final product = ProductDetail.fromJson({
          'id': 1,
          'name': 'Sản phẩm',
          'slug': 'san-pham',
          'basePrice': 190000,
          'status': 'ACTIVE',
          'variants': [
            {
              'id': 1,
              'sku': 'INACTIVE',
              'variantName': 'Ngừng bán',
              'price': 100000,
              'isActive': false,
            },
            {
              'id': 2,
              'sku': 'SALE',
              'variantName': 'Khuyến mãi',
              'price': 480000,
              'discountPrice': 408000,
              'isDefault': true,
              'isActive': true,
            },
          ],
        });

        expect(product.variants, hasLength(1));
        expect(product.variants.first.id, equals(2));
        expect(product.variants.first.effectivePrice, equals(408000));
        expect(product.variants.first.hasDiscount, isTrue);
      },
    );

    test('Product detail parses business facts supplied by backend', () {
      final product = ProductDetail.fromJson({
        'id': 2,
        'name': 'Serum',
        'slug': 'serum',
        'basePrice': 250000,
        'status': 'ACTIVE',
        'howToUse': 'Dùng buổi tối',
        'originCountry': 'Việt Nam',
        'volume': '30ml',
        'hasFragrance': false,
        'hasAlcohol': true,
        'averageRating': 4.5,
        'totalReviews': 12,
        'totalSold': 34,
      });

      expect(product.howToUse, equals('Dùng buổi tối'));
      expect(product.originCountry, equals('Việt Nam'));
      expect(product.hasFragrance, isFalse);
      expect(product.hasAlcohol, isTrue);
      expect(product.averageRating, equals(4.5));
      expect(product.totalReviews, equals(12));
      expect(product.totalSold, equals(34));
    });

    test(
      'Order and PaymentInstruction parse properly with new deadline and paidAmount',
      () {
        final orderJson = {
          'id': 1001,
          'orderNumber': 'ORD-2026-001',
          'status': 'PENDING',
          'paymentMethod': 'BANK',
          'paymentStatus': 'PENDING',
          'totalAmount': 850000,
          'paidAmount': 0.0,
          'paymentDeadline': '2026-09-26T11:00:00Z',
          'paymentInstruction': {
            'method': 'BANK',
            'bankName': 'MB Bank',
            'bankAccountNumber': '0912345678',
            'transferSyntax': 'SEPAY ORD-2026-001',
            'qrCodeUrl':
                'https://api.vietqr.io/image/970422-0912345678-compact.jpg',
          },
          'items': [],
        };

        final order = Order.fromJson(orderJson);
        expect(order.id, equals(1001));
        expect(order.orderNumber, equals('ORD-2026-001'));
        expect(order.paymentInstruction, isNotNull);
        expect(order.paymentInstruction!.bankName, equals('MB Bank'));
        expect(order.paymentDeadline, equals('2026-09-26T11:00:00Z'));
        expect(order.isPaid, isFalse);
      },
    );

    test('UserProfile and flattened AuthResponse parse properly', () {
      final authJson = {
        'accessToken': 'dummy-jwt-access-token',
        'refreshToken': 'dummy-jwt-refresh-token',
        'tokenType': 'Bearer',
        'id': 1,
        'username': 'thanhnam',
        'email': 'nam@example.com',
        'fullName': 'Nguyễn Thành Nam',
        'roles': ['ROLE_CUSTOMER'],
      };

      final auth = AuthResponse.fromJson(authJson);
      expect(auth.accessToken, equals('dummy-jwt-access-token'));
      expect(auth.user, isNotNull);
      expect(auth.user!.username, equals('thanhnam'));
      expect(auth.user!.fullName, equals('Nguyễn Thành Nam'));
    });

    test('Membership discount percentages match backend tiers', () {
      UserProfile profile(String tier) => UserProfile(
        id: 1,
        username: 'user',
        email: 'user@example.com',
        membershipTier: tier,
      );

      expect(profile('MEMBER').membershipDiscountPercentage, equals(0));
      expect(profile('SILVER').membershipDiscountPercentage, equals(5));
      expect(profile('GOLD').membershipDiscountPercentage, equals(10));
      expect(profile('PLATINUM').membershipDiscountPercentage, equals(15));
    });

    test('Spa package purchase result maps explicitly to payment order', () {
      final result = SpaPackageOrderResult.fromJson({
        'orderId': 99,
        'servicePackageId': 8,
        'orderNumber': 'ORD-SPA-99',
        'totalAmount': 1800000,
        'status': 'PENDING',
        'paymentMethod': 'BANK',
        'paymentStatus': 'PENDING',
        'paymentDeadline': '2026-09-27T12:00:00Z',
        'paymentInstruction': {
          'method': 'BANK',
          'bankName': 'MB Bank',
          'bankAccountNumber': '0912345678',
          'transferSyntax': 'ORD-SPA-99',
        },
      });

      final order = result.toOrder();
      expect(order.id, equals(99));
      expect(order.servicePackageId, equals(8));
      expect(order.paymentDeadline, equals('2026-09-27T12:00:00Z'));
      expect(order.paymentInstruction, isNotNull);
    });

    test(
      'Spa Appointment, Items and Ticket parse with serviceId and staffId',
      () {
        final appointmentJson = {
          'id': 50,
          'userId': 1,
          'customerName': 'Nguyễn Thành Nam',
          'appointmentDate': '2026-09-27',
          'startTime': '09:00:00',
          'endTime': '10:00:00',
          'status': 'CONFIRMED',
          'items': [
            {
              'id': 123,
              'serviceId': 10,
              'staffId': 45,
              'serviceName': 'Chăm sóc da mặt chuyên sâu',
              'staffName': 'Staff #45',
              'price': 350000,
              'startTime': '09:00:00',
              'endTime': '10:00:00',
            },
          ],
        };

        final appointment = Appointment.fromJson(appointmentJson);
        expect(appointment.id, equals(50));
        expect(appointment.items.length, equals(1));
        final item = appointment.items.first;
        expect(item.id, equals(123));
        expect(item.serviceId, equals(10));
        expect(item.staffId, equals(45));
        expect(item.serviceName, equals('Chăm sóc da mặt chuyên sâu'));

        final ticketJson = {
          'id': 5,
          'packageName': 'Gói Trị Liệu Trắng Sáng',
          'totalSessions': 10,
          'usedSessions': 2,
          'remainingSessions': 8,
          'status': 'ACTIVE',
          'remainingByService': {'10': 4, '11': 4},
        };

        final ticket = UserServiceTicket.fromJson(ticketJson);
        expect(ticket.id, equals(5));
        expect(ticket.remainingByService[10], equals(4));
        expect(ticket.remainingByService[11], equals(4));
      },
    );

    test('Appointment actions follow backend status and future-time rules', () {
      final tomorrow = DateTime.now().add(const Duration(days: 1));
      final yesterday = DateTime.now().subtract(const Duration(days: 1));
      String date(DateTime value) =>
          '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

      Appointment appointment(String status, DateTime day) =>
          Appointment.fromJson({
            'id': 1,
            'appointmentDate': date(day),
            'startTime': '09:00:00',
            'status': status,
            'items': [],
          });

      expect(appointment('PENDING', tomorrow).canCancel, isTrue);
      expect(appointment('CONFIRMED', tomorrow).canCancel, isTrue);
      expect(appointment('COMPLETED', tomorrow).canCancel, isFalse);
      expect(appointment('PENDING', yesterday).canCancel, isFalse);
    });
  });
}
