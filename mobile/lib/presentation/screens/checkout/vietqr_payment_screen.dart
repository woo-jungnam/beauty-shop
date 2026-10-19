import '../../../core/network/api_client.dart';
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/constants/api_constants.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/data/models/order_model.dart';
import 'package:beautyshop_mobile/providers/order_provider.dart';
import '../spa/spa_tickets_screen.dart';
import '../treatments/treatment_hub_screen.dart';

class VietQrPaymentScreen extends StatefulWidget {
  final OrderResponse order;

  const VietQrPaymentScreen({Key? key, required this.order}) : super(key: key);

  @override
  State<VietQrPaymentScreen> createState() => _VietQrPaymentScreenState();
}

class _VietQrPaymentScreenState extends State<VietQrPaymentScreen> {
  Timer? _pollingTimer;
  dynamic _ws;
  StreamSubscription? _wsSubscription;
  bool _isPaid = false;
  bool _isChecking = false;
  OrderResponse? _currentOrder;

  @override
  void initState() {
    super.initState();
    _startWebSocketListener();
    _startPaymentPolling();
    _checkPaymentStatus();
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    _wsSubscription?.cancel();
    try {
      _ws?.close();
    } catch (_) {}
    super.dispose();
  }

  Future<void> _startWebSocketListener() async {
    if (kIsWeb) return;
    try {
      final base = ApiConstants.baseUrl;
      final wsBase = base.startsWith('https://')
          ? base.replaceFirst('https://', 'wss://')
          : base.replaceFirst('http://', 'ws://');
      final response = await ApiClient().get<dynamic>(
        '/api/v1/orders/${widget.order.id}/payment-subscription',
      );
      final token = (response.data as Map?)?['subscriptionToken']?.toString();
      if (!mounted || _isPaid || token == null) return;
      final wsUri = Uri.parse(
        '$wsBase/ws/payment?subscriptionToken=${Uri.encodeComponent(token)}',
      );

      WebSocket.connect(wsUri.toString())
          .then((socket) {
            if (!mounted || _isPaid) {
              socket.close();
              return;
            }
            _ws = socket;
            _wsSubscription = socket.listen(
              (data) {
                try {
                  if (data == 'pong') return;
                  final decoded = jsonDecode(data.toString());
                  if (decoded is Map &&
                      (decoded['type'] == 'PAYMENT_SUCCESS' ||
                          decoded['status'] == 'PAID')) {
                    if (!_isPaid && mounted) {
                      _checkPaymentStatus();
                    }
                  }
                } catch (_) {}
              },
              onDone: () {
                if (mounted && !_isPaid)
                  Future<void>.delayed(const Duration(seconds: 2), () {
                    if (mounted && !_isPaid) _startWebSocketListener();
                  });
              },
              onError: (_) {
                // Falls back to periodic polling automatically
              },
            );
          })
          .catchError((_) {
            // Falls back to periodic polling automatically
          });
    } catch (_) {}
  }

  void _startPaymentPolling() {
    _pollingTimer = Timer.periodic(const Duration(seconds: 5), (_) async {
      await _checkPaymentStatus();
    });
  }

