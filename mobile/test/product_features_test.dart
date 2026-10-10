import 'package:flutter_test/flutter_test.dart';
import 'package:beautyshop_mobile/data/models/product_model.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/providers/product_provider.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/presentation/screens/product_detail/product_detail_screen.dart';

class TestProducts extends ProductProvider {
  final ProductDetail detail;
  TestProducts(this.detail);
  @override
  Future<void> loadInitialData() async {}
  @override
  ProductDetail get selectedProductDetail => detail;
  @override
  Future<ProductDetail?> fetchProductDetail(int id) async => detail;
}

class TestCart extends CartProvider {
  @override
  Future<void> fetchShippingPolicy() async {}
  @override
  Future<void> fetchCart() async {}
}

void main() {
  testWidgets('SKU selection filters attributes and full INCI remains readable', (tester) async {
    final product = ProductDetail.fromJson({
      'id': 1, 'name': 'Test serum', 'ingredients': 'Aqua, Niacinamide, Glycerin, Panthenol',
      'variants': [
        {'id': 9, 'variantName': '30ml', 'price': 100, 'isActive': true, 'isDefault': true},
        {'id': 10, 'variantName': '50ml', 'price': 200, 'isActive': true},
      ],
      'attributeValues': [
        {'id': 1, 'attributeDefinitionName': 'SPF', 'value': '50'},
        {'id': 2, 'attributeDefinitionName': 'Kết cấu', 'value': 'Gel', 'productVariantId': 9},
        {'id': 3, 'attributeDefinitionName': 'Kết cấu', 'value': 'Cream', 'productVariantId': 10},
      ],
    });
    await tester.pumpWidget(MultiProvider(providers: [
      ChangeNotifierProvider<ProductProvider>(create: (_) => TestProducts(product)),
      ChangeNotifierProvider<CartProvider>(create: (_) => TestCart()),
    ], child: const MaterialApp(home: ProductDetailScreen(productId: 1))));
    await tester.pumpAndSettle();
    expect(find.text('Gel'), findsOneWidget);
    expect(find.text('Cream'), findsNothing);
    await tester.ensureVisible(find.byWidgetPredicate((widget) => widget is ChoiceChip && widget.label is Text && ((widget.label as Text).data?.startsWith('50ml (') ?? false)));
    await tester.tap(find.byWidgetPredicate((widget) => widget is ChoiceChip && widget.label is Text && ((widget.label as Text).data?.startsWith('50ml (') ?? false)));
    await tester.pumpAndSettle();
    expect(find.text('Cream'), findsOneWidget);
    expect(find.text('Gel'), findsNothing);
    final inci = find.text('Bảng thành phần INCI đầy đủ');
    await tester.ensureVisible(inci);
    await tester.tap(inci);
    await tester.pumpAndSettle();
    expect(find.text(product.ingredients!), findsOneWidget);
  });
  test('Old product responses keep optional lists empty', () {
    final product = ProductDetail.fromJson({'id': 1});
    expect(product.attributeValues, isEmpty);
    expect(product.ingredientsList, isEmpty);
  });

  test('Product and SKU attributes, ingredient metadata retain values', () {
    final product = ProductDetail.fromJson({
      'id': 1,
      'attributeValues': [
        {'id': 1, 'attributeDefinitionName': 'SPF', 'value': '50', 'dataType': 'NUMBER'},
        {'id': 2, 'attributeDefinitionName': 'Chống nước', 'value': 'false', 'dataType': 'BOOLEAN', 'productVariantId': 9},
      ],
      'ingredientsList': [
        {'name': 'Niacinamide', 'inciName': 'Niacinamide', 'concentration': 10.0, 'concentrationUnit': '%',
          'isKeyActive': true, 'function': ['humectant'], 'benefits': ['Dưỡng ẩm'], 'potentialConcerns': ['Lưu ý']},
        {'name': 'Aqua'},
      ],
    });
    expect(product.attributeValues.first.variantId, isNull);
    expect(product.attributeValues.last.variantId, 9);
    expect(product.attributeValues.last.displayValue, 'Không');
    expect(product.ingredientsList.first.concentrationLabel, '10%');
    expect(product.ingredientsList.first.isKeyActive, isTrue);
    expect(product.ingredientsList.first.concerns, ['Lưu ý']);
    expect(product.ingredientsList.last.concentrationLabel, '');
    expect(product.ingredientsList.last.benefits, isEmpty);
  });
}
