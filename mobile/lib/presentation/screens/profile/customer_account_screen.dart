import '../treatments/treatment_enrollment_screen.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/network/api_client.dart';
import '../../../providers/auth_provider.dart';
import '../orders/order_detail_screen.dart';
import '../product_detail/product_detail_screen.dart';
import '../treatments/treatment_appointment_screen.dart';

typedef AccountMap = Map<String, dynamic>;
List<AccountMap> accountRows(dynamic data) =>
    (data is List
            ? data
            : data is Map
            ? data['content'] as List? ?? []
            : [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();

class CustomerAccountScreen extends StatefulWidget {
  const CustomerAccountScreen({
    super.key,
    this.area = 'profile',
    this.selectAddress = false,
  });
  final String area;
  final bool selectAddress;
  @override
  State<CustomerAccountScreen> createState() => _CustomerAccountScreenState();
}

class _CustomerAccountScreenState extends State<CustomerAccountScreen> {
  final api = ApiClient();
  dynamic data;
  String? error;
  bool busy = false;
  int page = 0, totalPages = 1;
  String get path => switch (widget.area) {
    'skin' => '/api/v1/users/skin-profile',
    'addresses' => '/api/v1/users/addresses',
    'wishlist' => '/api/v1/users/wishlist',
    'notifications' => '/api/v1/users/notifications',
    _ => '/api/v1/users/profile',
  };
  String get title => switch (widget.area) {
    'skin' => 'Hồ sơ làn da',
    'addresses' => 'Sổ địa chỉ',
    'wishlist' => 'Sản phẩm yêu thích',
    'notifications' => 'Thông báo',
    _ => 'Thông tin & bảo mật',
  };
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final response = await api.get<dynamic>(
        path,
        queryParameters: widget.area == 'notifications'
            ? {'page': page, 'size': 20}
            : null,
      );
      if (!mounted) return;
      setState(() {
        data = response.data;
        totalPages = data is Map
            ? (data['totalPages'] as num?)?.toInt() ?? 1
            : 1;
      });
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> edit(
    Map<String, String> fields, {
    AccountMap? initial,
    required String endpoint,
    bool post = false,
  }) async {
    final result = await accountForm(
      context,
      title: title,
      fields: fields,
      initial: initial,
    );
    if (result == null || !mounted) return;
    if (widget.area == 'profile' && result['dateOfBirth'] == '')
      result.remove('dateOfBirth');
    setState(() {
      busy = true;
      error = null;
    });
    try {
      if (post) {
        await api.post<dynamic>(endpoint, data: result);
      } else {
        await api.put<dynamic>(endpoint, data: result);
      }
      if (!mounted) return;
      if (widget.area == 'profile')
        await context.read<AuthProvider>().fetchCurrentUser();
      await load();
    } catch (e) {
      if (mounted)
        setState(() {
          error = '$e';
          busy = false;
        });
    }
  }

  Future<void> changePassword() async {
    final value = await accountForm(
      context,
      title: 'Đổi mật khẩu',
      fields: {
        'currentPassword': 'Mật khẩu hiện tại',
        'newPassword': 'Mật khẩu mới (ít nhất 8 ký tự)',
      },
    );
    if (value == null || !mounted) return;
    setState(() => busy = true);
    try {
      await api.put<dynamic>('/api/v1/users/password', data: value);
      if (!mounted) return;
      await context.read<AuthProvider>().logout();
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted)
        setState(() {
          error = '$e';
          busy = false;
        });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!context.watch<AuthProvider>().isAuthenticated)
      return Scaffold(
        appBar: AppBar(title: Text(title)),
        body: const Center(child: Text('Vui lòng đăng nhập để mở tài khoản.')),
      );
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
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
          if (widget.area == 'profile' && data is Map) ...[
            ...['fullName', 'email', 'phone', 'dateOfBirth', 'bio']
                .where((key) => data[key] != null)
                .map(
                  (key) => ListTile(
                    title: Text(
                      {
                        'fullName': 'Họ và tên',
                        'email': 'Email',
                        'phone': 'Điện thoại',
                        'dateOfBirth': 'Ngày sinh',
                        'bio': 'Giới thiệu',
                      }[key]!,
                    ),
                    subtitle: Text('${data[key]}'),
                  ),
                ),
            FilledButton(
              onPressed: busy
                  ? null
                  : () => edit(
                      {
                        'fullName': 'Họ và tên',
                        'phone': 'Số điện thoại',
                        'avatarUrl': 'Ảnh đại diện (URL)',
                        'bio': 'Giới thiệu',
                        'dateOfBirth': 'Ngày sinh YYYY-MM-DD',
                      },
                      initial: Map<String, dynamic>.from(data),
                      endpoint: path,
                    ),
              child: const Text('Chỉnh sửa thông tin'),
            ),
            OutlinedButton(
              onPressed: busy ? null : changePassword,
              child: const Text('Đổi mật khẩu'),
            ),
          ],
          if (widget.area == 'skin') ...[
            const Text(
              'Thông tin do bạn cung cấp để chuyên viên tham khảo. Để trống nếu chưa xác định; có thể cập nhật sau khi tư vấn.',
            ),
            ...{
              'skinType': 'Loại da',
              'sensitivity': 'Mức độ nhạy cảm',
              'allergies': 'Dị ứng',
              'contraindications': 'Lưu ý / chống chỉ định đã tư vấn',
              'preferredIngredients': 'Hoạt chất ưu tiên',
              'notes': 'Thông tin khác',
            }.entries.map(
              (e) => ListTile(
                title: Text(e.value),
                subtitle: Text(
                  '${data is Map ? data[e.key] ?? 'Chưa cung cấp' : 'Chưa cung cấp'}',
                ),
              ),
            ),
            FilledButton(
              onPressed: busy
                  ? null
                  : () => edit(
                      {
                        'skinType': 'Loại da',
                        'sensitivity': 'Mức độ nhạy cảm',
                        'allergies': 'Dị ứng',
                        'contraindications': 'Lưu ý / chống chỉ định',
                        'preferredIngredients': 'Hoạt chất ưu tiên',
                        'notes': 'Thông tin khác',
                      },
                      initial: data is Map
                          ? Map<String, dynamic>.from(data)
                          : {},
                      endpoint: path,
                    ),
              child: const Text('Cập nhật hồ sơ da'),
            ),
          ],
          if (widget.area == 'addresses') ...[
            ...accountRows(data).map(
              (row) => Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${row['recipientName']} ${row['isDefault'] == true ? '· Mặc định' : ''}',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      Text(
                        '${row['phone']}\n${['address', 'ward', 'district', 'city'].map((key) => row[key]).where((v) => v != null && '$v'.isNotEmpty).join(', ')}',
                      ),
                      Wrap(
                        spacing: 12,
                        children: [
                          if (widget.selectAddress)
                            TextButton(
                              onPressed: () => Navigator.pop(context, row),
                              child: const Text('Chọn địa chỉ'),
                            ),
                          TextButton(
                            onPressed: busy
                                ? null
                                : () => edit(
                                    addressFormFields,
                                    initial: row,
                                    endpoint: '$path/${row['id']}',
                                  ),
                            child: const Text('Sửa'),
                          ),
                          TextButton(
                            onPressed: busy
                                ? null
                                : () async {
                                    final confirm = await showDialog<bool>(
                                      context: context,
                                      builder: (ctx) => AlertDialog(
                                        title: const Text('Xóa địa chỉ?'),
                                        actions: [
                                          TextButton(
                                            onPressed: () =>
                                                Navigator.pop(ctx, false),
                                            child: const Text('Giữ lại'),
                                          ),
                                          TextButton(
                                            onPressed: () =>
                                                Navigator.pop(ctx, true),
                                            child: const Text('Xóa'),
                                          ),
                                        ],
                                      ),
                                    );
                                    if (confirm != true) return;
                                    try {
                                      await api.delete<dynamic>(
                                        '$path/${row['id']}',
                                      );
                                      await load();
                                    } catch (e) {
                                      if (mounted) setState(() => error = '$e');
                                    }
                                  },
                            child: const Text('Xóa'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
            FilledButton(
              onPressed: busy
                  ? null
                  : () => edit(addressFormFields, endpoint: path, post: true),
              child: const Text('Thêm địa chỉ'),
            ),
          ],
          if (widget.area == 'wishlist')
            ...accountRows(data).map(
              (row) => Card(
                child: ListTile(
                  title: Text('${row['name']}'),
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => ProductDetailScreen(
                        productId: (row['id'] as num).toInt(),
                      ),
                    ),
                  ),
                  trailing: IconButton(
                    tooltip: 'Bỏ yêu thích',
                    icon: const Icon(Icons.favorite),
                    onPressed: busy
                        ? null
                        : () async {
                            try {
                              await api.delete<dynamic>('$path/${row['id']}');
                              await load();
                            } catch (e) {
                              if (mounted) setState(() => error = '$e');
                            }
                          },
                  ),
                ),
              ),
            ),
          if (widget.area == 'notifications') ...[
            ...accountRows(data).map(
              (row) => Card(
                child: ListTile(
                  leading: Icon(
                    row['readAt'] == null
                        ? Icons.notifications_active
                        : Icons.notifications_none,
                  ),
                  title: Text('${row['title']}'),
                  subtitle: Text('${row['message'] ?? row['body'] ?? ''}'),
                  onTap: () async {
                    try {
                      if (row['readAt'] == null)
                        await api.put<dynamic>('$path/${row['id']}/read');
                      if (!context.mounted) return;
                      final target = Uri.tryParse('${row['target'] ?? ''}');
                      final query =
                          target?.queryParameters ?? <String, String>{};
                      final orderId = int.tryParse(
                        '${row['orderId'] ?? query['order'] ?? (target?.path == 'order-lookup' ? query['id'] : null)}',
                      );
                      final appointmentId = int.tryParse(
                        '${row['appointmentId'] ?? query['appointment']}',
                      );
                      final courseId = int.tryParse(
                        '${row['courseId'] ?? query['course']}',
                      );
                      Widget? destination;
                      if (orderId != null)
                        destination = OrderDetailScreen(orderId: orderId);
                      else if (appointmentId != null)
                        destination = TreatmentAppointmentScreen(
                          id: appointmentId,
                          operate: false,
                        );
                      else if (courseId != null)
                        destination = TreatmentEnrollmentScreen(id: courseId);
                      if (destination != null)
                        await Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => destination!),
                        );
                      await load();
                    } catch (e) {
                      if (mounted) setState(() => error = '$e');
                    }
                  },
                ),
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                TextButton(
                  onPressed: page == 0 || busy
                      ? null
                      : () {
                          page--;
                          load();
                        },
                  child: const Text('Trang trước'),
                ),
                Text('${page + 1} / ${totalPages < 1 ? 1 : totalPages}'),
                TextButton(
                  onPressed: page + 1 >= totalPages || busy
                      ? null
                      : () {
                          page++;
                          load();
                        },
                  child: const Text('Trang sau'),
                ),
              ],
            ),
          ],
          if ([
                'wishlist',
                'notifications',
                'addresses',
              ].contains(widget.area) &&
              !busy &&
              error == null &&
              accountRows(data).isEmpty)
            const Padding(
              padding: EdgeInsets.all(20),
              child: Text('Chưa có dữ liệu.'),
            ),
        ],
      ),
    );
  }
}

