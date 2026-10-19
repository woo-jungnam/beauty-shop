import 'package:flutter_test/flutter_test.dart';
import 'package:beautyshop_mobile/core/network/api_response.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:beautyshop_mobile/data/models/cart_model.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/data/models/auth_models.dart';
import 'package:beautyshop_mobile/data/models/spa_model.dart';

void main() {
  test('Product pages preserve backend prices and ratings', () {
    final response = ApiResponse<PageResponse<ProductItem>>.fromJson(
      {
        'status': 200,
        'data': {
          'content': [
            {
              'id': 101,
              'name': 'Serum',
              'minPrice': 425000,
              'averageRating': 4.9,
              'totalReviews': 20,
            },
          ],
          'totalElements': 1,
          'totalPages': 1,
        },
      },
      (data) => PageResponse.fromJson(
        data as Map<String, dynamic>,
        (item) => ProductItem.fromJson(item as Map<String, dynamic>),
      ),
    );
    expect(response.isSuccess, isTrue);
    expect(response.data!.content.single.displayPrice, 425000);
    expect(response.data!.content.single.averageRating, 4.9);
  });

  test('Missing page content is empty', () {
    final page = PageResponse<ProductItem>.fromJson({
      'content': null,
      'totalPages': null,
    }, (item) => ProductItem.fromJson(item as Map<String, dynamic>));
    expect(page.content, isEmpty);
    expect(page.page, 0);
  });

  test(
    'Cart preserves supplied shipping fee instead of using a local threshold',
    () {
      final cart = CartResponse.fromJson({
        'shippingFee': 49000,
        'items': [
          {'id': 10, 'variantId': 201, 'quantity': 2, 'price': 425000},
        ],
      });
      expect(cart.items.single.totalPrice, 850000);
      expect(cart.subtotal, 850000);
      expect(cart.shippingFee, 49000);
    },
  );

  test('SKU prices and active variants come from the backend', () {
    final product = ProductDetail.fromJson({
      'id': 1,
      'variants': [
        {
          'id': 1,
          'variantName': 'Inactive',
          'price': 100000,
          'isActive': false,
        },
        {
          'id': 2,
          'variantName': 'Sale',
          'price': 480000,
          'discountPrice': 408000,
          'isActive': true,
        },
      ],
    });
    expect(product.variants, hasLength(1));
    expect(product.variants.single.effectivePrice, 408000);
    expect(product.variants.single.discountPercent, 15);
  });

  test('Product business facts retain true and false values', () {
    final product = ProductDetail.fromJson({
      'id': 2,
      'originCountry': 'Việt Nam',
      'hasFragrance': false,
      'hasAlcohol': true,
      'averageRating': 4.5,
      'totalReviews': 12,
      'totalSold': 34,
    });
    expect(product.originCountry, 'Việt Nam');
    expect(product.hasFragrance, isFalse);
    expect(product.hasAlcohol, isTrue);
    expect(product.totalSold, 34);
  });

  test('Order payment instructions and deadline retain API values', () {
    final order = OrderResponse.fromJson({
      'id': 1001,
      'orderNumber': 'ORD-001',
      'paymentDeadline': '2026-09-26T11:00:00Z',
      'paymentStatus': 'PENDING',
      'paymentInstruction': {
        'bankName': 'API Bank',
        'bankAccountNumber': '12345',
        'transferSyntax': 'ORD-001',
      },
    });
    expect(order.orderCode, 'ORD-001');
    expect(order.paymentInstruction!.bankName, 'API Bank');
    expect(order.paymentDeadline, '2026-09-26T11:00:00Z');
    expect(order.isPaid, isFalse);
  });

  test('Absent payment instructions stay absent', () {
    final order = OrderResponse.fromJson({'id': 1});
    expect(order.paymentInstruction, isNull);
  });

  test('Flattened authentication response preserves the customer identity', () {
    final auth = AuthResponse.fromJson({
      'accessToken': 'test-token',
      'id': 1,
      'username': 'customer',
      'fullName': 'Customer',
      'roles': ['ROLE_CUSTOMER'],
    });
    expect(auth.user!.username, 'customer');
    expect(auth.user!.role, 'ROLE_CUSTOMER');
    expect(auth.user!.membershipTier, isNull);
  });

  test('Membership and loyalty points retain API values', () {
    final user = User.fromJson({
      'id': 1,
      'membershipTier': 'GOLD',
      'loyaltyPoints': 280,
    });
    expect(user.membershipTier, 'GOLD');
    expect(user.rewardPoints, 280);
  });

  test('Appointments and spa tickets use actual response names and counts', () {
    final appointment = Appointment.fromJson({
      'id': 50,
      'appointmentDate': '2026-09-27',
      'startTime': '09:00:00',
      'items': [
        {'serviceId': 10, 'serviceName': 'Chăm sóc da'},
      ],
    });
    expect(appointment.serviceId, 10);
    expect(appointment.serviceName, 'Chăm sóc da');
    final ticket = UserServiceTicket.fromJson({
      'id': 5,
      'packageName': 'Liệu trình',
      'totalSessions': 10,
      'remainingSessions': 8,
      'status': 'ACTIVE',
    });
    expect(ticket.serviceName, 'Liệu trình');
    expect(ticket.totalQuantity, 10);
    expect(ticket.remainingQuantity, 8);
  });

  test('Expired or incomplete spa tickets cannot claim to be available', () {
    expect(UserServiceTicket.fromJson({'id': 5}).isValid, isFalse);
    expect(
      UserServiceTicket.fromJson({
        'id': 5,
        'status': 'ACTIVE',
        'remainingSessions': 3,
        'expiryDate': '2020-01-01T00:00:00Z',
      }).isValid,
      isFalse,
    );
  });

  test('Past or missing appointment dates cannot enable cancellation', () {
    final tomorrow = DateTime.now()
        .add(const Duration(days: 1))
        .toIso8601String()
        .substring(0, 10);
    Appointment appointment(String status, String? date) =>
        Appointment.fromJson({
          'id': 1,
          'appointmentDate': date,
          'startTime': '09:00:00',
          'status': status,
        });
    expect(appointment('PENDING', tomorrow).canCancel, isTrue);
    expect(appointment('COMPLETED', tomorrow).canCancel, isFalse);
    expect(appointment('PENDING', '2020-01-01').canCancel, isFalse);
    expect(appointment('PENDING', null).canCancel, isFalse);
  });
}
