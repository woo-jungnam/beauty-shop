import 'package:beautyshop_shipper/core/constants/api_constants.dart';
import 'package:beautyshop_shipper/data/models/shipper_order_model.dart';
import 'package:beautyshop_shipper/presentation/screens/delivery/shipper_order_detail_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Order snapshot resolves item and delivery photo URLs', () {
    final order = ShipperOrderModel.fromJson({
      'id': 1,
      'totalAmount': 220000,
      'deliveryProofUrl': '/uploads/proof.jpg',
      'items': [
        {
          'productName': 'Kem dưỡng',
          'variantName': '50 ml',
          'sku': 'KEM-50',
          'quantity': 2,
          'price': 100000,
          'imageUrl': '/uploads/item.jpg',
        },
      ],
    });
    expect(order.deliveryProofUrl, '${ApiConstants.baseUrl}/uploads/proof.jpg');
    final item = order.items.single;
    expect(item.imageUrl, '${ApiConstants.baseUrl}/uploads/item.jpg');
    expect(item.sku, 'KEM-50');
    expect(item.quantity, 2);
  });

  testWidgets('Order detail shows line items on a narrow display', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 580));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final order = ShipperOrderModel.fromJson({
      'orderNumber': 'DELIVERY-71',
      'customerName': 'Người nhận',
      'shippingAddress': 'Địa chỉ giao hàng',
      'totalAmount': 220000,
      'shippingFee': 20000,
      'itemsSummary': ['Bản tóm tắt cũ'],
      'items': [
        {
          'productName': 'Kem dưỡng',
          'variantName': '50 ml',
          'sku': 'KEM-50',
          'quantity': 2,
          'price': 100000,
        },
      ],
    });
    await tester.pumpWidget(
      MaterialApp(home: ShipperOrderDetailScreen(order: order)),
    );
    expect(find.text('Chi tiết đơn giao'), findsOneWidget);
    expect(find.text('SKU: KEM-50'), findsOneWidget);
    expect(find.text('Bản tóm tắt cũ'), findsNothing);
    expect(find.byIcon(Icons.image_not_supported_outlined), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
