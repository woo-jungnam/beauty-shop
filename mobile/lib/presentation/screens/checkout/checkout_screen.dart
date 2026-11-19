import 'dart:convert';
import '../../../core/network/api_client.dart';
import 'checkout_address_fields.dart';
import '../profile/customer_account_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/core/utils/idempotency_key.dart';
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
  bool _saveToAddressBook = true;
  String _paymentMethod = 'VIETQR';
  String? _checkoutFingerprint;
  String? _checkoutIdempotencyKey;
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
    final auth = context.watch<AuthProvider>();

    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Thanh toán đơn hàng'),
        backgroundColor: AppColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
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
                        Icons.person_outline,
                        color: AppColors.primary,
                        size: 20,
                      ),
                      SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '1. Thông tin người nhận',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w800,
                          ),
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
                    controller: _emailController,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(
                      labelText: 'Email nhận thông báo vận đơn',
                      isDense: true,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            if (auth.isAuthenticated)
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
                child: const Text('Chọn hoặc quản lý địa chỉ đã lưu'),
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
                      Expanded(
                        child: Text(
                          '2. Địa chỉ nhận hàng',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const Divider(color: AppColors.borderSubtle, height: 16),
                  CheckoutAddressFields(
                    city: _cityController,
                    ward: _wardController,
                    district: _districtController,
                  ),
                  TextField(
                    controller: _addressController,
                    decoration: const InputDecoration(
                      labelText: 'Địa chỉ chi tiết (số nhà, tên đường) *',
                      isDense: true,
                    ),
                  ),
                  if (auth.isAuthenticated)
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      controlAffinity: ListTileControlAffinity.leading,
                      title: const Text('Lưu địa chỉ vào Sổ địa chỉ'),
                      value: _saveToAddressBook,
                      onChanged: _placing
                          ? null
                          : (value) => setState(
                              () => _saveToAddressBook = value ?? false,
                            ),
                    ),
                  const SizedBox(height: 10),
                  TextField(
                    controller: _noteController,
                    decoration: const InputDecoration(
                      labelText: 'Ghi chú giao hàng',
                      hintText: 'Ví dụ: Giao giờ hành chính, gọi trước khi đến',
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
                      '3. Phương thức thanh toán',
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
                  if (cart.membershipDiscount > 0) ...[
                    const SizedBox(height: 6),
                    _buildCostRow(
                      'Ưu đãi thành viên:',
                      '-${AppFormatters.formatCurrency(cart.membershipDiscount)}',
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
                                'Vui lòng điền đầy đủ họ tên, số điện thoại, địa chỉ, phường/xã và tỉnh/thành phố',
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
                          if (!cart.hasShippingPolicy) {
                            await cart.fetchShippingPolicy();
                          }
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

                          final draftPayload = {
                            'sessionId': !StorageService.isLoggedIn
                                ? StorageService.getGuestSessionId()
                                : null,
                            'customerName': _nameController.text.trim(),
                            'customerPhone': _phoneController.text.trim(),
                            'customerEmail':
                                _emailController.text.trim().isEmpty
                                ? null
                                : _emailController.text.trim(),
                            'shippingAddress': _addressController.text.trim(),
                            'ward': _wardController.text.trim(),
                            'district': _districtController.text.trim(),
                            'city': _cityController.text.trim(),
                            'paymentMethod': _paymentMethod == 'VIETQR'
                                ? 'BANK'
                                : 'COD',
                            'notes': _noteController.text.trim(),
                            'voucherCode': cart.appliedVoucherCode,
                            'items': cart.cart.items
                                .map((i) => {'v': i.variantId, 'q': i.quantity})
                                .toList(),
                          };
                          final fingerprint = jsonEncode(draftPayload);
                          if (fingerprint != _checkoutFingerprint ||
                              _checkoutIdempotencyKey == null) {
                            _checkoutFingerprint = fingerprint;
                            _checkoutIdempotencyKey = generateIdempotencyKey(
                              'ord',
                            );
                          }

                          final request = CheckoutRequest(
                            idempotencyKey: _checkoutIdempotencyKey,
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

                          final saveAddress =
                              auth.isAuthenticated && _saveToAddressBook;
                          final owner = StorageService.authSessionChanged.value;
                          final authClearCount =
                              StorageService.authCleared.value;
                          final createdOrder = await context
                              .read<OrderProvider>()
                              .checkout(request);
                          if (!mounted || !context.mounted) return;

                          if (createdOrder != null) {
                            _checkoutFingerprint = null;
                            _checkoutIdempotencyKey = null;
                            final delivery = {
                              'address': request.shippingAddress,
                              'ward': request.ward,
                              'district': request.district,
                              'city': request.city,
                            };
                            if (owner ==
                                    StorageService.authSessionChanged.value &&
                                authClearCount ==
                                    StorageService.authCleared.value) {
                              await StorageService.setDeliveryDetails(delivery);
                              if (saveAddress && mounted) {
                                await _saveAddress(
                                  {
                                    ...delivery,
                                    'recipientName': request.customerName,
                                    'phone': request.customerPhone,
                                  },
                                  owner,
                                  authClearCount,
                                );
                              }
                            }
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
                            final err =
                                context.read<OrderProvider>().errorMessage ??
                                'Không thể tạo đơn hàng';
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(err),
                                backgroundColor: AppColors.dealRed,
                                duration: const Duration(seconds: 4),
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

  Future<void> _saveAddress(
    Map<String, String> address,
    int authSession,
    int authClearCount,
  ) async {
    const endpoint = '/api/v1/users/addresses';
    try {
      final api = ApiClient();
      final existing = await api.get<dynamic>(endpoint);
      if (!existing.isSuccess || existing.data is! List) {
        throw StateError('Address book unavailable');
      }
      if ((existing.data as List).whereType<Map>().any(
        (row) => address.entries.every(
          (entry) => (row[entry.key]?.toString().trim() ?? '') == entry.value,
        ),
      ))
        return;
      if (authSession != StorageService.authSessionChanged.value ||
          authClearCount != StorageService.authCleared.value)
        return;
      final result = await api.post<dynamic>(
        endpoint,
        data: {...address, 'isDefault': false},
      );
      if (!result.isSuccess) throw StateError('Address could not be saved');
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã đặt hàng, nhưng chưa lưu được địa chỉ vào sổ.'),
          ),
        );
      }
    }
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
