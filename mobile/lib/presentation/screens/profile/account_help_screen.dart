import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/network/api_client.dart';

class AccountHelpScreen extends StatefulWidget {
  const AccountHelpScreen({super.key, required this.showBranches});

  final bool showBranches;

  @override
  State<AccountHelpScreen> createState() => _AccountHelpScreenState();
}

class _AccountHelpScreenState extends State<AccountHelpScreen> {
  late Future<Map<String, dynamic>> _details = _load();

  Future<Map<String, dynamic>> _load() async {
    final response = await ApiClient().get<dynamic>(
      widget.showBranches
          ? '/api/v1/system-configs/public'
          : '/api/v1/spa/booking-policy',
    );
    if (!response.isSuccess || response.data is! Map) {
      throw StateError('Missing public information');
    }
    return Map<String, dynamic>.from(response.data as Map);
  }

  Future<void> _open(Uri uri) async {
    try {
      if (await launchUrl(uri, mode: LaunchMode.externalApplication)) return;
    } catch (_) {}
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Không thể mở ứng dụng liên kết.')),
      );
    }
  }

  List<Widget> _contact(Map<String, dynamic> config) {
    final address = (config['store.address'] as String? ?? '').trim();
    final hotline = (config['store.hotline'] as String? ?? '').trim();
    final email = (config['store.email'] as String? ?? '').trim();
    return [
      if (address.isEmpty)
        const ListTile(title: Text('Cửa hàng chưa công bố địa chỉ chi nhánh.'))
      else
        ListTile(
          leading: const Icon(Icons.location_on_outlined),
          title: const Text('Địa chỉ cửa hàng'),
          subtitle: Text(address),
          trailing: const Icon(Icons.open_in_new),
          onTap: () => _open(
            Uri.https('www.google.com', '/maps/search/', {
              'api': '1',
              'query': address,
            }),
          ),
        ),
      if (hotline.isNotEmpty)
        ListTile(
          leading: const Icon(Icons.phone_outlined),
          title: Text(hotline),
          onTap: () => _open(
            Uri(
              scheme: 'tel',
              path: hotline.replaceAll(RegExp(r'[^+0-9]'), ''),
            ),
          ),
        ),
      if (email.isNotEmpty)
        ListTile(
          leading: const Icon(Icons.email_outlined),
          title: Text(email),
          onTap: () => _open(Uri(scheme: 'mailto', path: email)),
        ),
    ];
  }

  List<Widget> _bookingRules(Map<String, dynamic> policy) {
    final rules = {
      'pendingTtlMinutes': 'Thời hạn xác nhận lịch chờ',
      'cancelCutoffMinutes': 'Hủy lịch trước giờ hẹn tối thiểu',
      'rescheduleCutoffMinutes': 'Đổi lịch trước giờ hẹn tối thiểu',
      'noShowGraceMinutes': 'Thời gian chờ khi khách đến muộn',
    };
    return [
      const ListTile(title: Text('Quy định đặt và thay đổi lịch Spa')),
      for (final rule in rules.entries)
        if (policy[rule.key] is num)
          ListTile(
            title: Text(rule.value),
            subtitle: Text('${policy[rule.key]} phút'),
          ),
      if (policy['maxReschedules'] is num)
        ListTile(
          title: const Text('Số lần đổi lịch tối đa'),
          subtitle: Text('${policy['maxReschedules']} lần'),
        ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.showBranches ? 'Hệ thống chi nhánh' : 'Chính sách cam kết',
        ),
      ),
      body: FutureBuilder<Map<String, dynamic>>(
        future: _details,
        builder: (context, snapshot) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (!widget.showBranches) ...[
              const ListTile(
                title: Text('Giao hàng'),
                subtitle: Text(
                  'Kiểm tra phí tại bước thanh toán và theo dõi vận chuyển trong chi tiết đơn.',
                ),
              ),
              const ListTile(
                title: Text('Đổi trả và hoàn tiền'),
                subtitle: Text(
                  'Mở đơn đã giao để gửi yêu cầu đổi trả. Cửa hàng sẽ xét duyệt theo điều kiện của đơn và thông báo kết quả.',
                ),
              ),
              const ListTile(
                title: Text('Thông tin cá nhân'),
                subtitle: Text(
                  'Bạn có thể cập nhật thông tin hồ sơ và địa chỉ nhận hàng trong tài khoản.',
                ),
              ),
              const Divider(),
            ],
            if (snapshot.connectionState != ConnectionState.done)
              const LinearProgressIndicator()
            else if (snapshot.hasError) ...[
              const Text('Chưa tải được thông tin công khai.'),
              TextButton(
                onPressed: () => setState(() => _details = _load()),
                child: const Text('Thử lại'),
              ),
            ] else if (snapshot.hasData)
              ...(widget.showBranches
                  ? _contact(snapshot.data!)
                  : _bookingRules(snapshot.data!)),
          ],
        ),
      ),
    );
  }
}
