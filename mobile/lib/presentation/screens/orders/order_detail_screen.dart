import 'dart:convert';
import '../../../core/utils/idempotency_key.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/storage_service.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/order_model.dart';
import '../../../providers/cart_provider.dart';
import '../checkout/vietqr_payment_screen.dart';
import '../spa/spa_tickets_screen.dart';
import '../treatments/treatment_hub_screen.dart';

class OrderDetailScreen extends StatefulWidget {
  const OrderDetailScreen({super.key, required this.orderId});
  final int orderId;
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
  String? returnFingerprint, returnKey;
  bool busy = false;
  String money(dynamic value) =>
      AppFormatters.formatCurrency((value as num?)?.toDouble() ?? 0);
  @override
  void initState() {
    super.initState();
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
      if (mounted) setState(() => returns = returnResponse.data as List? ?? []);
    } catch (e) {
      if (mounted) setState(() => error = '$e');
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
      await load();
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> reorder() async {
    final failures = <String>[];
    var added = 0;
    for (final item in order?['items'] as List? ?? []) {
      try {
        if (item['variantId'] == null)
          throw StateError('Sản phẩm tạm thời hết hàng');
        await api.post<dynamic>(
          '/api/v1/cart/add',
          data: {
            'variantId': item['variantId'],
            'quantity': item['quantity'],
            'sessionId': StorageService.getGuestSessionId(),
          },
        );
        added++;
      } catch (e) {
        failures.add('${item['productName']}: $e');
      }
    }
    if (!mounted) return;
    await context.read<CartProvider>().fetchCart();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Đã thêm $added mặt hàng theo giá hiện tại vào giỏ.'),
      ),
    );
    if (failures.isNotEmpty) throw StateError(failures.join('\n'));
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

  @override
  Widget build(BuildContext context) {
    final value = order;
    final deadline = DateTime.tryParse('${value?['paymentDeadline']}');
    final canPay =
        value?['paymentMethod'] == 'BANK' &&
        value?['paymentStatus'] == 'PENDING' &&
        value?['status'] != 'CANCELLED' &&
        (deadline == null || deadline.isAfter(DateTime.now()));
    return Scaffold(
      appBar: AppBar(
        title: Text('${value?['orderNumber'] ?? 'Chi tiết đơn hàng'}'),
        actions: [
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
              'Trạng thái: ${OrderResponse.fromJson(value).statusLabel} · Thanh toán: ${value['paymentStatus']}',
            ),
            const SizedBox(height: 12),
            Text(
              '${value['customerName'] ?? ''} · ${value['customerPhone'] ?? ''}\n${value['shippingAddress'] ?? ''}',
            ),
            if (value['trackingCode'] != null)
              Text(
                'Vận chuyển: ${value['carrierName'] ?? ''} · ${value['trackingCode']}',
              ),
            ...(value['items'] as List? ?? []).map(
              (item) {
                final img = item['imageUrl']?.toString() ?? item['thumbnailUrl']?.toString();
                final price = ((item['price'] as num?) ?? 0) - ((item['discount'] as num?) ?? 0);
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
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                );
              },
            ),
            Text(
              'Tổng đơn: ${money(value['totalAmount'])}\nĐã thu: ${money(value['paidAmount'])}\nĐã hoàn: ${money(value['refundedAmount'])}',
            ),
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
                onPressed: busy ? null : () => run(reorder),
                child: const Text('Mua lại theo giá hiện tại'),
              ),
            if (['PENDING', 'CONFIRMED'].contains(value['status']))
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
                        if (confirmed == true)
                          await run(() async {
                            await api.delete<dynamic>(
                              '/api/v1/orders/${widget.orderId}/cancel',
                            );
                          });
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
            if (value['status'] == 'DELIVERED') ...[
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
