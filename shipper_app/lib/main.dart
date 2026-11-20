import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_shipper/core/storage/storage_service.dart';
import 'package:beautyshop_shipper/core/theme/app_colors.dart';
import 'package:beautyshop_shipper/presentation/screens/auth/login_screen.dart';
import 'package:beautyshop_shipper/presentation/screens/dashboard/shipper_dashboard_screen.dart';
import 'package:beautyshop_shipper/providers/auth_provider.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await StorageService.init();

  runApp(const ShipperApp());
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
              title: 'BeautyShop Shipper',
              debugShowCheckedModeBanner: false,
              theme: ThemeData(
                useMaterial3: true,
                colorScheme: ColorScheme.fromSeed(
                  seedColor: AppColors.primary,
                  primary: AppColors.primary,
                ),
                textTheme: GoogleFonts.interTextTheme(
                  Theme.of(context).textTheme,
                ),
                scaffoldBackgroundColor: AppColors.background,
                appBarTheme: const AppBarTheme(
                  backgroundColor: AppColors.primary,
                  foregroundColor: Colors.white,
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
