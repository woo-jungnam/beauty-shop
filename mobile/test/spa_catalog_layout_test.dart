import 'package:beautyshop_mobile/data/models/spa_model.dart';
import 'package:beautyshop_mobile/presentation/screens/spa/spa_screen.dart';
import 'package:beautyshop_mobile/providers/spa_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

class _SpaCatalog extends SpaProvider {
  @override
  Future<void> fetchServices() async {}

  @override
  List<SpaService> get filteredServices => [
    SpaService(
      id: 1,
      name: 'Chăm sóc da chuyên sâu',
      slug: 'cham-soc-da',
      description: 'Liệu trình chăm sóc da và làm sạch chuyên sâu.',
      price: 1200000,
      originalPrice: 1500000,
    ),
  ];
}

void main() {
  testWidgets('Spa intro and cards scroll above bottom navigation', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 500));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    const navBarKey = Key('bottom-nav');
    await tester.pumpWidget(
      ChangeNotifierProvider<SpaProvider>(
        create: (_) => _SpaCatalog(),
        child: MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(1.4)),
            child: child!,
          ),
          home: const Scaffold(
            body: SpaScreen(),
            bottomNavigationBar: SizedBox(key: navBarKey, height: 56),
          ),
        ),
      ),
    );

    final intro = find.text('Gói dịch vụ nhiều lượt');
    final booking = find.text('Đặt Lịch Hẹn');
    expect(intro.hitTestable(), findsOneWidget);
    await tester.scrollUntilVisible(
      booking,
      130,
      scrollable: find.descendant(
        of: find.byType(CustomScrollView),
        matching: find.byWidgetPredicate(
          (widget) =>
              widget is Scrollable &&
              widget.axisDirection == AxisDirection.down,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(intro.hitTestable(), findsNothing);
    expect(booking.hitTestable(), findsOneWidget);
    expect(
      tester.getBottomRight(find.byType(ElevatedButton)).dy,
      lessThan(tester.getTopLeft(find.byKey(navBarKey)).dy),
    );
    expect(tester.takeException(), isNull);
  });
}
