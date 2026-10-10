import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/core/storage/storage_service.dart';
import 'package:beautyshop_mobile/providers/cart_provider.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/presentation/screens/checkout/vietqr_payment_screen.dart';

class CheckoutScreen extends StatefulWidget {
  const CheckoutScreen({Key? key}) : super(key: key);

  @override
  State<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends State<CheckoutScreen> {
  String _paymentMethod = 'VIETQR'; // 'VIETQR' (BANK) | 'COD'
  late final TextEditingController _nameController;
  late final TextEditingController _phoneController;
  late final TextEditingController _addressController;
  late final TextEditingController _wardController;
  late final TextEditingController _districtController;
  late final TextEditingController _cityController;
  late final TextEditingController _noteController;

  @override
  void initState() {
    super.initState();
    final auth = context.read<AuthProvider>();
    final user = auth.currentUser;
    _nameController = TextEditingController(text: user?.fullName ?? '');
    _phoneController = TextEditingController(text: user?.phone ?? '');
    _addressController = TextEditingController(text: StorageService.getDeliveryAddress());
    _wardController = TextEditingController(text: 'Phường Bến Nghé');
    _districtController = TextEditingController(text: 'Quận 1');
    _cityController = TextEditingController(text: 'TP. Hồ Chí Minh');
    _noteController = TextEditingController();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    _addressController.dispose();
    _wardController.dispose();
    _districtController.dispose();
    _cityController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cart = context.watch<CartProvider>();

    return Scaffold(
      backgroundColor: HasakiColors.canvas,
      appBar: AppBar(
        title: const Text('Xác Nhận Đơn Hàng'),
        backgroundColor: HasakiColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 1. Delivery Information Form
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: HasakiColors.borderSubtle),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.location_on, color: HasakiColors.primary, size: 20),
                      SizedBox(width: 8),
                      Text('Thông tin giao hàng (Giao 2H)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800)),
                    ],
                  ),
                  const Divider(color: HasakiColors.borderSubtle, height: 16),
                  TextField(
                    controller: _nameController,
                    decoration: const InputDecoration(
                      labelText: 'Họ và tên người nhận *',
                      isDense: true,
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _phoneController,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(
                      labelText: 'Số điện thoại liên hệ *',
                      isDense: true,
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _addressController,
                    decoration: const InputDecoration(
                      labelText: 'Địa chỉ chi tiết (số nhà, tên đường) *',
                      isDense: true,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _wardController,
                          decoration: const InputDecoration(
                            labelText: 'Phường/Xã',
                            isDense: true,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: TextField(
                          controller: _districtController,
                          decoration: const InputDecoration(
                            labelText: 'Quận/Huyện',
                            isDense: true,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _cityController,
                    decoration: const InputDecoration(
                      labelText: 'Tỉnh/Thành phố',
                      isDense: true,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // 2. Order Items Review List
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: HasakiColors.borderSubtle),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Sản phẩm đã chọn (${cart.cart.items.where((i) => i.isSelected).length}):', style: HasakiTypography.titleMedium),
                  const Divider(color: HasakiColors.borderSubtle, height: 16),
                  ...cart.cart.items.where((i) => i.isSelected).map((item) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              '${item.productName} (x${item.quantity})',
                              style: HasakiTypography.bodySmall,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          Text(
                            HasakiFormatters.formatCurrency(item.totalPrice),
                            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                          ),
                        ],
                      ),
                    );
                  }).toList(),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // 3. Payment Method Selector
            Material(
              color: Colors.white,
              borderRadius: BorderRadius.circular(8),
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: HasakiColors.borderSubtle),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Phương thức thanh toán:', style: HasakiTypography.titleMedium),
                    const SizedBox(height: 6),
                    RadioListTile<String>(
                    contentPadding: EdgeInsets.zero,
                    title: const Row(
                      children: [
                        Icon(Icons.qr_code_scanner, color: HasakiColors.primary, size: 20),
                        SizedBox(width: 8),
                        Text('Quét mã VietQR chuyển khoản (SePay)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                      ],
                    ),
                    subtitle: const Text('Tự động xác nhận giao dịch • Khuyên dùng', style: TextStyle(color: HasakiColors.success, fontSize: 11)),
                    value: 'VIETQR',
                    groupValue: _paymentMethod,
                    activeColor: HasakiColors.primary,
                    onChanged: (val) => setState(() => _paymentMethod = val!),
                  ),
                  RadioListTile<String>(
                    contentPadding: EdgeInsets.zero,
                    title: const Row(
                      children: [
                        Icon(Icons.payments_outlined, color: HasakiColors.textMuted, size: 20),
                        SizedBox(width: 8),
                        Text('Thanh toán khi nhận hàng (COD)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                      ],
                    ),
                    value: 'COD',
                    groupValue: _paymentMethod,
                    activeColor: HasakiColors.primary,
                    onChanged: (val) => setState(() => _paymentMethod = val!),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),

          // 4. Order Note Input
          Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: HasakiColors.borderSubtle),
              ),
              child: TextField(
                controller: _noteController,
                decoration: const InputDecoration(
                  hintText: 'Ghi chú cho tài xế giao hàng (tùy chọn)...',
                  isDense: true,
                ),
              ),
            ),
            const SizedBox(height: 12),

            // 5. Payment Cost Breakdown
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: HasakiColors.borderSubtle),
              ),
              child: Column(
                children: [
                  _buildCostRow('Tạm tính:', HasakiFormatters.formatCurrency(cart.selectedSubtotal)),
                  const SizedBox(height: 6),
                  _buildCostRow('Phí vận chuyển 2H:', HasakiFormatters.formatCurrency(cart.shippingFee)),
                  if (cart.voucherDiscount > 0) ...[
                    const SizedBox(height: 6),
                    _buildCostRow('Giảm giá voucher:', '-${HasakiFormatters.formatCurrency(cart.voucherDiscount)}', isDiscount: true),
                  ],
                  const Divider(color: HasakiColors.borderSubtle, height: 18),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Tổng thanh toán:', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800)),
                      Text(
                        HasakiFormatters.formatCurrency(cart.finalTotal),
                        style: HasakiTypography.priceLarge,
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Place Order Button
            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton(
                onPressed: () async {
                  if (_nameController.text.trim().isEmpty ||
                      _phoneController.text.trim().isEmpty ||
                      _addressController.text.trim().isEmpty) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Vui lòng điền đầy đủ họ tên, số điện thoại và địa chỉ nhận hàng'),
                        backgroundColor: HasakiColors.dealRed,
                      ),
                    );
                    return;
                  }

                  final request = CheckoutRequest(
                    sessionId: !StorageService.isLoggedIn ? StorageService.getGuestSessionId() : null,
                    customerName: _nameController.text.trim(),
                    customerPhone: _phoneController.text.trim(),
                    shippingAddress: _addressController.text.trim(),
                    ward: _wardController.text.trim().isNotEmpty ? _wardController.text.trim() : 'Phường Bến Nghé',
                    district: _districtController.text.trim().isNotEmpty ? _districtController.text.trim() : 'Quận 1',
                    city: _cityController.text.trim().isNotEmpty ? _cityController.text.trim() : 'TP. Hồ Chí Minh',
                    paymentMethod: _paymentMethod == 'VIETQR' ? 'BANK' : 'COD',
                    notes: _noteController.text.trim(),
                    voucherCode: cart.appliedVoucherCode,
                  );

                  final createdOrder = await context.read<OrderProvider>().checkout(request);

                  if (createdOrder != null) {
                    StorageService.setDeliveryAddress(_addressController.text.trim());
                    cart.clearCart();

                    if (_paymentMethod == 'VIETQR') {
                      Navigator.pushReplacement(
                        context,
                        MaterialPageRoute(
                          builder: (_) => VietQrPaymentScreen(order: createdOrder),
                        ),
                      );
                    } else {
                      _showSuccessDialog(context, createdOrder.orderCode);
                    }
                  }
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: HasakiColors.dealRed,
                ),
                child: const Text('ĐẶT HÀNG NGAY', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w900)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCostRow(String label, String value, {bool isDiscount = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: HasakiTypography.bodySmall),
        Text(
          value,
          style: TextStyle(
            fontWeight: FontWeight.w700,
            color: isDiscount ? HasakiColors.success : HasakiColors.textMain,
          ),
        ),
      ],
    );
  }

  void _showSuccessDialog(BuildContext context, String orderCode) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.check_circle, color: HasakiColors.success),
            SizedBox(width: 8),
            Text('Đặt Hàng Thành Công!'),
          ],
        ),
        content: Text('Mã đơn hàng: $orderCode. BeautyShop sẽ đóng gói và giao nhanh trong 2 giờ.'),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pop(context);
            },
            child: const Text('HOÀN TẤT', style: TextStyle(color: HasakiColors.primary, fontWeight: FontWeight.w800)),
          ),
        ],
      ),
    );
  }
}
