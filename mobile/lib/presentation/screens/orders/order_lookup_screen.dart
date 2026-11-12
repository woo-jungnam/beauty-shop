import 'package:flutter/material.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/storage_service.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../data/models/order_model.dart';
import 'order_detail_screen.dart';

class OrderLookupScreen extends StatefulWidget {
  const OrderLookupScreen({super.key});

  @override
  State<OrderLookupScreen> createState() => _OrderLookupScreenState();
}

class _OrderLookupScreenState extends State<OrderLookupScreen> {
  final _orderNumberController = TextEditingController();
  final _phoneController = TextEditingController();
  final _api = ApiClient();
  bool _loading = false;
  String? _error;
  List<int> _recentOrderIds = [];

  @override
  void initState() {
    super.initState();
    _recentOrderIds = StorageService.getGuestOrderIds();
  }

  @override
  void dispose() {
    _orderNumberController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _lookupOrder(String orderNumber, String phone) async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final response = await _api.get<Map<String, dynamic>>(
        '/api/v1/orders/lookup',
        queryParameters: {'orderNumber': orderNumber, 'phone': phone},
        fromJsonT: (json) => Map<String, dynamic>.from(json as Map),
      );

      if (!mounted) return;

      if (response.isSuccess && response.data != null) {
        final raw = response.data!;
        final parsed = OrderResponse.fromJson(raw);
        if (parsed.id > 0) {
          await StorageService.rememberGuestOrder(parsed.id);
          if (mounted) {
            setState(() {
              _recentOrderIds = StorageService.getGuestOrderIds();
            });
          }
        }

        if (mounted) {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => OrderDetailScreen(
                orderId: parsed.id,
                initialRaw: raw,
              ),
            ),
          );
        }
      } else {
        setState(() {
          _error = response.message ?? 'Không tìm thấy thông tin đơn hàng.';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = 'Không tìm thấy đơn hàng khớp với mã đơn và số điện thoại.';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  void _submit() {
    final orderNumber = _orderNumberController.text.trim();
    final phone = _phoneController.text.trim();
    if (orderNumber.isEmpty || phone.isEmpty) {
      setState(
        () => _error = 'Vui lòng nhập đầy đủ mã đơn hàng và số điện thoại.',
      );
      return;
    }
    _lookupOrder(orderNumber, phone);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Tra Cứu Đơn Hàng'),
        backgroundColor: AppColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.getSurface(context),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: AppColors.primaryLight,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Icon(
                          Icons.local_shipping_outlined,
                          color: AppColors.primary,
                          size: 24,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Kiểm tra tình trạng đơn hàng',
                              style: AppTypography.titleMedium,
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Nhập mã đơn hàng và số điện thoại đặt hàng',
                              style: AppTypography.caption,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _orderNumberController,
                    decoration: const InputDecoration(
                      labelText: 'Mã đơn hàng (ví dụ: ORD-1234ABCD)',
                      hintText: 'Nhập mã đơn hàng...',
                      prefixIcon: Icon(Icons.tag),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _phoneController,
                    keyboardType: TextInputType.phone,
                    decoration: InputDecoration(
                      labelText: 'Số điện thoại đặt hàng',
                      hintText: 'Nhập số điện thoại...',
                      prefixIcon: const Icon(Icons.phone_outlined),
                      suffixIcon: IconButton(
                        icon: const Icon(Icons.arrow_forward),
                        onPressed: _loading ? null : _submit,
                      ),
                    ),
                    onSubmitted: (_) => _submit(),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        const Icon(Icons.error_outline, size: 16, color: AppColors.dealRed),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            _error!,
                            style: const TextStyle(color: AppColors.dealRed, fontSize: 12),
                          ),
                        ),
                      ],
                    ),
                  ],
                  const SizedBox(height: 14),
                  SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: ElevatedButton(
                      onPressed: _loading ? null : _submit,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                      ),
                      child: _loading
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text(
                              'TRA CỨU ĐƠN HÀNG',
                              style: TextStyle(fontWeight: FontWeight.w800),
                            ),
                    ),
                  ),
                ],
              ),
            ),
            if (_recentOrderIds.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text(
                'Đơn hàng đã đặt trên thiết bị này',
                style: AppTypography.titleMedium,
              ),
              const SizedBox(height: 8),
              ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _recentOrderIds.length,
                separatorBuilder: (_, __) => const SizedBox(height: 8),
                itemBuilder: (context, index) {
                  final id = _recentOrderIds[index];
                  return ListTile(
                    tileColor: AppColors.getSurface(context),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                      side: const BorderSide(color: AppColors.borderSubtle),
                    ),
                    leading: const Icon(Icons.receipt_long, color: AppColors.primary),
                    title: Text(
                      'Đơn hàng #$id',
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    subtitle: const Text('Bấm để xem tiến trình vận chuyển & hóa đơn'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => OrderDetailScreen(orderId: id),
                      ),
                    ),
                  );
                },
              ),
            ],
          ],
        ),
      ),
    );
  }
}
