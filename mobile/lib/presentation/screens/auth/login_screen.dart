import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/providers/auth_provider.dart';
import 'register_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({Key? key}) : super(key: key);

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void dispose() {
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
        title: const Text('Đăng Nhập Tài Khoản'),
        backgroundColor: HasakiColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 10),
            Center(
              child: Container(
                width: 60,
                height: 60,
                decoration: BoxDecoration(
                  color: HasakiColors.primaryLight,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(Icons.spa, color: HasakiColors.primary, size: 36),
              ),
            ),
            const SizedBox(height: 16),
            Center(
              child: Text(
                'BEAUTYSHOP',
                style: HasakiTypography.headlineMedium.copyWith(color: HasakiColors.primary, letterSpacing: 1),
              ),
            ),
            Center(
              child: Text(
                'Mỹ phẩm chính hãng & Spa làm đẹp cao cấp',
                style: HasakiTypography.caption.copyWith(color: HasakiColors.textMuted),
              ),
            ),
            const SizedBox(height: 30),

            if (auth.errorMessage != null)
              Container(
                padding: const EdgeInsets.all(10),
                margin: const EdgeInsets.only(bottom: 14),
                decoration: BoxDecoration(
                  color: HasakiColors.dealRedLight,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: HasakiColors.dealRedBorder),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline, color: HasakiColors.dealRed, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(auth.errorMessage!, style: const TextStyle(color: HasakiColors.dealRed, fontSize: 12)),
                    ),
                  ],
                ),
              ),

            Text('Tên đăng nhập / Email:', style: HasakiTypography.bodySmall.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            TextField(
              controller: _usernameController,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.person_outline, size: 20),
                hintText: 'Nhập tên tài khoản hoặc email',
              ),
            ),
            const SizedBox(height: 16),

            Text('Mật khẩu:', style: HasakiTypography.bodySmall.copyWith(fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            TextField(
              controller: _passwordController,
              obscureText: _obscurePassword,
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.lock_outline, size: 20),
                hintText: 'Nhập mật khẩu',
                suffixIcon: IconButton(
                  icon: Icon(_obscurePassword ? Icons.visibility_off : Icons.visibility, size: 18),
                  onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                ),
              ),
            ),
            const SizedBox(height: 24),

            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: auth.isLoading
                    ? null
                    : () async {
                        final success = await auth.login(
                          _usernameController.text.trim(),
                          _passwordController.text,
                        );
                        if (success && mounted) {
                          Navigator.pop(context);
                        }
                      },
                child: auth.isLoading
                    ? const CircularProgressIndicator(color: Colors.white)
                    : const Text('ĐĂNG NHẬP'),
              ),
            ),
            const SizedBox(height: 20),

            Center(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Chưa có tài khoản? ', style: TextStyle(fontSize: 13, color: HasakiColors.textMuted)),
                  InkWell(
                    onTap: () {
                      Navigator.pushReplacement(
                        context,
                        MaterialPageRoute(builder: (_) => const RegisterScreen()),
                      );
                    },
                    child: Text(
                      'Đăng ký ngay',
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
}