const addressFormFields = {
  'recipientName': 'Người nhận',
  'phone': 'Số điện thoại',
  'address': 'Số nhà, tên đường',
  'ward': 'Phường / xã',
  'district': 'Quận / huyện (nếu có)',
  'city': 'Tỉnh / thành phố',
};

Future<AccountMap?> accountForm(
  BuildContext context, {
  required String title,
  required Map<String, String> fields,
  AccountMap? initial,
}) async {
  final controllers = {
    for (final key in fields.keys)
      key: TextEditingController(text: '${initial?[key] ?? ''}'),
  };
  final key = GlobalKey<FormState>();
  var isDefault = initial?['isDefault'] == true;
  final result = await showDialog<AccountMap>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, update) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 460,
          child: Form(
            key: key,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ...fields.entries.map(
                    (entry) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: TextFormField(
                        controller: controllers[entry.key],
                        obscureText: entry.key.toLowerCase().contains(
                          'password',
                        ),
                        maxLength: switch (entry.key) {
                          'newPassword' || 'currentPassword' => 128,
                          'phone' => 20,
                          'fullName' ||
                          'recipientName' ||
                          'skinType' ||
                          'sensitivity' ||
                          'city' ||
                          'district' ||
                          'ward' => 100,
                          'avatarUrl' || 'bio' || 'address' => 500,
                          'dateOfBirth' => 10,
                          'email' => 254,
                          'token' => 256,
                          'notes' => 2000,
                          _ => 1000,
                        },
                        decoration: InputDecoration(labelText: entry.value),
                        validator: (value) {
                          final text = value?.trim() ?? '';
                          if ([
                                'fullName',
                                'recipientName',
                                'address',
                                'ward',
                                'city',
                                'currentPassword',
                                'newPassword',
                                'token',
                                'email',
                              ].contains(entry.key) &&
                              text.isEmpty)
                            return 'Vui lòng nhập thông tin';
                          if (entry.key == 'newPassword' && text.length < 8)
                            return 'Mật khẩu ít nhất 8 ký tự';
                          if (entry.key == 'phone' &&
                              fields.containsKey('recipientName') &&
                              text.isEmpty)
                            return 'Vui lòng nhập số điện thoại';
                          if (entry.key == 'phone' &&
                              text.isNotEmpty &&
                              !RegExp(r'^0[35789]\d{8}$').hasMatch(text))
                            return 'Số điện thoại không hợp lệ';
                          if (entry.key == 'dateOfBirth' &&
                              text.isNotEmpty &&
                              DateTime.tryParse(text) == null)
                            return 'Dùng định dạng YYYY-MM-DD';
                          return null;
                        },
                      ),
                    ),
                  ),
                  if (fields.containsKey('recipientName'))
                    CheckboxListTile(
                      value: isDefault,
                      onChanged: (v) => update(() => isDefault = v ?? false),
                      title: const Text('Địa chỉ mặc định'),
                    ),
                ],
              ),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () {
              if (key.currentState!.validate())
                Navigator.pop(ctx, <String, dynamic>{
                  for (final entry in controllers.entries)
                    entry.key: entry.key.toLowerCase().contains('password')
                        ? entry.value.text
                        : entry.value.text.trim(),
                  if (fields.containsKey('recipientName'))
                    'isDefault': isDefault,
                });
            },
            child: const Text('Lưu'),
          ),
        ],
      ),
    ),
  );
  Future<void>.delayed(const Duration(milliseconds: 400), () {
    for (final controller in controllers.values) {
      controller.dispose();
    }
  });
  return result;
}

