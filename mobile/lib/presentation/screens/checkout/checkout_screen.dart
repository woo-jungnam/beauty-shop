import '../profile/customer_account_screen.dart';
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
  bool _placing = false;
  String _paymentMethod = 'VIETQR';
  late final TextEditingController _nameController;
  late final TextEditingController _phoneController;
  late final TextEditingController _emailController;
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
    final delivery = StorageService.getDeliveryDetails();
    _nameController = TextEditingController(text: user?.fullName ?? '');
    _phoneController = TextEditingController(text: user?.phone ?? '');
    _emailController = TextEditingController(text: user?.email ?? '');
    _addressController = TextEditingController(text: delivery['address'] ?? '');
    _wardController = TextEditingController(text: delivery['ward'] ?? '');
    _districtController = TextEditingController(
      text: delivery['district'] ?? '',
    );
    _cityController = TextEditingController(text: delivery['city'] ?? '');
    _noteController = TextEditingController();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    _emailController.dispose();
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
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Xác Nhận Đơn Hàng'),
        backgroundColor: AppColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (context.read<AuthProvider>().isAuthenticated)
              OutlinedButton(
                onPressed: () async {
                  final selected = await Navigator.push<Map<String, dynamic>>(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const CustomerAccountScreen(
                        area: 'addresses',
                        selectAddress: true,
                      ),
                    ),
                  );
                  if (selected == null || !mounted) return;
                  _nameController.text = '${selected['recipientName'] ?? ''}';
                  _phoneController.text = '${selected['phone'] ?? ''}';
                  _addressController.text = '${selected['address'] ?? ''}';
                  _wardController.text = '${selected['ward'] ?? ''}';
                  _districtController.text = '${selected['district'] ?? ''}';
                  _cityController.text = '${selected['city'] ?? ''}';
                },
                child: const Text('Chọn từ sổ địa chỉ'),
              ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: TextField(
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(
                  labelText: 'Email nhận thông báo (không bắt buộc)',
                ),
              ),
            ),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.getSurface(context),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(
                        Icons.location_on,
                        color: AppColors.primary,
                        size: 20,
                      ),
                      SizedBox(width: 8),
                      Text(
                        'Thông tin giao hàng',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ],
                  ),
                  const Divider(color: AppColors.borderSubtle, height: 16),
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
                            labelText: 'Phường/Xã *',
                            isDense: true,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: TextField(
                          controller: _districtController,
                          decoration: const InputDecoration(
                            labelText: 'Quận/Huyện *',
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
                      labelText: 'Tỉnh/Thành phố *',
                      isDense: true,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),

            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.getSurface(context),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Sản phẩm đã chọn (${cart.cart.items.where((i) => i.isSelected).length}):',
                    style: AppTypography.titleMedium,
                  ),
                  const Divider(color: AppColors.borderSubtle, height: 16),
                  ...cart.cart.items.where((i) => i.isSelected).map((item) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              '${item.productName} (x${item.quantity})',
                              style: AppTypography.bodySmall,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          Text(
                            AppFormatters.formatCurrency(item.totalPrice),
                            style: const TextStyle(
                              fontWeight: FontWeight.w700,
                              fontSize: 13,
                            ),
                          ),
                        ],
                      ),
                    );
                  }).toList(),
                ],
              ),
            ),
            const SizedBox(height: 12),

            Material(
              color: AppColors.getSurface(context),
              borderRadius: BorderRadius.circular(8),
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.borderSubtle),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Phương thức thanh toán:',
                      style: AppTypography.titleMedium,
                    ),
                    const SizedBox(height: 6),
                    RadioListTile<String>(
                      contentPadding: EdgeInsets.zero,
                      title: const Row(
                        children: [
                          Icon(
                            Icons.qr_code_scanner,
                            color: AppColors.primary,
                            size: 20,
                          ),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Chuyển khoản VietQR',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                      subtitle: const Text(
                        'Tự động xác nhận giao dịch • Khuyên dùng',
                        style: TextStyle(
                          color: AppColors.success,
                          fontSize: 11,
                        ),
                      ),
                      value: 'VIETQR',
                      groupValue: _paymentMethod,
                      activeColor: AppColors.primary,
                      onChanged: (val) => setState(() => _paymentMethod = val!),
                    ),
                    RadioListTile<String>(
                      contentPadding: EdgeInsets.zero,
                      title: const Row(
                        children: [
                          Icon(
                            Icons.payments_outlined,
                            color: AppColors.textMuted,
                            size: 20,
                          ),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Thanh toán khi nhận hàng (COD)',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                      value: 'COD',
                      groupValue: _paymentMethod,
                      activeColor: AppColors.primary,
                      onChanged: (val) => setState(() => _paymentMethod = val!),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),

            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.getSurface(context),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppColors.borderSubtle),
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

            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.getSurface(context),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                children: [
                  _buildCostRow(
                    'Tạm tính:',
                    AppFormatters.formatCurrency(cart.selectedSubtotal),
                  ),
                  const SizedBox(height: 6),
                  _buildCostRow(
                    'Phí vận chuyển:',
                    cart.hasShippingPolicy
                        ? AppFormatters.formatCurrency(cart.shippingFee)
                        : 'Chưa xác định',
                  ),
                  if (cart.voucherDiscount > 0) ...[
                    const SizedBox(height: 6),
                    _buildCostRow(
                      'Giảm giá voucher:',
                      '-${AppFormatters.formatCurrency(cart.voucherDiscount)}',
                      isDiscount: true,
                    ),
                  ],
                  const Divider(color: AppColors.borderSubtle, height: 18),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Expanded(
                        child: Text(
                          'Tổng dự kiến:',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Text(
                          cart.hasShippingPolicy
                              ? AppFormatters.formatCurrency(cart.finalTotal)
                              : 'Chưa xác định',
                          style: AppTypography.priceLarge,
                          textAlign: TextAlign.right,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
            const Text(
              'Số tiền cuối cùng và ưu đãi hội viên được xác nhận khi tạo đơn hàng.',
            ),
            const SizedBox(height: 12),

            SizedBox(
              width: double.infinity,
              height: 50,
              child: ElevatedButton(
                onPressed: _placing || cart.isApplyingVoucher
                    ? null
                    : () async {
                        if (_nameController.text.trim().isEmpty ||
                            _phoneController.text.trim().isEmpty ||
                            _addressController.text.trim().isEmpty ||
                            _wardController.text.trim().isEmpty ||
                            _cityController.text.trim().isEmpty) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                'Vui lòng điền đầy đủ họ tên, số điện thoại, địa chỉ, phường/xã, quận/huyện và tỉnh/thành phố',
                              ),
                              backgroundColor: AppColors.dealRed,
                            ),
                          );
                          return;
                        }

                        if (!RegExp(
                          r'^(0|\+84)[35789][0-9]{8}$',
                        ).hasMatch(_phoneController.text.trim())) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('Số điện thoại không hợp lệ'),
                            ),
                          );
                          return;
                        }
                        if (!cart.allItemsSelected) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text(
                                'Vui lòng chọn tất cả sản phẩm trong giỏ để đặt hàng.',
                              ),
                            ),
                          );
                          return;
                        }
                        setState(() => _placing = true);
                        try {
                          await cart.fetchShippingPolicy();
                          if (!mounted || !context.mounted) return;
                          if (!cart.hasShippingPolicy ||
                              !await cart.revalidateVoucher()) {
                            if (!mounted || !context.mounted) return;
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  cart.shippingError ??
                                      cart.voucherError ??
                                      'Giỏ hàng đã thay đổi. Vui lòng kiểm tra lại.',
                                ),
                              ),
                            );
                            return;
                          }
                          if (!mounted || !context.mounted) return;

                          final request = CheckoutRequest(
                            sessionId: !StorageService.isLoggedIn
                                ? StorageService.getGuestSessionId()
                                : null,
                            customerName: _nameController.text.trim(),
                            customerPhone: _phoneController.text.trim(),
                            customerEmail: _emailController.text.trim().isEmpty
                                ? null
                                : _emailController.text.trim(),
                            shippingAddress: _addressController.text.trim(),
                            ward: _wardController.text.trim(),
                            district: _districtController.text.trim(),
                            city: _cityController.text.trim(),
                            paymentMethod: _paymentMethod == 'VIETQR'
                                ? 'BANK'
                                : 'COD',
                            notes: _noteController.text.trim(),
                            voucherCode: cart.appliedVoucherCode,
                          );

                          final createdOrder = await context
                              .read<OrderProvider>()
                              .checkout(request);
                          if (!mounted || !context.mounted) return;

                          if (createdOrder != null) {
                            await StorageService.setDeliveryDetails({
                              'address': _addressController.text.trim(),
                              'ward': _wardController.text.trim(),
                              'district': _districtController.text.trim(),
                              'city': _cityController.text.trim(),
                            });
                            await cart.fetchCart();
                            if (!mounted || !context.mounted) return;

                            if (_paymentMethod == 'VIETQR') {
                              Navigator.pushReplacement(
                                context,
                                MaterialPageRoute(
                                  builder: (_) =>
                                      VietQrPaymentScreen(order: createdOrder),
                                ),
                              );
                            } else {
                              _showSuccessDialog(context, createdOrder);
                            }
                          } else {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  context.read<OrderProvider>().errorMessage ??
                                      'Không thể tạo đơn hàng',
                                ),
                              ),
                            );
                          }
                        } finally {
                          if (mounted) setState(() => _placing = false);
                        }
                      },
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.dealRed,
                ),
                child: Text(
                  _placing ? 'ĐANG XÁC NHẬN...' : 'ĐẶT HÀNG NGAY',
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w900,
                  ),
                ),
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
        Expanded(child: Text(label, style: AppTypography.bodySmall)),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: TextStyle(
              fontWeight: FontWeight.w700,
              color: isDiscount
                  ? AppColors.success
                  : AppColors.getTextMain(context),
            ),
          ),
        ),
      ],
    );
  }

  void _showSuccessDialog(BuildContext context, OrderResponse order) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.check_circle, color: AppColors.success),
            SizedBox(width: 8),
            Text('Đặt Hàng Thành Công!'),
          ],
        ),
        content: Text(
          'Mã đơn hàng: ${order.orderCode}. Tổng thanh toán: ${AppFormatters.formatCurrency(order.totalAmount)}. Bạn có thể theo dõi trạng thái trong lịch sử đơn hàng.',
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pop(context);
            },
            child: const Text(
              'HOÀN TẤT',
              style: TextStyle(
                color: AppColors.primary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
