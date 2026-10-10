import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

class HasakiTypography {
  static TextStyle get headlineLarge => GoogleFonts.inter(
        fontSize: 22,
        fontWeight: FontWeight.w800,
        color: HasakiColors.textMain,
        letterSpacing: -0.3,
      );

  static TextStyle get headlineMedium => GoogleFonts.inter(
        fontSize: 18,
        fontWeight: FontWeight.w700,
        color: HasakiColors.textMain,
      );

  static TextStyle get titleLarge => GoogleFonts.inter(
        fontSize: 16,
        fontWeight: FontWeight.w700,
        color: HasakiColors.textMain,
      );

  static TextStyle get titleMedium => GoogleFonts.inter(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        color: HasakiColors.textMain,
      );

  static TextStyle get bodyMedium => GoogleFonts.inter(
        fontSize: 13,
        fontWeight: FontWeight.w400,
        color: HasakiColors.textMain,
        height: 1.4,
      );

  static TextStyle get bodySmall => GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w400,
        color: HasakiColors.textMuted,
        height: 1.35,
      );

  static TextStyle get caption => GoogleFonts.inter(
        fontSize: 11,
        fontWeight: FontWeight.w500,
        color: HasakiColors.textLight,
      );

  static TextStyle get priceLarge => GoogleFonts.inter(
        fontSize: 18,
        fontWeight: FontWeight.w800,
        color: HasakiColors.dealRed,
      );

  static TextStyle get priceMedium => GoogleFonts.inter(
        fontSize: 15,
        fontWeight: FontWeight.w700,
        color: HasakiColors.dealRed,
      );

  static TextStyle get priceStrikethrough => GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w400,
        color: HasakiColors.textLight,
        decoration: TextDecoration.lineThrough,
      );
}
