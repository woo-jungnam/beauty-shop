import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/theme/app_theme.dart';
import 'core/storage/storage_service.dart';
import 'providers/auth_provider.dart';
import 'providers/product_provider.dart';
import 'providers/cart_provider.dart';
import 'providers/spa_provider.dart';
import 'providers/treatment_provider.dart';
import 'providers/order_provider.dart';
import 'providers/chatbot_provider.dart';
import 'providers/theme_provider.dart';
import 'presentation/screens/main_wrapper_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const _MobileStartup());
}

class _MobileStartup extends StatefulWidget {
  const _MobileStartup();

  @override
  State<_MobileStartup> createState() => _MobileStartupState();
}

class _MobileStartupState extends State<_MobileStartup> {
  static const _background = Color(0xFFF8D2E0);
  static const _artwork = AssetImage('assets/pageloadingbeautystore.png');
  Future<void>? _startup;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _startup ??= _initialize();
  }

  Future<void> _initialize() async {
    await Future.wait<void>([
      StorageService.init(),
      precacheImage(_artwork, context),
    ]);
    await WidgetsBinding.instance.endOfFrame;
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<void>(
      future: _startup,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.done &&
            !snapshot.hasError) {
          return const _CustomerApp();
        }

        final failed =
            snapshot.connectionState == ConnectionState.done &&
            snapshot.hasError;
        return MaterialApp(
          debugShowCheckedModeBanner: false,
          home: Scaffold(
            backgroundColor: _background,
            body: failed
                ? Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text('Không thể khởi động BeautyStore.'),
                        FilledButton(
                          onPressed: () {
                            setState(() => _startup = _initialize());
                          },
                          child: const Text('Thử lại'),
                        ),
                      ],
                    ),
                  )
                : const SizedBox.expand(
                    child: Image(
                      image: _artwork,
                      fit: BoxFit.cover,
                      semanticLabel: 'Đang mở BeautyStore',
                    ),
                  ),
          ),
        );
      },
    );
  }
}

class _CustomerApp extends StatelessWidget {
  const _CustomerApp();

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ThemeProvider()),
        ChangeNotifierProvider(create: (_) => AuthProvider()),
        ChangeNotifierProvider(create: (_) => ProductProvider()),
        ChangeNotifierProvider(create: (_) => CartProvider()),
        ChangeNotifierProxyProvider<AuthProvider, SpaProvider>(
          create: (_) => SpaProvider(),
          update: (_, auth, provider) =>
              provider!
                ..bindUser(auth.isAuthenticated ? auth.currentUser?.id : null),
        ),
        ChangeNotifierProxyProvider<AuthProvider, TreatmentProvider>(
          create: (_) => TreatmentProvider(),
          update: (_, auth, provider) => provider!
            ..bindUser(auth.currentUser, authenticated: auth.isAuthenticated),
        ),
        ChangeNotifierProvider(create: (_) => OrderProvider()),
        ChangeNotifierProvider(create: (_) => ChatbotProvider()),
      ],
      child: const MobileApp(),
    );
  }
}

class MobileApp extends StatelessWidget {
  const MobileApp({super.key});

  @override
  Widget build(BuildContext context) {
    final themeProvider = context.watch<ThemeProvider>();

    return MaterialApp(
      title: 'Mỹ Phẩm & Spa Làm Đẹp',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: themeProvider.themeMode,
      home: const MainWrapperScreen(),
    );
  }
}
