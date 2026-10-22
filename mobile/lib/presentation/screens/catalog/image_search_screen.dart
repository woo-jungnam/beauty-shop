import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/utils/cart_actions.dart';
import '../../../data/models/product_model.dart';
import '../../widgets/app_product_card.dart';
import '../../widgets/catalog_state.dart';
import '../product_detail/product_detail_screen.dart';

class ImageSearchScreen extends StatefulWidget {
  const ImageSearchScreen({super.key});

  @override
  State<ImageSearchScreen> createState() => _ImageSearchScreenState();
}

class _ImageSearchScreenState extends State<ImageSearchScreen> {
  final _urlController = TextEditingController();
  final _api = ApiClient();
  bool _loading = false;
  String? _error;
  List<ProductItem>? _results;
  Uint8List? _previewBytes;
  String? _previewUrl;

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _searchWithBytes(Uint8List bytes, String filename) async {
    setState(() {
      _loading = true;
      _error = null;
      _results = null;
      _previewBytes = bytes;
    });

    try {
      final formData = FormData.fromMap({
        'image': MultipartFile.fromBytes(
          bytes,
          filename: filename.isEmpty ? 'search.jpg' : filename,
        ),
      });

      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.searchByImage,
        data: formData,
        fromJsonT: (json) => Map<String, dynamic>.from(json as Map),
      );

      if (!mounted) return;

      if (response.isSuccess && response.data != null) {
        final data = response.data!;
        final rawItems = data['items'] as List<dynamic>? ?? [];
        final parsed = <ProductItem>[];
        for (final item in rawItems) {
          if (item is Map && item['product'] != null) {
            parsed.add(ProductItem.fromJson(item['product'] as Map<String, dynamic>));
          } else if (item is Map) {
            parsed.add(ProductItem.fromJson(item as Map<String, dynamic>));
          }
        }

        setState(() {
          _results = parsed;
          if (parsed.isEmpty) {
            _error = 'Không tìm thấy sản phẩm có bao bì tương đồng trong cửa hàng.';
          }
        });
      } else {
        setState(() {
          _error = response.message ?? 'Không thể tìm kiếm bằng ảnh.';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = 'Không thể phân tích ảnh hoặc ảnh không đúng định dạng JPEG/PNG/WebP (tối đa 5 MiB).';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _fetchAndSearchUrl(String imageUrl) async {
    final clean = imageUrl.trim();
    if (clean.isEmpty) {
      setState(() => _error = 'Vui lòng nhập đường dẫn ảnh.');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _previewUrl = clean;
    });

    try {
      final res = await http.get(Uri.parse(clean));
      if (res.statusCode == 200 && res.bodyBytes.isNotEmpty) {
        if (res.bodyBytes.length > 5 * 1024 * 1024) {
          setState(() {
            _loading = false;
            _error = 'Dung lượng ảnh vượt quá giới hạn 5 MiB.';
          });
          return;
        }
        await _searchWithBytes(res.bodyBytes, 'url_image.jpg');
      } else {
        setState(() {
          _loading = false;
          _error = 'Không thể tải ảnh từ đường dẫn đã nhập.';
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = 'Đường dẫn ảnh không hợp lệ hoặc máy chủ từ chối kết nối.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Tìm Sản Phẩm Bằng Ảnh'),
        backgroundColor: AppColors.primary,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.getSurface(context),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: AppColors.primaryLight,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Icon(
                          Icons.camera_alt,
                          color: AppColors.primary,
                          size: 24,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Tìm kiếm bằng hình ảnh',
                              style: AppTypography.titleMedium,
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Nhập đường dẫn ảnh sản phẩm để tìm kiếm sản phẩm trong cửa hàng',
                              style: AppTypography.caption,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: _urlController,
                    decoration: InputDecoration(
                      hintText: 'Dán đường dẫn ảnh (URL) sản phẩm...',
                      prefixIcon: const Icon(Icons.link, size: 20),
                      suffixIcon: IconButton(
                        icon: const Icon(Icons.search),
                        onPressed: _loading ? null : () => _fetchAndSearchUrl(_urlController.text),
                      ),
                    ),
                    onSubmitted: (val) => _fetchAndSearchUrl(val),
                  ),
                  if (_previewBytes != null) ...[
                    const SizedBox(height: 12),
                    Center(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.memory(
                          _previewBytes!,
                          height: 140,
                          fit: BoxFit.cover,
                        ),
                      ),
                    ),
                  ],
                  if (_error != null) ...[
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        const Icon(Icons.error_outline, size: 16, color: AppColors.dealRed),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            _error!,
                            style: const TextStyle(color: AppColors.dealRed, fontSize: 12),
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 16),
            if (_loading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(32),
                  child: Column(
                    children: [
                      CircularProgressIndicator(color: AppColors.primary),
                      SizedBox(height: 12),
                      Text('Đang tìm kiếm sản phẩm phù hợp...'),
                    ],
                  ),
                ),
              )
            else if (_results != null && _results!.isNotEmpty) ...[
              Text(
                'Sản phẩm tìm thấy (${_results!.length}):',
                style: AppTypography.titleMedium,
              ),
              const SizedBox(height: 10),
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _results!.length,
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  crossAxisSpacing: 10,
                  mainAxisSpacing: 10,
                  childAspectRatio: 0.65,
                ),
                itemBuilder: (context, idx) {
                  final p = _results![idx];
                  return AppProductCard(
                    product: p,
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => ProductDetailScreen(productId: p.id),
                      ),
                    ),
                    onAddToCart: () => addProductToCart(
                      context,
                      productId: p.id,
                      productName: p.name,
                      price: p.displayPrice,
                      thumbnailUrl: p.thumbnailUrl,
                    ),
                  );
                },
              ),
            ],
          ],
        ),
      ),
    );
  }
}
