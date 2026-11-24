import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_shipper/core/storage/storage_service.dart';
import 'package:beautyshop_shipper/core/theme/app_colors.dart';
import 'package:beautyshop_shipper/presentation/screens/auth/login_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/dashboard/shipper_dashboard_screen.dart';
import 'package:beautyshop_shipper/providers/auth_provider.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const _ShipperStartup());
}

class _ShipperStartup extends StatefulWidget {
  const _ShipperStartup();

  @override
  State<_ShipperStartup> createState() => _ShipperStartupState();
}

class _ShipperStartupState extends State<_ShipperStartup> {
  static const _background = Color(0xFF7C6970);
  static const _artwork = AssetImage('assets/pageloadingship.png');
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
          return const ShipperApp();
        }

        return MaterialApp(
          debugShowCheckedModeBanner: false,
          home: Scaffold(
            backgroundColor: _background,
            body:
                snapshot.connectionState == ConnectionState.done &&
                    snapshot.hasError
                ? Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text(
                          'Không thể khởi động BeautyShip.',
                          style: TextStyle(color: Colors.white),
                        ),
                        FilledButton(
                          onPressed: () {
                            setState(() => _startup = _initialize());
                          },
                          child: const Text('Thử lại'),
                        ),
                      ],
                    ),
                  )
                : SizedBox.expand(
                    child: Image(
                      image: _artwork,
                      fit: BoxFit.cover,
                      semanticLabel: 'Đang mở BeautyShip',
                    ),
                  ),
          ),
        );
      },
    );
  }
}

class ShipperApp extends StatelessWidget {
  const ShipperApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => AuthProvider(),
      child: Consumer<AuthProvider>(
        builder: (context, auth, _) {
          return ChangeNotifierProvider(
            key: ValueKey(auth.isAuthenticated),
            create: (_) => ShipperProvider(),
            child: MaterialApp(
              title: 'BeautyShip',
              debugShowCheckedModeBanner: false,
              theme: ThemeData(
                useMaterial3: true,
                colorScheme: ColorScheme.fromSeed(
                  seedColor: AppColors.primary,
                  primary: AppColors.primary,
                  surface: AppColors.card,
                  onSurface: AppColors.textMain,
                  secondaryContainer: AppColors.primarySurface,
                  onSecondaryContainer: AppColors.primaryDark,
                ),
                textTheme: GoogleFonts.interTextTheme(
                  Theme.of(context).textTheme,
                ),
                scaffoldBackgroundColor: AppColors.background,
                cardTheme: CardThemeData(
                  color: AppColors.card,
                  elevation: 0,
                  margin: const EdgeInsets.only(bottom: 8),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                    side: const BorderSide(color: AppColors.border),
                  ),
                ),
                navigationBarTheme: const NavigationBarThemeData(
                  backgroundColor: AppColors.card,
                  indicatorColor: AppColors.primarySurface,
                  surfaceTintColor: Colors.transparent,
                ),
                snackBarTheme: const SnackBarThemeData(
                  behavior: SnackBarBehavior.floating,
                ),
                appBarTheme: const AppBarTheme(
                  backgroundColor: AppColors.background,
                  foregroundColor: AppColors.textMain,
                  surfaceTintColor: Colors.transparent,
                  elevation: 0,
                ),
              ),
              home: auth.isAuthenticated
                  ? const ShipperDashboardScreen()
                  : const LoginScreen(),
            ),
          );
        },
      ),
    );
  }
}
