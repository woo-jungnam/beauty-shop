import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:beautyshop_shipper/core/storage/storage_service.dart';
import 'package:beautyshop_shipper/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('ShipperApp smoke test', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    await StorageService.init();

    await tester.pumpWidget(const ShipperApp());
    expect(find.text('BeautyShop Shipper'), findsOneWidget);
    expect(find.text('Đăng nhập tài xế'), findsOneWidget);
  });
}
