import 'package:intl/intl.dart';

class HasakiFormatters {
  static final NumberFormat _currencyFormatter = NumberFormat.currency(
    locale: 'vi_VN',
    symbol: '₫',
    decimalDigits: 0,
  );

  static String formatCurrency(num? amount) {
    if (amount == null) return '0 ₫';
    return _currencyFormatter.format(amount).trim();
  }

  static String formatDate(DateTime? date) {
    if (date == null) return '';
    return DateFormat('dd/MM/yyyy').format(date);
  }

  static String formatDateTime(DateTime? date) {
    if (date == null) return '';
    return DateFormat('HH:mm - dd/MM/yyyy').format(date);
  }

  static int calculateDiscountPercent(num? basePrice, num? salePrice) {
    if (basePrice == null || salePrice == null || basePrice <= 0) return 0;
    if (salePrice >= basePrice) return 0;
    final percent = ((basePrice - salePrice) / basePrice * 100).round();
    return percent.clamp(1, 99);
  }
}
