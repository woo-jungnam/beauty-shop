import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/utils/cart_actions.dart';
import '../../../data/models/product_model.dart';
import '../../widgets/app_product_card.dart';
import '../product_detail/product_detail_screen.dart';

class ImageSearchResultEntry {
  final ProductItem product;
  final double? similarity;
  final bool inStock;

  ImageSearchResultEntry({
    required this.product,
    this.similarity,
    this.inStock = true,
  });
}

class ImageSearchScreen extends StatefulWidget {
  const ImageSearchScreen({super.key});

  @override
  State<ImageSearchScreen> createState() => _ImageSearchScreenState();
}

class _ImageSearchScreenState extends State<ImageSearchScreen> {
  final _urlController = TextEditingController();
  final _api = ApiClient();
  final _picker = ImagePicker();

  bool _loading = false;
  String? _error;
  List<ImageSearchResultEntry>? _results;
  Uint8List? _previewBytes;
  String? _fileName;
  bool _showUrlInput = false;

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _pickImage(ImageSource source) async {
    try {
      final XFile? file = await _picker.pickImage(
        source: source,
        maxWidth: 1200,
        maxHeight: 1200,
        imageQuality: 85,
      );
      if (file == null) return;

      final bytes = await file.readAsBytes();
      if (bytes.length > 5 * 1024 * 1024) {
        setState(() {
          _error = 'Dung lượng ảnh vượt quá 5 MiB. Vui lòng chọn ảnh nhỏ hơn.';
        });
        return;
      }
      _fileName = file.name.isNotEmpty ? file.name : 'product_search.jpg';
      await _searchWithBytes(bytes, _fileName!);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = 'Không thể mở máy ảnh hoặc thư viện ảnh. Vui lòng kiểm tra quyền ứng dụng.';
        });
      }
    }
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
        final parsed = <ImageSearchResultEntry>[];
        for (final item in rawItems) {
          if (item is Map) {
            final rawProd = item['product'] != null
                ? item['product'] as Map<String, dynamic>
                : item as Map<String, dynamic>;
            final product = ProductItem.fromJson(rawProd);
            final similarity = (item['similarity'] is num)
                ? (item['similarity'] as num).toDouble()
                : null;
            final inStock = item['inStock'] is bool
                ? item['inStock'] as bool
                : true;
            parsed.add(ImageSearchResultEntry(
              product: product,
              similarity: similarity,
              inStock: inStock,
            ));
          }
        }

        setState(() {
          _results = parsed;
          if (parsed.isEmpty) {
            _error = 'Không tìm thấy sản phẩm có bao bì tương đồng trong catalog.';
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
          _error = 'Không thể phân tích ảnh hoặc ảnh không đúng định dạng (tối đa 5 MiB).';
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
      _previewBytes = null;
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

  void _clearSelection() {
    setState(() {
      _previewBytes = null;
      _results = null;
      _error = null;
      _urlController.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getCanvas(context),
      appBar: AppBar(
        title: const Text('Tìm Sản Phẩm Bằng Ảnh'),
        backgroundColor: AppColors.primary,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.getSurface(context),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.getBorder(context)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.04),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: AppColors.primaryLight,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(
                          Icons.camera_alt_outlined,
                          color: AppColors.primary,
                          size: 24,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Tìm kiếm hình ảnh thông minh',
                              style: AppTypography.titleMedium.copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Chụp ảnh trực tiếp hoặc chọn ảnh để tìm sản phẩm tương đồng',
                              style: AppTypography.caption,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  if (_previewBytes != null) ...[
                    Stack(
                      alignment: Alignment.topRight,
                      children: [
                        Container(
                          width: double.infinity,
                          height: 190,
                          decoration: BoxDecoration(
                            color: Colors.black12,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
                          ),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(9),
                            child: Image.memory(
                              _previewBytes!,
                              fit: BoxFit.contain,
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.all(8),
                          child: InkWell(
                            onTap: _loading ? null : _clearSelection,
                            child: Container(
                              padding: const EdgeInsets.all(6),
                              decoration: const BoxDecoration(
                                color: Colors.black54,
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(Icons.close, color: Colors.white, size: 16),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                  ],

                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _loading ? null : () => _pickImage(ImageSource.camera),
                          icon: const Icon(Icons.camera_alt, size: 20),
                          label: const Text('Chụp ảnh'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.primary,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _loading ? null : () => _pickImage(ImageSource.gallery),
                          icon: const Icon(Icons.photo_library_outlined, size: 20),
                          label: const Text('Chọn từ máy'),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: AppColors.primary,
                            side: const BorderSide(color: AppColors.primary),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 10),

                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      onPressed: () {
                        setState(() => _showUrlInput = !_showUrlInput);
                      },
                      icon: Icon(
                        _showUrlInput ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                        size: 18,
                        color: AppColors.textMuted,
                      ),
                      label: Text(
                        _showUrlInput ? 'Thu gọn nhập liên kết URL' : 'Hoặc tìm bằng liên kết ảnh (URL)',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textMuted,
                        ),
                      ),
                    ),
                  ),

                  if (_showUrlInput) ...[
                    const SizedBox(height: 4),
                    TextField(
                      controller: _urlController,
                      decoration: InputDecoration(
                        hintText: 'Dán đường dẫn ảnh (URL)...',
                        prefixIcon: const Icon(Icons.link, size: 18),
                        suffixIcon: IconButton(
                          icon: const Icon(Icons.search),
                          onPressed: _loading ? null : () => _fetchAndSearchUrl(_urlController.text),
                        ),
                        isDense: true,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      onSubmitted: (val) => _fetchAndSearchUrl(val),
                    ),
                  ],

                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFEBEE),
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: const Color(0xFFFFCDD2)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.error_outline, size: 16, color: AppColors.dealRed),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _error!,
                              style: const TextStyle(color: AppColors.dealRed, fontSize: 12),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],

                  const SizedBox(height: 10),

                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.getCanvas(context),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Icon(Icons.lightbulb_outline, size: 16, color: AppColors.textMuted),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Mẹo: Chụp rõ nhãn mác bao bì, ánh sáng đầy đủ và góc chính diện để nhận diện chính xác nhất.',
                            style: AppTypography.caption.copyWith(
                              fontSize: 11,
                              color: AppColors.textMuted,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            if (_loading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(32),
                  child: Column(
                    children: [
                      CircularProgressIndicator(color: AppColors.primary),
                      SizedBox(height: 14),
                      Text(
                        'Đang trích xuất đặc trưng và tìm kiếm sản phẩm...',
                        style: TextStyle(fontSize: 13, color: AppColors.textMuted),
                      ),
                    ],
                  ),
                ),
              )
            else if (_results != null && _results!.isNotEmpty) ...[
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Sản phẩm tương đồng (${_results!.length})',
                    style: AppTypography.titleMedium.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppColors.primaryLight,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: const Text(
                      'AI Visual Match',
                      style: TextStyle(
                        fontSize: 11,
                        color: AppColors.primary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _results!.length,
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  crossAxisSpacing: 10,
                  mainAxisSpacing: 10,
                  childAspectRatio: 0.60,
                ),
                itemBuilder: (context, idx) {
                  final item = _results![idx];
                  final simPercent = item.similarity != null
                      ? (item.similarity! * 100).toStringAsFixed(1)
                      : null;
                  final isHighMatch = item.similarity != null && item.similarity! >= 0.85;

                  return Stack(
                    children: [
                      AppProductCard(
                        product: item.product,
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => ProductDetailScreen(productId: item.product.id),
                          ),
                        ),
                        onAddToCart: () => addProductToCart(
                          context,
                          productId: item.product.id,
                          productName: item.product.name,
                          price: item.product.displayPrice,
                          thumbnailUrl: item.product.thumbnailUrl,
                        ),
                      ),
                      if (simPercent != null)
                        Positioned(
                          top: 6,
                          left: 6,
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                            decoration: BoxDecoration(
                              color: isHighMatch
                                  ? const Color(0xFF1B5E20)
                                  : const Color(0xFF0D47A1),
                              borderRadius: BorderRadius.circular(4),
                              boxShadow: const [
                                BoxShadow(
                                  color: Colors.black26,
                                  blurRadius: 3,
                                  offset: Offset(0, 1),
                                ),
                              ],
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.auto_awesome, size: 10, color: Colors.white),
                                const SizedBox(width: 3),
                                Text(
                                  '$simPercent%',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 10,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                    ],
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