class PasswordRecoveryScreen extends StatefulWidget {
  const PasswordRecoveryScreen({super.key});
  @override
  State<PasswordRecoveryScreen> createState() => _PasswordRecoveryScreenState();
}

class _PasswordRecoveryScreenState extends State<PasswordRecoveryScreen> {
  String? feedback;
  bool busy = false;
  Future<void> submit(bool reset) async {
    final values = await accountForm(
      context,
      title: reset ? 'Đặt lại mật khẩu' : 'Khôi phục mật khẩu',
      fields: reset
          ? {'token': 'Mã khôi phục từ email', 'newPassword': 'Mật khẩu mới'}
          : {'email': 'Email tài khoản'},
    );
    if (values == null || !mounted) return;
    setState(() => busy = true);
    try {
      final response = await ApiClient().post<dynamic>(
        '/api/v1/auth/${reset ? 'reset-password' : 'forgot-password'}',
        data: values,
      );
      if (mounted)
        setState(
          () => feedback = response.message ?? 'Yêu cầu đã được tiếp nhận.',
        );
    } catch (e) {
      if (mounted) setState(() => feedback = '$e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Khôi phục tài khoản')),
    body: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          if (busy) const LinearProgressIndicator(),
          if (feedback != null) Text(feedback!),
          FilledButton(
            onPressed: busy ? null : () => submit(false),
            child: const Text('Gửi hướng dẫn qua email'),
          ),
          OutlinedButton(
            onPressed: busy ? null : () => submit(true),
            child: const Text('Tôi đã có mã khôi phục'),
          ),
        ],
      ),
    ),
  );
}