  Future<void> _checkPaymentStatus({bool showFeedback = false}) async {
    if (_isPaid || _isChecking) return;
    setState(() => _isChecking = true);

    final updated = await context.read<OrderProvider>().getOrderDetail(
      widget.order.id,
    );
    if (!mounted) return;

    setState(() {
      _isChecking = false;
      if (updated != null) _currentOrder = updated;
    });

    if (updated != null && updated.paymentStatus == 'PAID') {
      _pollingTimer?.cancel();
      setState(() => _isPaid = true);
      _showPaymentSuccessDialog();
    } else if (updated?.status == 'CANCELLED' ||
        (updated?.paymentDeadline != null &&
            DateTime.tryParse(
                  updated!.paymentDeadline!,
                )?.isBefore(DateTime.now()) ==
                true)) {
      _pollingTimer?.cancel();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Đơn đã hủy hoặc hết hạn thanh toán. Vui lòng kiểm tra lại đơn hàng.',
          ),
        ),
      );
      Navigator.pop(context);
    } else if (showFeedback) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Hệ thống chưa ghi nhận tiền về tài khoản. Vui lòng đợi trong giây lát!',
          ),
          backgroundColor: HasakiColors.primary,
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  void _showPaymentSuccessDialog() {
    final order = _currentOrder ?? widget.order;
    final package = order.servicePackageId != null;
    final treatment = order.spaBenefitType == 'TREATMENT_COURSE';
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.check_circle, color: HasakiColors.success, size: 28),
            SizedBox(width: 8),
            Text('Thanh Toán Thành Công!'),
          ],
        ),
        content: Text(
          package
              ? 'Đã xác nhận thanh toán #${order.orderCode}. ${treatment ? 'Mở hồ sơ điều trị để tiếp nhận và đặt từng buổi.' : 'Mở ví vé Spa để xem quyền lợi và đặt lịch bằng vé.'}'
              : 'Đơn hàng #${order.orderCode} đã được thanh toán thành công qua chuyển khoản VietQR.',
        ),
        actions: [
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: HasakiColors.primary,
            ),
            onPressed: () {
              Navigator.pop(ctx);
              if (package) {
                Navigator.pushReplacement(
                  context,
                  MaterialPageRoute(
                    builder: (_) => treatment
                        ? const TreatmentHubScreen()
                        : const SpaTicketsScreen(),
                  ),
                );
              } else {
                Navigator.popUntil(context, (route) => route.isFirst);
              }
            },
            child: Text(
              package
                  ? (treatment ? 'MỞ LỘ TRÌNH' : 'MỞ VÍ VÉ SPA')
                  : 'VỀ TRANG CHỦ',
            ),
          ),
        ],
      ),
    );
  }

  void _copyToClipboard(BuildContext context, String text, String label) {
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Đã sao chép $label: $text'),
        duration: const Duration(seconds: 2),
        backgroundColor: HasakiColors.primary,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final order = _currentOrder ?? widget.order;
    final instruction = order.paymentInstruction;
    final transferContent = instruction?.transferSyntax;
    final bankName = instruction?.bankName;
    final bankAccount = instruction?.bankAccountNumber ?? '';
    final bankAccountName = instruction?.bankAccountName;
    final qrUrl =
        (instruction?.qrCodeUrl != null && instruction!.qrCodeUrl!.isNotEmpty)
        ? instruction.qrCodeUrl!
        : '';

    return Scaffold(
      backgroundColor: HasakiColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Thanh Toán VietQR Tức Thì'),
        backgroundColor: HasakiColors.primary,
        automaticallyImplyLeading: false,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Center(
          child: Column(
            children: [
              // Polling status badge
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: _isPaid
                      ? HasakiColors.primaryLight
                      : const Color(0xFFFEF3C7),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: _isPaid
                        ? HasakiColors.primarySoft
                        : const Color(0xFFFDE68A),
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (_isChecking)
                      const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: HasakiColors.primary,
                        ),
                      )
                    else
                      Icon(
                        _isPaid ? Icons.check_circle : Icons.sync,
                        size: 16,
                        color: _isPaid
                            ? HasakiColors.primary
                            : const Color(0xFFB45309),
                      ),
                    const SizedBox(width: 8),
                    Text(
                      _isPaid
                          ? 'Đã xác nhận thanh toán'
                          : 'Đang lắng nghe thanh toán từ ngân hàng...',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: _isPaid
                            ? HasakiColors.primary
                            : const Color(0xFFB45309),
                      ),
                    ),
                  ],
                ),
              ),

              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: HasakiColors.getSurface(context),
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.06),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Column(
                  children: [
                    const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.qr_code_2,
                          color: HasakiColors.primary,
                          size: 28,
                        ),
                        SizedBox(width: 8),
                        Text(
                          'Quét mã qua App Ngân Hàng',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    // QR Image
                    if (qrUrl.isNotEmpty)
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          border: Border.all(color: HasakiColors.border),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Image.network(
                          qrUrl,
                          width: 220,
                          height: 220,
                          fit: BoxFit.contain,
                          errorBuilder: (_, __, ___) => const SizedBox(
                            width: 220,
                            height: 220,
                            child: Center(
                              child: Text(
                                'Không thể tải mã QR. Vui lòng tải lại thông tin thanh toán.',
                              ),
                            ),
                          ),
                        ),
                      ),
                    const SizedBox(height: 16),

                    if (instruction == null ||
                        bankAccount.isEmpty ||
                        transferContent == null ||
                        transferContent.isEmpty)
                      const Text(
                        'Chưa có đầy đủ hướng dẫn chuyển khoản. Vui lòng tải lại thông tin thanh toán.',
                      ),
                    if (bankName != null)
                      _buildPaymentDetailRow('Ngân hàng:', bankName),
                    _buildCopyableRow(
                      context,
                      'Số tài khoản:',
                      bankAccount,
                      'Số tài khoản',
                    ),
                    if (bankAccountName != null)
                      _buildPaymentDetailRow('Chủ tài khoản:', bankAccountName),
                    _buildPaymentDetailRow(
                      'Số tiền:',
                      HasakiFormatters.formatCurrency(order.totalAmount),
                    ),
                    if (transferContent != null && transferContent.isNotEmpty)
                      _buildCopyableRow(
                        context,
                        'Nội dung CK:',
                        transferContent,
                        'Nội dung chuyển khoản',
                      ),
                    TextButton(
                      onPressed: _isChecking
                          ? null
                          : () async {
                              final refreshed = await context
                                  .read<OrderProvider>()
                                  .getOrderDetail(order.id);
                              if (context.mounted && refreshed != null)
                                Navigator.pushReplacement(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) =>
                                        VietQrPaymentScreen(order: refreshed),
                                  ),
                                );
                            },
                      child: const Text('Tải lại thông tin thanh toán'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  onPressed: () => _checkPaymentStatus(showFeedback: true),
                  child: const Text('KIỂM TRA TRẠNG THÁI THANH TOÁN'),
                ),
              ),
              const SizedBox(height: 10),
              TextButton(
                onPressed: () =>
                    Navigator.popUntil(context, (route) => route.isFirst),
                child: const Text(
                  'Tôi sẽ thanh toán sau',
                  style: TextStyle(color: HasakiColors.textMuted),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPaymentDetailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: HasakiTypography.caption.copyWith(
              color: HasakiColors.textMuted,
            ),
          ),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCopyableRow(
    BuildContext context,
    String label,
    String value,
    String copyLabel,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: HasakiTypography.caption.copyWith(
              color: HasakiColors.textMuted,
            ),
          ),
          Expanded(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Flexible(
                  child: Text(
                    value,
                    textAlign: TextAlign.right,
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 13,
                      color: HasakiColors.primary,
                    ),
                  ),
                ),
                const SizedBox(width: 4),
                InkWell(
                  onTap: () => _copyToClipboard(context, value, copyLabel),
                  child: const Icon(
                    Icons.copy,
                    size: 16,
                    color: HasakiColors.primary,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
