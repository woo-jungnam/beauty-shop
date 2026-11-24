import 'dart:convert';
import '../../../core/utils/idempotency_key.dart';
import 'package:dio/dio.dart' as dio_pkg;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import 'package:printing/printing.dart';
import 'order_invoice.dart';
import '../../../core/network/api_client.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/utils/image_utils.dart';
import '../../../data/models/order_model.dart';
import '../../../core/utils/cart_actions.dart';
import '../../../providers/order_provider.dart';
import '../checkout/vietqr_payment_screen.dart';
import '../spa/spa_tickets_screen.dart';
import '../treatments/treatment_hub_screen.dart';

class OrderDetailScreen extends StatefulWidget {
  const OrderDetailScreen({super.key, required this.orderId, this.initialRaw});
  final int orderId;
  final Map<String, dynamic>? initialRaw;
  @override
  State<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends State<OrderDetailScreen> {
  static const _vietnamBanks = [
    {'bin': '970422', 'name': 'MBBank'},
    {'bin': '970436', 'name': 'Vietcombank'},
    {'bin': '970415', 'name': 'VietinBank'},
    {'bin': '970418', 'name': 'BIDV'},
    {'bin': '970407', 'name': 'Techcombank'},
    {'bin': '970416', 'name': 'ACB'},
    {'bin': '970423', 'name': 'TPBank'},
    {'bin': '970432', 'name': 'VPBank'},
    {'bin': '970405', 'name': 'Agribank'},
    {'bin': '970403', 'name': 'Sacombank'},
    {'bin': '970441', 'name': 'VIB'},
    {'bin': '970448', 'name': 'OCB'},
  ];

  final api = ApiClient();
  final _picker = ImagePicker();
  Map<String, dynamic>? order;
  List<dynamic> returns = [];
  Map<int, int> quantities = {};
  final reason = TextEditingController();
  final accountNumber = TextEditingController();
  final accountName = TextEditingController();
  final List<String> evidenceUrls = [];
  String refundMethod = 'BANK_TRANSFER';
  String selectedBankBin = '970422';
  bool uploadingEvidence = false;
  String? error;
  bool returnsLoaded = false;
  String? returnFingerprint, returnKey;
  bool busy = false;
  String money(dynamic value) =>
      AppFormatters.formatCurrency((value as num?)?.toDouble() ?? 0);
  @override
  void initState() {
    super.initState();
    order = widget.initialRaw;
    load();
  }

  @override
  void dispose() {
    reason.dispose();
    accountNumber.dispose();
    accountName.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() {
      busy = true;
      error = null;
      returnsLoaded = false;
    });
    try {
      final response = await api.get<dynamic>(
        '/api/v1/orders/${widget.orderId}',
      );
      if (!mounted) return;
      setState(() => order = Map<String, dynamic>.from(response.data as Map));
      final returnResponse = await api.get<dynamic>(
        '/api/v1/orders/${widget.orderId}/returns',
      );
      if (!returnResponse.isSuccess || returnResponse.data is! List) {
        throw StateError('Missing returns data');
      }
      if (mounted) {
        setState(() {
          returns = returnResponse.data as List;
          returnsLoaded = true;
          quantities.clear();
        });
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => error =
              'Không thể cập nhật chi tiết hoặc yêu cầu đổi trả. Hãy bấm làm mới.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> run(Future<void> Function() action) async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await action();
      if (mounted) await load();
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  int returnable(dynamic item) {
    var remaining = (item['quantity'] as num).toInt();
    for (final row in returns.where((row) => row['status'] != 'REJECTED')) {
      for (final line in row['lines'] as List? ?? []) {
        if (line['orderItemId'] == item['id'])
          remaining -= (line['quantity'] as num).toInt();
      }
    }
    return remaining.clamp(0, 999999);
  }

  Future<void> _pickAndUploadEvidence(ImageSource source) async {
    if (evidenceUrls.length >= 8) {
      setState(() => error = 'Tối đa 8 ảnh bằng chứng cho mỗi yêu cầu.');
      return;
    }
    try {
      final file = await _picker.pickImage(
        source: source,
        maxWidth: 1600,
        maxHeight: 1600,
        imageQuality: 85,
      );
      if (file == null) return;
      final bytes = await file.readAsBytes();
      if (bytes.length > 10 * 1024 * 1024) {
        if (mounted) {
          setState(() => error = 'Ảnh vượt quá giới hạn 10MB.');
        }
        return;
      }
      setState(() {
        uploadingEvidence = true;
        error = null;
      });
      final formData = dio_pkg.FormData.fromMap({
        'file': dio_pkg.MultipartFile.fromBytes(
          bytes,
          filename: file.name.isNotEmpty ? file.name : 'evidence.jpg',
        ),
      });
      final response = await api.post<dynamic>(
        '/api/v1/orders/${widget.orderId}/returns/evidence',
        data: formData,
      );
      final data = response.data;
      final uploadedUrl = data is Map ? (data['url'] ?? data['publicUrl'])?.toString() : null;
      if (mounted && uploadedUrl != null && uploadedUrl.isNotEmpty) {
        setState(() => evidenceUrls.add(uploadedUrl));
      }
    } catch (e) {
      if (mounted) {
        setState(() => error = 'Không thể tải ảnh lên: $e');
      }
    } finally {
      if (mounted) setState(() => uploadingEvidence = false);
    }
  }

  Future<void> sendReturn() async {
    final lines = quantities.entries
        .where((e) => e.value > 0)
        .map((e) => {'orderItemId': e.key, 'quantity': e.value})
        .toList();
    if (lines.isEmpty || reason.text.trim().length < 5) {
      setState(() => error = 'Chọn sản phẩm và nhập lý do ít nhất 5 ký tự.');
      return;
    }
    if (refundMethod == 'BANK_TRANSFER' &&
        (accountNumber.text.trim().isEmpty || accountName.text.trim().isEmpty)) {
      setState(
        () => error = 'Vui lòng nhập số tài khoản và tên chủ tài khoản nhận hoàn tiền.',
      );
      return;
    }
    final bank = _vietnamBanks.firstWhere(
      (b) => b['bin'] == selectedBankBin,
      orElse: () => _vietnamBanks.first,
    );
    final payload = {
      'reason': reason.text.trim(),
      'lines': lines,
      'evidenceUrls': List<String>.from(evidenceUrls),
      'refundMethod': refundMethod,
      'bankBin': refundMethod == 'BANK_TRANSFER' ? bank['bin'] : null,
      'bankName': refundMethod == 'BANK_TRANSFER' ? bank['name'] : null,
      'accountNumber': refundMethod == 'BANK_TRANSFER' ? accountNumber.text.trim() : null,
      'accountName': refundMethod == 'BANK_TRANSFER' ? accountName.text.trim() : null,
    };
    final fingerprint = jsonEncode(payload);
    if (fingerprint != returnFingerprint) {
      returnFingerprint = fingerprint;
      returnKey = generateIdempotencyKey('ret');
    }
    await api.post<dynamic>(
      '/api/v1/orders/${widget.orderId}/returns',
      data: payload,
      headers: {'Idempotency-Key': returnKey},
    );
    returnFingerprint = null;
    returnKey = null;
    quantities = {};
    reason.clear();
    evidenceUrls.clear();
    accountNumber.clear();
    accountName.clear();
  }

  Future<void> _printInvoice(OrderResponse order) async {
    try {
      await Printing.layoutPdf(
        name: 'BeautyShop-${order.orderCode}',
        onLayout: (format) => buildOrderInvoice(order, format),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không thể mở hộp thoại in hóa đơn.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final value = order;
    final summary = value == null ? null : OrderResponse.fromJson(value);
    final canPay = summary?.canPayByQr ?? false;
    final deadline = summary?.paymentExpiresAt;
    return Scaffold(
      appBar: AppBar(
        title: Text('${value?['orderNumber'] ?? 'Chi tiết đơn hàng'}'),
        actions: [
          if (summary != null)
            IconButton(
              tooltip: 'In hóa đơn',
              icon: const Icon(Icons.print_outlined),
              onPressed: busy ? null : () => _printInvoice(summary),
            ),
          IconButton(
            onPressed: busy ? null : load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (busy) const LinearProgressIndicator(),
          if (error != null)
            Text(error!, style: const TextStyle(color: Colors.red)),
          if (value != null) ...[
            Text(
              'Trạng thái: ${summary!.statusLabel} · ${summary.paymentStatusLabel}',
            ),
            const SizedBox(height: 12),
            Text(
              '${value['customerName'] ?? ''} · ${value['customerPhone'] ?? ''}\n${value['shippingAddress'] ?? ''}',
            ),
            if (value['trackingCode'] != null)
              Text(
                'Vận chuyển: ${value['carrierName'] ?? ''} · ${value['trackingCode']}',
              ),
            ...(value['items'] as List? ?? []).map((item) {
              final img = ImageUtils.resolveUrl(
                item['imageUrl']?.toString() ??
                    item['thumbnailUrl']?.toString(),
              );
              final price =
                  ((item['price'] as num?) ?? 0) -
                  ((item['discount'] as num?) ?? 0);
              final qty = (item['quantity'] as num?)?.toInt() ?? 1;
              return ListTile(
                contentPadding: EdgeInsets.zero,
                leading: ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: Container(
                    width: 48,
                    height: 48,
                    color: const Color(0xFFF3F4F6),
                    child: img != null && img.isNotEmpty
                        ? Image.network(
                            img,
                            fit: BoxFit.cover,
                            errorBuilder: (_, __, ___) => const Icon(
                              Icons.image_outlined,
                              size: 20,
                              color: Color(0xFF9CA3AF),
                            ),
                          )
                        : const Icon(
                            Icons.image_outlined,
                            size: 20,
                            color: Color(0xFF9CA3AF),
                          ),
                  ),
                ),
                title: Text('${item['productName']}'),
                subtitle: Text(
                  '${item['variantName'] ?? item['sku'] ?? 'Tiêu chuẩn'} · SL: $qty',
                ),
                trailing: Text(
                  money(price * qty),
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              );
            }),
            ...(value['spaVisitItems'] as List? ?? []).map(
              (item) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: Container(
                    width: 48,
                    height: 48,
                    color: const Color(0xFFFDF2F8),
                    child: const Icon(
                      Icons.spa,
                      color: Color(0xFF9D174D),
                      size: 22,
                    ),
                  ),
                ),
                title: Text('${item['serviceName'] ?? 'Dịch vụ Spa'}'),
                subtitle: const Text('Dịch vụ Spa tại chỗ'),
                trailing: Text(
                  money(
                    (item['amount'] ??
                            item['price'] ??
                            item['unitPrice'] as num?) ??
                        0,
                  ),
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
            ),
            Text(
              'Tổng đơn: ${money(value['totalAmount'])}\nĐã thu: ${money(value['paidAmount'])}\nĐã hoàn: ${money(value['refundedAmount'])}',
            ),
            if (summary.refundReference?.trim().isNotEmpty == true)
              Text('Mã chứng từ hoàn tiền: ${summary.refundReference}'),
            if (summary.cancelReason?.trim().isNotEmpty == true)
              Text('Lý do hủy đơn: ${summary.cancelReason}'),
            if (deadline != null)
              Text('Hạn thanh toán: ${AppFormatters.formatDate(deadline)}'),
            if (canPay)
              FilledButton(
                onPressed: busy
                    ? null
                    : () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => VietQrPaymentScreen(
                            order: OrderResponse.fromJson(value),
                          ),
                        ),
                      ),
                child: const Text('Tiếp tục thanh toán'),
              ),
            if (value['servicePackageId'] != null)
              OutlinedButton(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) =>
                        value['spaBenefitType'] == 'TREATMENT_COURSE'
                        ? const TreatmentHubScreen()
                        : const SpaTicketsScreen(),
                  ),
                ),
                child: Text(
                  value['spaBenefitType'] == 'TREATMENT_COURSE'
                      ? 'Mở lộ trình điều trị'
                      : 'Mở ví vé Spa',
                ),
              ),
            if ((value['items'] as List? ?? []).isNotEmpty)
              OutlinedButton(
                onPressed: busy
                    ? null
                    : () => run(() => reorderProducts(context, summary.items)),
                child: const Text('Mua lại theo giá hiện tại'),
              ),
            if (value['status'] == 'PENDING')
              TextButton(
                onPressed: busy
                    ? null
                    : () async {
                        final confirmed = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('Hủy đơn hàng?'),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, false),
                                child: const Text('Giữ đơn'),
                              ),
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, true),
                                child: const Text('Hủy đơn'),
                              ),
                            ],
                          ),
                        );
                        if (confirmed == true) {
                          await run(() async {
                            await api.delete<dynamic>(
                              '/api/v1/orders/${widget.orderId}/cancel',
                            );
                          });
                          if (mounted) {
                            try {
                              await context
                                  .read<OrderProvider>()
                                  .refreshOrders();
                            } catch (_) {}
                          }
                        }
                      },
                child: const Text('Hủy đơn'),
              ),
            ExpansionTile(
              title: const Text('Lịch sử trạng thái'),
              children: (value['statusHistories'] as List? ?? [])
                  .map<Widget>(
                    (row) => ListTile(
                      title: Text('${row['status'] ?? row['toStatus'] ?? ''}'),
                      subtitle: Text(
                        '${row['notes'] ?? row['reason'] ?? ''}\n${row['createdAt'] ?? ''}',
                      ),
                    ),
                  )
                  .toList(),
            ),
            const Divider(),
            const Text(
              'Yêu cầu đổi trả',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            ...returns.map(
              (row) => Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '#${row['id']} · ${row['status']} · Hoàn ${money(row['refundAmount'])}',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 4),
                      Text('Lý do: ${row['reason']}'),
                      Text(
                        'Hình thức: ${row['refundMethod'] == 'VOUCHER' ? 'Voucher hoàn tiền' : 'Chuyển khoản ngân hàng'}'
                        '${row['accountNumber'] != null ? ' (${row['bankName'] ?? ''} · ${row['accountNumber']})' : ''}',
                      ),
                      Text('Phản hồi: ${row['decisionReason'] ?? 'Đang xử lý'}'),
                      if (row['refundVoucherCode'] != null &&
                          row['refundVoucherCode'].toString().isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xFFECFDF5),
                            borderRadius: BorderRadius.circular(6),
                            border: Border.all(color: const Color(0xFFA7F3D0)),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Expanded(
                                child: Text(
                                  'Mã Voucher: ${row['refundVoucherCode']}',
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: Color(0xFF065F46),
                                  ),
                                ),
                              ),
                              TextButton(
                                onPressed: () {
                                  Clipboard.setData(
                                    ClipboardData(
                                      text: row['refundVoucherCode'].toString(),
                                    ),
                                  );
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(
                                      content: Text('Đã sao chép mã Voucher'),
                                    ),
                                  );
                                },
                                child: const Text('Sao chép'),
                              ),
                            ],
                          ),
                        ),
                      ],
                      if ((row['evidenceUrls'] as List? ?? []).isNotEmpty) ...[
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: (row['evidenceUrls'] as List)
                              .map((url) => ImageUtils.resolveUrl(url?.toString()))
                              .whereType<String>()
                              .map(
                                (resolved) => ClipRRect(
                                  borderRadius: BorderRadius.circular(6),
                                  child: Image.network(
                                    resolved,
                                    width: 56,
                                    height: 56,
                                    fit: BoxFit.cover,
                                    errorBuilder: (_, __, ___) => const Icon(
                                      Icons.broken_image_outlined,
                                    ),
                                  ),
                                ),
                              )
                              .toList(),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
            if (value['status'] == 'DELIVERED' && returnsLoaded) ...[
              const SizedBox(height: 8),
              const Text('Chọn số lượng cần đổi trả'),
              ...(value['items'] as List? ?? []).map((item) {
                final max = returnable(item);
                final id = (item['id'] as num).toInt();
                return ListTile(
                  title: Text('${item['productName']}'),
                  subtitle: Text('Có thể yêu cầu: $max'),
                  trailing: DropdownButton<int>(
                    value: quantities[id] ?? 0,
                    onChanged: busy
                        ? null
                        : (v) => setState(() => quantities[id] = v ?? 0),
                    items: List.generate(
                      max + 1,
                      (i) => DropdownMenuItem(value: i, child: Text('$i')),
                    ),
                  ),
                );
              }),
              TextField(
                controller: reason,
                maxLength: 1000,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'Lý do đổi trả'),
              ),
              const SizedBox(height: 8),
              const Text(
                'Ảnh bằng chứng (tối đa 8 ảnh)',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              Row(
                children: [
                  OutlinedButton.icon(
                    onPressed: busy || uploadingEvidence
                        ? null
                        : () => _pickAndUploadEvidence(ImageSource.gallery),
                    icon: const Icon(Icons.photo_library_outlined, size: 18),
                    label: const Text('Chọn ảnh'),
                  ),
                  const SizedBox(width: 8),
                  OutlinedButton.icon(
                    onPressed: busy || uploadingEvidence
                        ? null
                        : () => _pickAndUploadEvidence(ImageSource.camera),
                    icon: const Icon(Icons.camera_alt_outlined, size: 18),
                    label: const Text('Chụp ảnh'),
                  ),
                ],
              ),
              if (uploadingEvidence)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 6),
                  child: LinearProgressIndicator(),
                ),
              if (evidenceUrls.isNotEmpty) ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: evidenceUrls.asMap().entries.map((entry) {
                    final idx = entry.key;
                    final resolved = ImageUtils.resolveUrl(entry.value) ?? '';
                    return Stack(
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: Image.network(
                            resolved,
                            width: 64,
                            height: 64,
                            fit: BoxFit.cover,
                            errorBuilder: (_, __, ___) => const SizedBox(
                              width: 64,
                              height: 64,
                              child: Icon(Icons.image_outlined),
                            ),
                          ),
                        ),
                        Positioned(
                          right: 0,
                          top: 0,
                          child: InkWell(
                            onTap: () =>
                                setState(() => evidenceUrls.removeAt(idx)),
                            child: Container(
                              padding: const EdgeInsets.all(2),
                              decoration: const BoxDecoration(
                                color: Colors.red,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(
                                Icons.close,
                                size: 14,
                                color: Colors.white,
                              ),
                            ),
                          ),
                        ),
                      ],
                    );
                  }).toList(),
                ),
              ],
              const SizedBox(height: 12),
              const Text(
                'Hình thức nhận hoàn tiền',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
              RadioListTile<String>(
                contentPadding: EdgeInsets.zero,
                value: 'BANK_TRANSFER',
                groupValue: refundMethod,
                title: const Text('Chuyển khoản ngân hàng'),
                onChanged: busy
                    ? null
                    : (v) => setState(() => refundMethod = v ?? 'BANK_TRANSFER'),
              ),
              if (value['userId'] != null)
                RadioListTile<String>(
                  contentPadding: EdgeInsets.zero,
                  value: 'VOUCHER',
                  groupValue: refundMethod,
                  title: const Text('Nhận Voucher hoàn tiền ngay (dùng 180 ngày)'),
                  onChanged: busy
                      ? null
                      : (v) => setState(() => refundMethod = v ?? 'VOUCHER'),
                ),
              if (refundMethod == 'BANK_TRANSFER') ...[
                DropdownButtonFormField<String>(
                  value: selectedBankBin,
                  decoration: const InputDecoration(labelText: 'Ngân hàng nhận hoàn'),
                  items: _vietnamBanks
                      .map(
                        (b) => DropdownMenuItem(
                          value: b['bin'],
                          child: Text(b['name']!),
                        ),
                      )
                      .toList(),
                  onChanged: busy
                      ? null
                      : (v) => setState(() => selectedBankBin = v ?? '970422'),
                ),
                TextField(
                  controller: accountNumber,
                  decoration: const InputDecoration(labelText: 'Số tài khoản'),
                ),
                TextField(
                  controller: accountName,
                  decoration: const InputDecoration(labelText: 'Tên chủ tài khoản'),
                ),
              ],
              const SizedBox(height: 12),
              FilledButton(
                onPressed: busy || uploadingEvidence ? null : () => run(sendReturn),
                child: const Text('Gửi yêu cầu đổi trả'),
              ),
            ],
          ],
        ],
      ),
    );
  }
}
