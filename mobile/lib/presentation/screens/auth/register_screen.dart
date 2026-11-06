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
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<AuthProvider>().clearError();
      }
    });
  }

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
      backgroundColor: AppColors.getSurface(context),
      appBar: AppBar(
        title: const Text('Đăng Ký Tài Khoản'),
        backgroundColor: AppColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Tạo tài khoản',
              style: AppTypography.headlineMedium,
            ),
            const SizedBox(height: 4),
            Text(
              'Theo dõi đơn hàng và lịch hẹn của bạn',
              style: AppTypography.bodySmall,
            ),
            const SizedBox(height: 20),

            if (auth.errorMessage != null)
              Container(
                padding: const EdgeInsets.all(10),
                margin: const EdgeInsets.only(bottom: 14),
                decoration: BoxDecoration(
                  color: AppColors.dealRedLight,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: AppColors.dealRedBorder),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.error_outline,
                      color: AppColors.dealRed,
                      size: 18,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        auth.errorMessage!,
                        style: const TextStyle(
                          color: AppColors.dealRed,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

            _buildInputField(
              'Họ và tên *',
              _fullNameController,
              Icons.badge_outlined,
              'Nguyễn Văn A',
            ),
            const SizedBox(height: 12),
            _buildInputField(
              'Email *',
              _emailController,
              Icons.email_outlined,
              'email@domain.com',
            ),
            const SizedBox(height: 12),
            _buildInputField(
              'Số điện thoại *',
              _phoneController,
              Icons.phone_outlined,
              '0901234567',
            ),
            const SizedBox(height: 12),
            _buildInputField(
              'Tên đăng nhập *',
              _usernameController,
              Icons.person_outline,
              'nguyenvana',
            ),
            const SizedBox(height: 12),
            _buildInputField(
              'Mật khẩu *',
              _passwordController,
              Icons.lock_outline,
              'Tối thiểu 6 ký tự',
              obscure: true,
            ),
            const SizedBox(height: 24),

            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: auth.isLoading
                    ? null
                    : () async {
                        final fullName = _fullNameController.text.trim();
                        final email = _emailController.text.trim();
                        final phone = _phoneController.text.trim();
                        final username = _usernameController.text.trim();
                        final password = _passwordController.text;

                        if (fullName.isEmpty || email.isEmpty || phone.isEmpty || username.isEmpty || password.isEmpty) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Vui lòng điền đầy đủ tất cả các trường có dấu *')),
                          );
                          return;
                        }
                        if (password.length < 6) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Mật khẩu phải có ít nhất 6 ký tự')),
                          );
                          return;
                        }

                        final success = await auth.register(
                          fullName: fullName,
                          email: email,
                          phone: phone,
                          username: username,
                          password: password,
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
                  const Text(
                    'Đã có tài khoản? ',
                    style: TextStyle(
                      fontSize: 13,
                      color: AppColors.textMuted,
                    ),
                  ),
                  InkWell(
                    onTap: () {
                      Navigator.pushReplacement(
                        context,
                        MaterialPageRoute(builder: (_) => const LoginScreen()),
                      );
                    },
                    child: Text(
                      'Đăng nhập ngay',
                      style: AppTypography.bodyMedium.copyWith(
                        color: AppColors.primary,
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

  Widget _buildInputField(
    String label,
    TextEditingController controller,
    IconData icon,
    String hint, {
    bool obscure = false,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: AppTypography.bodySmall.copyWith(
            fontWeight: FontWeight.w700,
          ),
        ),
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
