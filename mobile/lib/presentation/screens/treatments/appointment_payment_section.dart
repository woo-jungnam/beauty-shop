import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../../../data/models/order_model.dart';
import '../../../data/models/treatment_model.dart';
import '../../../providers/treatment_provider.dart';

class AppointmentPaymentSection extends StatefulWidget {
  const AppointmentPaymentSection({
    super.key,
    required this.id,
    required this.status,
  });
  final int id;
  final String status;

  @override
  State<AppointmentPaymentSection> createState() =>
      _AppointmentPaymentSectionState();
}

class _AppointmentPaymentSectionState extends State<AppointmentPaymentSection>
    with WidgetsBindingObserver {
  late TreatmentProvider _provider;
  late int _generation;
  ModalRoute<dynamic>? _route;
  Timer? _timer;
  JsonMap? _finance, _invoice;
  String? _financeError, _invoiceError;
  bool _busy = false, _ready = false, _resumed = true;

  bool get _valid =>
      mounted && _provider.authenticated && _generation == _provider.generation;
  bool get _active =>
      _valid &&
      _resumed &&
      (_route?.isCurrent ?? true) &&
      TickerMode.of(context);
  num _amount(dynamic value) => num.tryParse('$value') ?? 0;
  bool get _depositOpen =>
      ['PENDING', 'CONFIRMED'].contains(widget.status) && _finance?['closed'] != true;
  bool get _pending =>
      (_depositOpen &&
          (_finance == null || _amount(_finance!['depositDue']) > 0)) ||
      (widget.status == 'COMPLETED' &&
          (_invoice == null ||
              (_amount(_invoice!['amountDue']) > 0 &&
                  _invoice!['paymentStatus'] != 'PAID')));

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _resumed =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    _provider = context.read<TreatmentProvider>();
    _generation = _provider.generation;
    _provider.addListener(_sessionChanged);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final route = ModalRoute.of(context);
    if (_route != route) {
      _route?.secondaryAnimation?.removeStatusListener(_routeChanged);
      _route = route;
      _route?.secondaryAnimation?.addStatusListener(_routeChanged);
    }
    if (!_ready) {
      _ready = true;
      WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
    } else if (!_active) {
      _timer?.cancel();
    } else if (!_busy && _timer?.isActive != true) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
    }
  }

  @override
  void didUpdateWidget(covariant AppointmentPaymentSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.status != widget.status) _refresh();
  }

  void _sessionChanged() {
    if (!_valid) {
      _timer?.cancel();
      if (mounted)
        setState(() {
          _finance = null;
          _invoice = null;
        });
    }
  }

  void _routeChanged(AnimationStatus status) {
    if (!_active) {
      _timer?.cancel();
    } else if (status == AnimationStatus.dismissed) {
      _refresh();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _resumed = state == AppLifecycleState.resumed;
    _timer?.cancel();
    if (_resumed) _refresh();
  }

  Future<void> _refresh({bool createDeposit = false}) async {
    if (!_active || _busy) return;
    _timer?.cancel();
    setState(() => _busy = true);
    try {
      try {
        final finance = createDeposit
            ? await _provider.createDepositPayment(widget.id)
            : await _provider.finance(widget.id);
        if (!_valid) return;
        setState(() {
          _finance = finance;
          _financeError = null;
        });
      } catch (error) {
        if (!_valid) return;
        setState(() => _financeError = '$error');
      }
      if (!_active) return;
      if (widget.status == 'COMPLETED') {
        try {
          final invoice = await _provider.invoice(widget.id);
          if (!_valid) return;
          setState(() {
            _invoice = invoice;
            _invoiceError = null;
          });
        } catch (error) {
          if (!_valid) return;
          setState(() => _invoiceError = '$error');
        }
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
        if (_active && _pending) {
          _timer = Timer(const Duration(seconds: 5), _refresh);
        }
      }
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _route?.secondaryAnimation?.removeStatusListener(_routeChanged);
    _provider.removeListener(_sessionChanged);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Widget _instruction(dynamic raw, num due) {
    if (raw is! Map)
      return const Text('Chưa có hướng dẫn chuyển khoản. Liên hệ tiếp tân.');
    final instruction = PaymentInstruction.fromJson(treatmentMap(raw));
    final account = instruction.bankAccountNumber;
    final syntax = instruction.transferSyntax;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (instruction.instructionMessage?.isNotEmpty == true)
          Text(instruction.instructionMessage!),
        if (instruction.qrCodeUrl?.isNotEmpty == true)
          Image.network(
            instruction.qrCodeUrl!,
            key: ValueKey(instruction.qrCodeUrl),
            width: 220,
            height: 220,
            semanticLabel: 'Mã QR thanh toán $due đồng',
            errorBuilder: (_, error, stack) => const Text(
              'Không tải được QR. Dùng thông tin chuyển khoản bên dưới.',
            ),
          ),
        if (instruction.bankName != null)
          Text('Ngân hàng: ${instruction.bankName}'),
        if (instruction.bankAccountName != null)
          Text('Chủ tài khoản: ${instruction.bankAccountName}'),
        if (account != null) SelectableText('Số tài khoản: $account'),
        if (syntax != null) SelectableText('Nội dung: $syntax'),
        if (account != null || syntax != null)
          TextButton.icon(
            onPressed: () async {
              await Clipboard.setData(
                ClipboardData(
                  text:
                      '${instruction.bankName ?? ''}\n${instruction.bankAccountName ?? ''}\n${account ?? ''}\nSố tiền: $due VND\n${syntax ?? ''}',
                ),
              );
              if (mounted)
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Đã sao chép thông tin chuyển khoản'),
                  ),
                );
            },
            icon: const Icon(Icons.copy),
            label: const Text('Sao chép chuyển khoản'),
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!_valid) return const SizedBox.shrink();
    final finance = _finance;
    final payment = treatmentMap(finance?['depositPayment']);
    final due = _amount(finance?['depositDue']);
    final invoice = _invoice;
    final invoiceDue = _amount(invoice?['amountDue']);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Cọc & phí lịch hẹn',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            if (_busy) const LinearProgressIndicator(),
            if (_financeError != null)
              Text('Chưa tải được thông tin cọc/phí: $_financeError'),
            if (finance != null) ...[
              for (final entry in const <String, String>{
                'depositRequired': 'Cọc yêu cầu',
                'depositCollected': 'Đã thu cọc',
                'depositDue': 'Cọc còn thiếu',
                'appliedToInvoice': 'Đã trừ vào hóa đơn',
                'assessedFee': 'Phí phát sinh theo chính sách',
                'feePaid': 'Phí đã thu',
                'feeDue': 'Phí còn thiếu',
                'refundable': 'Cọc còn có thể hoàn',
                'refunded': 'Đã hoàn',
              }.entries)
                Text('${entry.value}: ${finance[entry.key] ?? 0} ₫'),
              for (final reason
                  in finance['reviewReasons'] as List? ?? const [])
                Text('Cần đối soát: $reason'),
              if (_amount(finance['depositRequired']) == 0)
                const Text(
                  'Không yêu cầu cọc. Vé đã mua không thu lại phí dịch vụ.',
                ),
              if (_amount(finance['depositRequired']) > 0 && due == 0)
                const Text('Đã đủ cọc theo hệ thống.'),
              if (_depositOpen && due > 0) ...[
                if (_financeError == null && payment['canPay'] == true)
                  _instruction(payment['paymentInstruction'], due),
                if (payment.isEmpty || payment['canPay'] == true && payment['paymentInstruction'] == null)
                  OutlinedButton(
                    onPressed: _busy
                        ? null
                        : () => _refresh(createDeposit: true),
                    child: const Text('Tạo QR trả cọc'),
                  ),
              ],
            ],
            if (widget.status == 'COMPLETED') ...[
              const Divider(),
              const Text(
                'Hóa đơn buổi dịch vụ',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              if (_invoiceError != null)
                Text('Chưa tải được hóa đơn: $_invoiceError')
              else if (invoice == null && !_busy)
                const Text('Chờ nhân viên lập hóa đơn'),
              if (invoice != null) ...[
                Text(
                  'Hóa đơn: ${invoice['orderNumber'] ?? invoice['orderId'] ?? ''}',
                ),
                Text('Còn phải trả: ${invoice['amountDue']} ₫'),
                Text(
                  'Trạng thái thanh toán: ${invoice['paymentStatus'] ?? 'Chưa xác định'}',
                ),
                if (invoice['paymentStatus'] == 'PAID')
                  const Text('Hóa đơn đã thanh toán theo hệ thống.')
                else if (_invoiceError == null && invoiceDue > 0 && invoice['paymentMethod'] == 'BANK')
                  _instruction(invoice['paymentInstruction'], invoiceDue)
                else if (invoiceDue > 0)
                  const Text('Liên hệ tiếp tân để thanh toán tại quầy.'),
              ],
            ],
            TextButton(
              onPressed: _busy ? null : _refresh,
              child: const Text('Kiểm tra thanh toán'),
            ),
            const Text(
              'Chỉ ghi nhận thanh toán khi hệ thống xác nhận. Không chuyển lại phần tiền đã thu.',
            ),
          ],
        ),
      ),
    );
  }
}
