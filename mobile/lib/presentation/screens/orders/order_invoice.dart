import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../../../core/utils/formatters.dart';
import '../../../data/models/order_model.dart';

Future<Uint8List> buildOrderInvoice(
  OrderResponse order,
  PdfPageFormat format,
) async {
  final font = pw.Font.ttf(
    await rootBundle.load('assets/fonts/NotoSans-Regular.ttf'),
  );
  final document = pw.Document(
    title: 'Hóa đơn đơn hàng ${order.orderCode}',
    author: 'BeautyShop',
    theme: pw.ThemeData.withFont(base: font),
  );
  final money = AppFormatters.formatCurrency;
  document.addPage(
    pw.MultiPage(
      pageFormat: format,
      margin: const pw.EdgeInsets.all(32),
      build: (_) => [
        pw.Text('BEAUTYSHOP', style: const pw.TextStyle(fontSize: 12)),
        pw.Text('Hóa đơn đơn hàng', style: const pw.TextStyle(fontSize: 23)),
        pw.SizedBox(height: 12),
        pw.Text('Mã đơn: ${order.orderCode}'),
        pw.Text('Trạng thái: ${order.statusLabel}'),
        pw.Text('Thanh toán: ${order.paymentStatusLabel}'),
        if (order.customerName?.isNotEmpty == true)
          pw.Text('Người nhận: ${order.customerName}'),
        if (order.customerPhone?.isNotEmpty == true)
          pw.Text('Điện thoại: ${order.customerPhone}'),
        if (order.shippingAddress?.isNotEmpty == true)
          pw.Text('Địa chỉ: ${order.shippingAddress}'),
        pw.SizedBox(height: 16),
        if (order.items.isNotEmpty)
          pw.TableHelper.fromTextArray(
            headers: ['Sản phẩm', 'SL', 'Đơn giá', 'Thành tiền'],
            data: [
              for (final item in order.items)
                [
                  [item.productName, item.variantName, item.sku]
                      .whereType<String>()
                      .where((value) => value.isNotEmpty)
                      .join('\n'),
                  item.quantity.toString(),
                  money(item.effectiveUnitPrice),
                  money(item.effectiveUnitPrice * item.quantity),
                ],
            ],
            headerDecoration: const pw.BoxDecoration(color: PdfColors.grey200),
            cellStyle: const pw.TextStyle(fontSize: 10),
            columnWidths: const {
              0: pw.FlexColumnWidth(4),
              1: pw.FlexColumnWidth(1),
              2: pw.FlexColumnWidth(2),
              3: pw.FlexColumnWidth(2),
            },
          ),
        pw.SizedBox(height: 16),
        pw.Text('Tạm tính: ${money(order.subTotal)}'),
        pw.Text('Phí vận chuyển: ${money(order.shippingFee)}'),
        if (order.discountAmount > 0)
          pw.Text('Giảm giá: -${money(order.discountAmount)}'),
        pw.Text(
          'Tổng đơn hàng: ${money(order.totalAmount)}',
          style: const pw.TextStyle(fontSize: 15),
        ),
      ],
    ),
  );
  return document.save();
}
