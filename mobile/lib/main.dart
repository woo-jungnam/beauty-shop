import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/theme/app_theme.dart';
import 'core/storage/storage_service.dart';
import 'providers/auth_provider.dart';
import 'providers/product_provider.dart';
import 'providers/cart_provider.dart';
import 'providers/spa_provider.dart';
import 'providers/order_provider.dart';
import 'providers/chatbot_provider.dart';
import 'providers/theme_provider.dart';
import 'presentation/screens/main_wrapper_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await StorageService.init();

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ThemeProvider()),
        ChangeNotifierProvider(create: (_) => AuthProvider()),
        ChangeNotifierProvider(create: (_) => ProductProvider()),
        ChangeNotifierProvider(create: (_) => CartProvider()),
        ChangeNotifierProvider(create: (_) => SpaProvider()),
        ChangeNotifierProvider(create: (_) => OrderProvider()),
        ChangeNotifierProvider(create: (_) => ChatbotProvider()),
      ],
      child: const BeautyShopApp(),
    ),
  );
}

class BeautyShopApp extends StatelessWidget {
  const BeautyShopApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final themeProvider = context.watch<ThemeProvider>();

    return MaterialApp(
      title: 'BeautyShop — Mỹ Phẩm & Spa Làm Đẹp',
      debugShowCheckedModeBanner: false,
      theme: HasakiTheme.lightTheme,
      darkTheme: HasakiTheme.darkTheme,
      themeMode: themeProvider.themeMode,
      home: const MainWrapperScreen(),
    );
  }
}
