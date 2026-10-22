import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

class AppTypography {
  static TextStyle get headlineLarge => GoogleFonts.inter(
    fontSize: 22,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.3,
  );

  static TextStyle get headlineMedium =>
      GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w700);

  static TextStyle get titleLarge =>
      GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.w700);

  static TextStyle get titleMedium =>
      GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w600);

  static TextStyle get bodyMedium =>
      GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w400, height: 1.4);

  static TextStyle get bodySmall => GoogleFonts.inter(
    fontSize: 12,
    fontWeight: FontWeight.w400,
    color: AppColors.textMuted,
    height: 1.35,
  );

  static TextStyle get caption => GoogleFonts.inter(
    fontSize: 11,
    fontWeight: FontWeight.w500,
    color: AppColors.textLight,
  );

  static TextStyle get priceLarge => GoogleFonts.inter(
    fontSize: 18,
    fontWeight: FontWeight.w800,
    color: AppColors.dealRed,
  );

  static TextStyle get priceMedium => GoogleFonts.inter(
    fontSize: 15,
    fontWeight: FontWeight.w700,
    color: AppColors.dealRed,
  );

  static TextStyle get priceStrikethrough => GoogleFonts.inter(
    fontSize: 12,
    fontWeight: FontWeight.w400,
    color: AppColors.textLight,
    decoration: TextDecoration.lineThrough,
  );
}
