import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'login_screen.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({Key? key}) : super(key: key);

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _fullNameController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();

  @override
  void dispose() {
    _fullNameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();

    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: const Text('Đăng Ký Tài Khoản'),
        backgroundColor: HasakiColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Tạo tài khoản BeautyClub', style: HasakiTypography.headlineMedium),
            const SizedBox(height: 4),
            Text('Nhận ngay voucher 100K và tích điểm đổi quà', style: HasakiTypography.bodySmall),
            const SizedBox(height: 20),

            _buildInputField('Họ và tên *', _fullNameController, Icons.badge_outlined, 'Nguyễn Văn A'),
            const SizedBox(height: 12),
            _buildInputField('Email *', _emailController, Icons.email_outlined, 'email@domain.com'),
            const SizedBox(height: 12),
            _buildInputField('Số điện thoại *', _phoneController, Icons.phone_outlined, '0901234567'),
            const SizedBox(height: 12),
            _buildInputField('Tên đăng nhập *', _usernameController, Icons.person_outline, 'nguyenvana'),
            const SizedBox(height: 12),
            _buildInputField('Mật khẩu *', _passwordController, Icons.lock_outline, 'Tối thiểu 6 ký tự', obscure: true),
            const SizedBox(height: 24),

            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: auth.isLoading
                    ? null
                    : () async {
                        final success = await auth.register(
                          fullName: _fullNameController.text.trim(),
                          email: _emailController.text.trim(),
                          phone: _phoneController.text.trim(),
                          username: _usernameController.text.trim(),
                          password: _passwordController.text,
                        );
                        if (success && mounted) {
                          Navigator.pop(context);
                        }
                      },
                child: auth.isLoading
                    ? const CircularProgressIndicator(color: Colors.white)
                    : const Text('ĐĂNG KÝ TÀI KHOẢN'),
              ),
            ),
            const SizedBox(height: 20),

            Center(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Đã có tài khoản? ', style: TextStyle(fontSize: 13, color: HasakiColors.textMuted)),
                  InkWell(
                    onTap: () {
                      Navigator.pushReplacement(
                        context,
                        MaterialPageRoute(builder: (_) => const LoginScreen()),
                      );
                    },
                    child: Text(
                      'Đăng nhập ngay',
                      style: HasakiTypography.bodyMedium.copyWith(
                        color: HasakiColors.primary,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInputField(String label, TextEditingController controller, IconData icon, String hint, {bool obscure = false}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: HasakiTypography.bodySmall.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        TextField(
          controller: controller,
          obscureText: obscure,
          decoration: InputDecoration(
            prefixIcon: Icon(icon, size: 20),
            hintText: hint,
          ),
        ),
      ],
    );
  }
}
