import 'package:flutter/material.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/presentation/widgets/fade_indexed_stack.dart';
import 'home/home_screen.dart';
import 'catalog/category_screen.dart';
import 'deals/flash_deals_screen.dart';
import 'spa/spa_screen.dart';
import 'profile/profile_screen.dart';

class MainWrapperScreen extends StatefulWidget {
  const MainWrapperScreen({Key? key}) : super(key: key);

  @override
  State<MainWrapperScreen> createState() => _MainWrapperScreenState();
}

class _MainWrapperScreenState extends State<MainWrapperScreen> {
  int _currentIndex = 0;
  late final List<Widget> _screens;

  @override
  void initState() {
    super.initState();
    _screens = [
      HomeScreen(onNavigateTab: _onTabChange),
      const CategoryScreen(),
      const SpaScreen(),
      const FlashDealsScreen(),
      const ProfileScreen(),
    ];
  }

  void _onTabChange(int index) {
    if (_currentIndex == index) return;
    setState(() => _currentIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.canvas,
      body: FadeIndexedStack(
        index: _currentIndex,
        children: _screens,
      ),

      // Hasaki 5-Tab Bottom Navigation Bar
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: _onTabChange,
        type: BottomNavigationBarType.fixed,
        backgroundColor: Colors.white,
        selectedItemColor: HasakiColors.primary,
        unselectedItemColor: HasakiColors.textLight,
        selectedFontSize: 11,
        unselectedFontSize: 11,
        selectedLabelStyle: const TextStyle(fontWeight: FontWeight.w800),
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.home_outlined),
            activeIcon: Icon(Icons.home),
            label: 'Trang chủ',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.grid_view_outlined),
            activeIcon: Icon(Icons.grid_view),
            label: 'Danh mục',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.spa_outlined),
            activeIcon: Icon(Icons.spa),
            label: 'Spa & Làm Đẹp',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.flash_on_outlined),
            activeIcon: Icon(Icons.flash_on, color: HasakiColors.dealRed),
            label: 'Deal Giờ Vàng',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.person_outline),
            activeIcon: Icon(Icons.person),
            label: 'Tài khoản',
          ),
        ],
      ),
    );
  }
}
