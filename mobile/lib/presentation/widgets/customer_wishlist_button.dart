import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../core/network/api_client.dart';
import '../../providers/auth_provider.dart';
import '../screens/auth/login_screen.dart';

class CustomerWishlistButton extends StatefulWidget {
  const CustomerWishlistButton({super.key, required this.productId});
  final int productId;
  @override
  State<CustomerWishlistButton> createState() => _CustomerWishlistButtonState();
}

class _CustomerWishlistButtonState extends State<CustomerWishlistButton> {
  static final changed = ValueNotifier<int>(0);
  static String? owner;
  static Set<int> saved = {};
  static Future<void>? pending;
  bool busy = false;
  @override
  void initState() {
    super.initState();
    changed.addListener(refresh);
  }

  @override
  void dispose() {
    changed.removeListener(refresh);
    super.dispose();
  }

  void refresh() {
    if (mounted) setState(() {});
  }

  Future<void> ensureLoaded(String? id) async {
    if (id != owner) {
      owner = id;
      saved = {};
      pending = null;
    }
    if (id == null) return;
    pending ??= ApiClient()
        .get<dynamic>('/api/v1/users/wishlist')
        .then((response) {
          if (owner != id) return;
          saved = (response.data as List? ?? [])
              .whereType<Map>()
              .map((row) => (row['id'] as num).toInt())
              .toSet();
          changed.value++;
        })
        .catchError((Object error) {
          pending = null;
        });
    await pending;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final auth = context.watch<AuthProvider?>();
    ensureLoaded(
      auth?.isAuthenticated == true ? '${auth?.currentUser?.id}' : null,
    );
  }

  Future<void> toggle() async {
    final auth = context.read<AuthProvider?>();
    if (auth?.isAuthenticated != true) {
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const LoginScreen()),
      );
      return;
    }
    if (busy) return;
    setState(() => busy = true);
    final id = '${auth?.currentUser?.id}';
    try {
      await ensureLoaded(id);
      final exists = saved.contains(widget.productId);
      if (exists) {
        await ApiClient().delete<dynamic>(
          '/api/v1/users/wishlist/${widget.productId}',
        );
      } else {
        await ApiClient().put<dynamic>(
          '/api/v1/users/wishlist/${widget.productId}',
        );
      }
      if (owner == id) {
        if (exists) {
          saved.remove(widget.productId);
        } else {
          saved.add(widget.productId);
        }
        changed.value++;
      }
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: saved.contains(widget.productId)
        ? 'Bỏ yêu thích'
        : 'Lưu yêu thích',
    onPressed: busy ? null : toggle,
    icon: Icon(
      saved.contains(widget.productId) ? Icons.favorite : Icons.favorite_border,
      color: Colors.pink,
    ),
  );
}
