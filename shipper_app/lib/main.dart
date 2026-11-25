import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_shipper/core/storage/shipper_settings_store.dart';
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
                  margin: const EdgeInsets.only(bottom: 10),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(
                      ShipperLayout.cardRadius,
                    ),
                    side: const BorderSide(color: AppColors.border),
                  ),
                ),
                inputDecorationTheme: InputDecorationTheme(
                  filled: true,
                  fillColor: AppColors.background,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(
                      ShipperLayout.innerRadius,
                    ),
                    borderSide: const BorderSide(color: AppColors.border),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(
                      ShipperLayout.innerRadius,
                    ),
                    borderSide: const BorderSide(color: AppColors.border),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(
                      ShipperLayout.innerRadius,
                    ),
                    borderSide: const BorderSide(
                      color: AppColors.primary,
                      width: 1.5,
                    ),
                  ),
                ),
                filledButtonTheme: FilledButtonThemeData(
                  style: FilledButton.styleFrom(
                    minimumSize: const Size(0, 46),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 12,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(
                        ShipperLayout.innerRadius,
                      ),
                    ),
                  ),
                ),
                elevatedButtonTheme: ElevatedButtonThemeData(
                  style: ElevatedButton.styleFrom(
                    minimumSize: const Size(0, 46),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 12,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(
                        ShipperLayout.innerRadius,
                      ),
                    ),
                    elevation: 0,
                  ),
                ),
                outlinedButtonTheme: OutlinedButtonThemeData(
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size(0, 44),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 10,
                    ),
                    side: const BorderSide(color: AppColors.border),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(
                        ShipperLayout.innerRadius,
                      ),
                    ),
                  ),
                ),
                dialogTheme: DialogThemeData(
                  backgroundColor: AppColors.card,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(18),
                  ),
                ),
                dividerTheme: const DividerThemeData(
                  color: AppColors.border,
                  thickness: 1,
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
              builder: (context, child) {
                if (child == null) return const SizedBox.shrink();
                return ListenableBuilder(
                  listenable: ShipperSettingsStore.instance,
                  builder: (context, _) {
                    final userScale = ShipperSettingsStore.instance.textScaleFactor;
                    if (userScale == 1.0) return child;
                    final media = MediaQuery.of(context);
                    final baseScale = media.textScaler.scale(10.0) / 10.0;
                    return MediaQuery(
                      data: media.copyWith(
                        textScaler: TextScaler.linear(baseScale * userScale),
                      ),
                      child: child,
                    );
                  },
                );
              },
            ),
          );
        },
      ),
    );
  }
}
