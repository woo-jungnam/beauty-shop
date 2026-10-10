import 'package:flutter/material.dart';

/// BeautyShop Pastel Pink Theme & Design Tokens
class HasakiColors {
  // Brand Primary (Hồng Phấn Pastel & Rose Blush)
  static const Color primary = Color(0xFFDB5A77);
  static const Color primaryDark = Color(0xFFB53D58);
  static const Color primaryLight = Color(0xFFFFF0F3);
  static const Color primarySoft = Color(0xFFFBCFE8);

  // Deal & Urgency (Hồng Đào & Rose Red)
  static const Color dealRed = Color(0xFFE11D48);
  static const Color dealRedDark = Color(0xFFBE123C);
  static const Color dealRedLight = Color(0xFFFFF1F2);
  static const Color dealRedBorder = Color(0xFFFECDD3);

  // Accent & Luxury (Rose Gold / Champagne)
  static const Color gold = Color(0xFFD4A373);
  static const Color goldDark = Color(0xFFA87747);
  static const Color goldLight = Color(0xFFFFF7ED);
  static const Color goldBorder = Color(0xFFFED7AA);

  // Background & Surfaces (Nền sáng phớt ánh hồng phấn nhẹ)
  static const Color canvas = Color(0xFFFAF7F8);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surfaceElevated = Color(0xFFFFFFFF);

  // Neutral Text
  static const Color textMain = Color(0xFF1F2937);
  static const Color textMuted = Color(0xFF6B7280);
  static const Color textLight = Color(0xFF9CA3AF);
  static const Color textOnDark = Color(0xFFFFFFFF);
  static const Color textOnDarkMuted = Color(0xFFD1D5DB);

  // Dividers & Borders
  static const Color border = Color(0xFFE5E7EB);
  static const Color borderSubtle = Color(0xFFF3F4F6);

  // Status & Utility
  static const Color star = Color(0xFFEAB308);
  static const Color success = Color(0xFF059669);
  static const Color successLight = Color(0xFFECFDF5);
  static const Color warning = Color(0xFFF59E0B);
  static const Color info = Color(0xFF0284C7);

  // Dark Mode Tokens (Hồng Phấn Dark Theme)
  static const Color darkCanvas = Color(0xFF141214);
  static const Color darkSurface = Color(0xFF1F1B1D);
  static const Color darkSurfaceElevated = Color(0xFF292426);
  static const Color darkBorder = Color(0xFF382F33);
  static const Color darkBorderSubtle = Color(0xFF2A2326);
  static const Color darkTextMain = Color(0xFFF9FAFB);
  static const Color darkTextMuted = Color(0xFF9CA3AF);
  static const Color darkTextLight = Color(0xFF6B7280);
  static const Color darkPrimary = Color(0xFFF472B6);
  static const Color darkPrimaryDark = Color(0xFFE85A88);
  static const Color darkPrimaryLight = Color(0xFF381F28);
  static const Color darkPrimarySoft = Color(0xFF4A2533);

  // Responsive Helpers for Adaptive Containers
  static Color getCanvas(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? darkCanvas : canvas;

  static Color getSurface(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? darkSurface : surface;

  static Color getTextMain(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? darkTextMain : textMain;

  static Color getBorder(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? darkBorder : border;

  static Color getPrimaryLight(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark ? darkPrimaryLight : primaryLight;
}
