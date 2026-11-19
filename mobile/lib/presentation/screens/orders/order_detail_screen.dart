import 'dart:convert';
import '../../../core/utils/idempotency_key.dart';
import 'package:flutter/material.dart';
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
  final api = ApiClient();
  Map<String, dynamic>? order;
  List<dynamic> returns = [];
  Map<int, int> quantities = {};
  final reason = TextEditingController(), evidence = TextEditingController();
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
    evidence.dispose();
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

  Future<void> sendReturn() async {
    final lines = quantities.entries
        .where((e) => e.value > 0)
        .map((e) => {'orderItemId': e.key, 'quantity': e.value})
        .toList();
    if (lines.isEmpty || reason.text.trim().length < 5) {
      setState(() => error = 'Chọn sản phẩm và nhập lý do ít nhất 5 ký tự.');
      return;
    }
    final urls = evidence.text
        .split('\n')
        .map((url) => url.trim())
        .where((url) => url.isNotEmpty)
        .toList();
    if (urls.length > 8 ||
        urls.any((url) => !RegExp(r'^https?://').hasMatch(url)))
      throw StateError('Dùng tối đa 8 đường dẫn ảnh HTTP hoặc HTTPS.');
    final payload = {
      'reason': reason.text.trim(),
      'lines': lines,
      'evidenceUrls': urls,
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
    evidence.clear();
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
                child: ListTile(
                  title: Text('#${row['id']} · ${row['status']}'),
                  subtitle: Text(
                    '${row['reason']}\nPhản hồi: ${row['decisionReason'] ?? 'Đang xử lý'}\nTiền hoàn: ${money(row['refundAmount'])}',
                  ),
                ),
              ),
            ),
            if (value['status'] == 'DELIVERED' && returnsLoaded) ...[
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
              TextField(
                controller: evidence,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Ảnh/chứng từ: mỗi URL HTTPS một dòng',
                ),
              ),
              FilledButton(
                onPressed: busy ? null : () => run(sendReturn),
                child: const Text('Gửi yêu cầu đổi trả'),
              ),
            ],
          ],
        ],
      ),
    );
  }
}
