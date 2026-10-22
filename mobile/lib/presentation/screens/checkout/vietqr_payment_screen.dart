import '../../../core/network/api_client.dart';
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
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

class _VietQrPaymentScreenState extends State<VietQrPaymentScreen>
    with WidgetsBindingObserver {
  Timer? _pollingTimer;
  dynamic _ws;
  StreamSubscription? _wsSubscription;
  bool _isPaid = false;
  bool _isChecking = false;
  OrderResponse? _currentOrder;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _startWebSocketListener();
    _startPaymentPolling();
    _checkPaymentStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pollingTimer?.cancel();
    _wsSubscription?.cancel();
    try {
      _ws?.close();
    } catch (_) {}
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_isPaid) {
      _checkPaymentStatus();
    }
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
              onError: (_) {},
            );
          })
          .catchError((_) {});
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
          backgroundColor: AppColors.primary,
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
            Icon(Icons.check_circle, color: AppColors.success, size: 28),
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
              backgroundColor: AppColors.primary,
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
        backgroundColor: AppColors.primary,
      ),
    );
  }

  Future<void> _openBankingApp(
    PaymentInstruction? instruction,
    double amount,
  ) async {
    if (instruction == null) return;
    final syntax = instruction.transferSyntax ?? '';

    if (syntax.isNotEmpty) {
      await Clipboard.setData(ClipboardData(text: syntax));
    }

    final bankBin = instruction.bankBin ?? '970422';
    final bankAccount = instruction.bankAccountNumber ?? '';
    final amountInt = amount.toInt();
    final accountName = instruction.bankAccountName ?? '';

    String deepLink = instruction.deepLinkUrl ?? '';
    if (deepLink.isEmpty) {
      final encodedName = Uri.encodeComponent(accountName);
      final encodedSyntax = Uri.encodeComponent(syntax);
      deepLink =
          'https://api.vietqr.io/$bankBin/$bankAccount?amount=$amountInt&addInfo=$encodedSyntax&accountName=$encodedName';
    }

    try {
      final uri = Uri.parse(deepLink);
      final launched = await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );

      if (!launched) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Không thể mở app ngân hàng tự động. Đã sao chép nội dung CK, vui lòng mở app ngân hàng của bạn.',
              ),
              backgroundColor: Color(0xFFB45309),
            ),
          );
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Đang chuyển sang ứng dụng ngân hàng... Vui lòng xác thực thanh toán.',
              ),
              backgroundColor: AppColors.primary,
              duration: Duration(seconds: 2),
            ),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Lỗi mở ứng dụng ngân hàng: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  void _copyAllPaymentInfo(PaymentInstruction? instruction, double amount) {
    if (instruction == null) return;
    final buffer = StringBuffer();
    if (instruction.bankName != null) {
      buffer.writeln('Ngân hàng: ${instruction.bankName}');
    }
    if (instruction.bankAccountNumber != null) {
      buffer.writeln('Số tài khoản: ${instruction.bankAccountNumber}');
    }
    if (instruction.bankAccountName != null) {
      buffer.writeln('Chủ tài khoản: ${instruction.bankAccountName}');
    }
    buffer.writeln('Số tiền: ${AppFormatters.formatCurrency(amount)}');
    if (instruction.transferSyntax != null) {
      buffer.writeln('Nội dung chuyển khoản: ${instruction.transferSyntax}');
    }

    Clipboard.setData(ClipboardData(text: buffer.toString().trim()));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Đã sao chép toàn bộ thông tin thanh toán!'),
        backgroundColor: AppColors.primary,
        duration: Duration(seconds: 2),
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
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Thanh Toán VietQR Tức Thì'),
        backgroundColor: AppColors.primary,
        automaticallyImplyLeading: false,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Center(
          child: Column(
            children: [
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: _isPaid
                      ? AppColors.primaryLight
                      : const Color(0xFFFEF3C7),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: _isPaid
                        ? AppColors.primarySoft
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
                          color: AppColors.primary,
                        ),
                      )
                    else
                      Icon(
                        _isPaid ? Icons.check_circle : Icons.sync,
                        size: 16,
                        color: _isPaid
                            ? AppColors.primary
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
                            ? AppColors.primary
                            : const Color(0xFFB45309),
                      ),
                    ),
                  ],
                ),
              ),

              Container(
                width: double.infinity,
                margin: const EdgeInsets.only(bottom: 16),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      AppColors.primary,
                      AppColors.primary.withOpacity(0.85),
                    ],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.primary.withOpacity(0.28),
                      blurRadius: 10,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: Colors.white.withOpacity(0.2),
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            Icons.bolt,
                            color: Colors.amberAccent,
                            size: 24,
                          ),
                        ),
                        const SizedBox(width: 12),
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Thanh toán tức thì 1-chạm',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 15,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              SizedBox(height: 2),
                              Text(
                                'Tự động mở App Ngân Hàng & điền sẵn thông tin',
                                style: TextStyle(
                                  color: Colors.white70,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.white,
                          foregroundColor: AppColors.primary,
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                        ),
                        icon: const Icon(Icons.account_balance, size: 20),
                        label: const Text(
                          'MỞ APP NGÂN HÀNG THANH TOÁN',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 0.3,
                          ),
                        ),
                        onPressed: () => _openBankingApp(
                          instruction,
                          order.totalAmount,
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    InkWell(
                      onTap: () => _copyAllPaymentInfo(
                        instruction,
                        order.totalAmount,
                      ),
                      borderRadius: BorderRadius.circular(8),
                      child: const Padding(
                        padding: EdgeInsets.symmetric(
                          vertical: 4,
                          horizontal: 8,
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.copy_all,
                              color: Colors.white70,
                              size: 16,
                            ),
                            SizedBox(width: 6),
                            Text(
                              'Sao chép toàn bộ thông tin CK',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                decoration: TextDecoration.underline,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.getSurface(context),
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
                          color: AppColors.primary,
                          size: 24,
                        ),
                        SizedBox(width: 8),
                        Text(
                          'Hoặc quét mã QR từ thiết bị khác',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    if (qrUrl.isNotEmpty)
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          border: Border.all(color: AppColors.border),
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
                      AppFormatters.formatCurrency(order.totalAmount),
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
                  style: TextStyle(color: AppColors.textMuted),
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
            style: AppTypography.caption.copyWith(
              color: AppColors.textMuted,
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
            style: AppTypography.caption.copyWith(
              color: AppColors.textMuted,
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
                      color: AppColors.primary,
                    ),
                  ),
                ),
                const SizedBox(width: 4),
                InkWell(
                  onTap: () => _copyToClipboard(context, value, copyLabel),
                  child: const Icon(
                    Icons.copy,
                    size: 16,
                    color: AppColors.primary,
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
