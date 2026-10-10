import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app_colors.dart';
import 'app_typography.dart';

class HasakiTheme {
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      primaryColor: HasakiColors.primary,
      scaffoldBackgroundColor: HasakiColors.canvas,
      colorScheme: const ColorScheme.light(
        primary: HasakiColors.primary,
        onPrimary: HasakiColors.textOnDark,
        secondary: HasakiColors.dealRed,
        onSecondary: HasakiColors.textOnDark,
        surface: HasakiColors.surface,
        onSurface: HasakiColors.textMain,
        error: HasakiColors.dealRed,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: HasakiColors.primary,
        foregroundColor: HasakiColors.textOnDark,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: HasakiTypography.titleLarge.copyWith(
          color: HasakiColors.textOnDark,
          fontWeight: FontWeight.w700,
        ),
        systemOverlayStyle: const SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.light,
          statusBarBrightness: Brightness.dark,
        ),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: HasakiColors.surface,
        selectedItemColor: HasakiColors.primary,
        unselectedItemColor: HasakiColors.textLight,
        selectedLabelStyle: TextStyle(fontSize: 11, fontWeight: FontWeight.w700),
        unselectedLabelStyle: TextStyle(fontSize: 11, fontWeight: FontWeight.w500),
        type: BottomNavigationBarType.fixed,
        elevation: 8,
      ),
      cardTheme: CardThemeData(
        color: HasakiColors.surface,
        elevation: 0.5,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: const BorderSide(color: HasakiColors.borderSubtle, width: 0.8),
        ),
        margin: EdgeInsets.zero,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: HasakiColors.primary,
          foregroundColor: HasakiColors.textOnDark,
          elevation: 0,
          textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: HasakiColors.primary,
          side: const BorderSide(color: HasakiColors.primary, width: 1.2),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
          textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: HasakiColors.surface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: HasakiColors.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: HasakiColors.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: HasakiColors.primary, width: 1.5),
        ),
        hintStyle: HasakiTypography.bodySmall,
      ),
    );
  }

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      primaryColor: HasakiColors.darkPrimary,
      scaffoldBackgroundColor: HasakiColors.darkCanvas,
      colorScheme: const ColorScheme.dark(
        primary: HasakiColors.darkPrimary,
        onPrimary: Colors.white,
        secondary: HasakiColors.dealRed,
        onSecondary: Colors.white,
        surface: HasakiColors.darkSurface,
        onSurface: HasakiColors.darkTextMain,
        error: HasakiColors.dealRed,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: HasakiColors.darkSurface,
        foregroundColor: HasakiColors.darkTextMain,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: HasakiTypography.titleLarge.copyWith(
          color: HasakiColors.darkTextMain,
          fontWeight: FontWeight.w700,
        ),
        systemOverlayStyle: const SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.light,
          statusBarBrightness: Brightness.dark,
        ),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: HasakiColors.darkSurface,
        selectedItemColor: HasakiColors.darkPrimary,
        unselectedItemColor: HasakiColors.darkTextLight,
        selectedLabelStyle: TextStyle(fontSize: 11, fontWeight: FontWeight.w700),
        unselectedLabelStyle: TextStyle(fontSize: 11, fontWeight: FontWeight.w500),
        type: BottomNavigationBarType.fixed,
        elevation: 8,
      ),
      cardTheme: CardThemeData(
        color: HasakiColors.darkSurface,
        elevation: 0.5,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: const BorderSide(color: HasakiColors.darkBorder, width: 0.8),
        ),
        margin: EdgeInsets.zero,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: HasakiColors.darkPrimary,
          foregroundColor: Colors.white,
          elevation: 0,
          textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: HasakiColors.darkPrimary,
          side: const BorderSide(color: HasakiColors.darkPrimary, width: 1.2),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
          textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: HasakiColors.darkSurface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: HasakiColors.darkBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: HasakiColors.darkBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: HasakiColors.darkPrimary, width: 1.5),
        ),
        hintStyle: HasakiTypography.bodySmall.copyWith(color: HasakiColors.darkTextMuted),
      ),
    );
  }
}
