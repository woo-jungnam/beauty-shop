import 'dart:typed_data';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/utils/cart_actions.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/utils/image_utils.dart';
import '../../../data/models/product_model.dart';
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

class _ImageSearchScreenState extends State<ImageSearchScreen>
    with SingleTickerProviderStateMixin {
  final _urlController = TextEditingController();
  final _api = ApiClient();
  final _picker = ImagePicker();

  late final AnimationController _scanController;
  late final Animation<double> _scanAnimation;

  bool _loading = false;
  String? _error;
  List<ImageSearchResultEntry>? _results;
  Uint8List? _previewBytes;
  String? _fileName;
  bool _showUrlSheet = false;

  @override
  void initState() {
    super.initState();
    _scanController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat(reverse: true);

    _scanAnimation = CurvedAnimation(
      parent: _scanController,
      curve: Curves.easeInOut,
    );
  }

  @override
  void dispose() {
    _scanController.dispose();
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
      _showUrlSheet = false;
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
      _showUrlSheet = false;
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

  void _clearAndRetake() {
    setState(() {
      _previewBytes = null;
      _results = null;
      _error = null;
      _urlController.clear();
      _showUrlSheet = false;
    });
    _pickImage(ImageSource.camera);
  }

  void _resetToScanner() {
    setState(() {
      _previewBytes = null;
      _results = null;
      _error = null;
      _urlController.clear();
      _showUrlSheet = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final hasImage = _previewBytes != null;

    return Scaffold(
      backgroundColor: const Color(0xFF0D0D11),
      body: SafeArea(
        child: hasImage ? _buildResultsView() : _buildScannerView(),
      ),
    );
  }

  // Giao dien Scanner / Camera chup anh BeautyShop
  Widget _buildScannerView() {
    return Column(
      children: [
        // Top Navigation Bar
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.arrow_back_ios_new, color: Colors.white, size: 20),
                style: IconButton.styleFrom(
                  backgroundColor: Colors.white.withValues(alpha: 0.12),
                  padding: const EdgeInsets.all(8),
                ),
              ),
              const Text(
                'Tìm kiếm hình ảnh',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 0.3,
                ),
              ),
              IconButton(
                onPressed: _showTipsDialog,
                icon: const Icon(Icons.help_outline, color: Colors.white, size: 20),
                style: IconButton.styleFrom(
                  backgroundColor: Colors.white.withValues(alpha: 0.12),
                  padding: const EdgeInsets.all(8),
                ),
              ),
            ],
          ),
        ),

        // Center Viewfinder
        Expanded(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  AspectRatio(
                    aspectRatio: 1.0,
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.04),
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          // 4 goc BeautyShop scanner
                          const _BeautyShopCornerBrackets(
                            length: 28,
                            strokeWidth: 3.5,
                            color: Color(0xFFDB5A77),
                          ),

                          // Tia laser quet BeautyShop
                          _BeautyShopScanBeam(animation: _scanAnimation),

                          // Icon huong dan o giua
                          Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.center_focus_strong,
                                size: 54,
                                color: Colors.white.withValues(alpha: 0.35),
                              ),
                              const SizedBox(height: 12),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                                decoration: BoxDecoration(
                                  color: Colors.black.withValues(alpha: 0.5),
                                  borderRadius: BorderRadius.circular(20),
                                  border: Border.all(
                                    color: Colors.white.withValues(alpha: 0.15),
                                    width: 0.8,
                                  ),
                                ),
                                child: const Text(
                                  'Căn chỉnh sản phẩm vào khung hình',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontSize: 12,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),
                  Text(
                    'AI nhận diện trực tiếp chai, tuýp, nhãn mác mỹ phẩm',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.6),
                      fontSize: 13,
                    ),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 14),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                      decoration: BoxDecoration(
                        color: AppColors.dealRed.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: AppColors.dealRed.withValues(alpha: 0.4)),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.error_outline, size: 16, color: AppColors.dealRed),
                          const SizedBox(width: 8),
                          Flexible(
                            child: Text(
                              _error!,
                              style: const TextStyle(color: Colors.white, fontSize: 12),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),

        // Nhap URL Sheet neu mo
        if (_showUrlSheet) ...[
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF1B1B22),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
              border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _urlController,
                    style: const TextStyle(color: Colors.white, fontSize: 13),
                    decoration: InputDecoration(
                      hintText: 'Dán đường dẫn ảnh (URL)...',
                      hintStyle: TextStyle(color: Colors.white.withValues(alpha: 0.4), fontSize: 13),
                      isDense: true,
                      filled: true,
                      fillColor: Colors.black.withValues(alpha: 0.4),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide(color: Colors.white.withValues(alpha: 0.2)),
                      ),
                    ),
                    onSubmitted: (val) => _fetchAndSearchUrl(val),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  onPressed: () => _fetchAndSearchUrl(_urlController.text),
                  icon: const Icon(Icons.search, color: Colors.white),
                  style: IconButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                ),
              ],
            ),
          ),
        ],

        // Bottom Controls Bar
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
          decoration: BoxDecoration(
            color: const Color(0xFF141419),
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              // Nut Thu vien
              _buildBeautyShopSideButton(
                icon: Icons.photo_library_outlined,
                label: 'Thư viện',
                onTap: () => _pickImage(ImageSource.gallery),
              ),

              // Nut Chup Shutter Button
              GestureDetector(
                onTap: () => _pickImage(ImageSource.camera),
                child: Container(
                  width: 76,
                  height: 76,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 3.5),
                  ),
                  padding: const EdgeInsets.all(4),
                  child: Container(
                    decoration: const BoxDecoration(
                      color: AppColors.primary,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.camera_alt,
                      color: Colors.white,
                      size: 32,
                    ),
                  ),
                ),
              ),

              // Nut Dan URL
              _buildBeautyShopSideButton(
                icon: Icons.link,
                label: 'Dán URL',
                onTap: () {
                  setState(() => _showUrlSheet = !_showUrlSheet);
                },
              ),
            ],
          ),
        ),
      ],
    );
  }

  // Giao dien Ket qua phong cach BeautyShop Shop
  Widget _buildResultsView() {
    return Column(
      children: [
        // Top section: Captured photo preview with BeautyShop frame & Retake action
        Container(
          height: 220,
          color: const Color(0xFF0D0D11),
          child: Stack(
            children: [
              // Anh chup duoc can vao khung
              Center(
                child: Container(
                  margin: const EdgeInsets.symmetric(vertical: 12),
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    color: Colors.black,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.white.withValues(alpha: 0.15)),
                  ),
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.memory(
                          _previewBytes!,
                          fit: BoxFit.contain,
                        ),
                      ),
                      // 4 goc BeautyShop overlay tren anh
                      const _BeautyShopCornerBrackets(
                        length: 20,
                        strokeWidth: 2.5,
                        color: Color(0xFFDB5A77),
                      ),
                      // Tia laser scan neu dang load
                      if (_loading) _BeautyShopScanBeam(animation: _scanAnimation),
                    ],
                  ),
                ),
              ),

              // Back button top-left
              Positioned(
                top: 8,
                left: 12,
                child: IconButton(
                  onPressed: _resetToScanner,
                  icon: const Icon(Icons.arrow_back_ios_new, color: Colors.white, size: 18),
                  style: IconButton.styleFrom(
                    backgroundColor: Colors.black54,
                    padding: const EdgeInsets.all(8),
                  ),
                ),
              ),

              // Retake button top-right (pill style)
              Positioned(
                top: 8,
                right: 12,
                child: InkWell(
                  onTap: _clearAndRetake,
                  borderRadius: BorderRadius.circular(20),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.black54,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: Colors.white.withValues(alpha: 0.2)),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.refresh, color: Colors.white, size: 15),
                        SizedBox(width: 4),
                        Text(
                          'Chụp lại',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),

              // Trinh trang thai AI
              if (_loading)
                Positioned(
                  bottom: 8,
                  left: 0,
                  right: 0,
                  child: Center(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.black87,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: AppColors.primary.withValues(alpha: 0.5)),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          SizedBox(
                            width: 12,
                            height: 12,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: AppColors.primary,
                            ),
                          ),
                          SizedBox(width: 8),
                          Text(
                            'AI đang phân tích bao bì sản phẩm...',
                            style: TextStyle(color: Colors.white, fontSize: 11),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),

        // Bottom section: BeautyShop Results Draggable Sheet
        Expanded(
          child: Container(
            decoration: BoxDecoration(
              color: AppColors.getCanvas(context),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.25),
                  blurRadius: 16,
                  offset: const Offset(0, -4),
                ),
              ],
            ),
            child: Column(
              children: [
                // Drag handle pill
                Center(
                  child: Container(
                    margin: const EdgeInsets.only(top: 10, bottom: 8),
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: Colors.grey.withValues(alpha: 0.35),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),

                // Sheet Header
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Text(
                            _results != null
                                ? 'Sản phẩm tìm thấy (${_results!.length})'
                                : 'Kết quả tìm kiếm',
                            style: AppTypography.titleMedium.copyWith(
                              fontWeight: FontWeight.bold,
                              fontSize: 15,
                            ),
                          ),
                        ],
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.primaryLight,
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(color: AppColors.primary.withValues(alpha: 0.2)),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.auto_awesome, size: 12, color: AppColors.primary),
                            SizedBox(width: 4),
                            Text(
                              'BeautyShop Visual AI',
                              style: TextStyle(
                                fontSize: 10,
                                color: AppColors.primary,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),

                const Divider(height: 1, thickness: 0.5),

                // Results Body
                Expanded(
                  child: _buildResultsBody(),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildResultsBody() {
    if (_loading) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(color: AppColors.primary),
            const SizedBox(height: 16),
            Text(
              'Đang so khớp vector đặc trưng thị giác...',
              style: TextStyle(fontSize: 13, color: AppColors.getTextMain(context).withValues(alpha: 0.7)),
            ),
          ],
        ),
      );
    }

    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: const BoxDecoration(
                  color: Color(0xFFFFEBEE),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.image_not_supported_outlined, size: 40, color: AppColors.dealRed),
              ),
              const SizedBox(height: 14),
              Text(
                _error!,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 13, color: AppColors.dealRed, fontWeight: FontWeight.w500),
              ),
              const SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: _clearAndRetake,
                icon: const Icon(Icons.camera_alt, size: 16),
                label: const Text('Chụp lại góc khác'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primary,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (_results == null || _results!.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.search_off, size: 48, color: Colors.grey),
            const SizedBox(height: 12),
            const Text(
              'Không tìm thấy sản phẩm trùng khớp',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
            ),
            const SizedBox(height: 14),
            ElevatedButton(
              onPressed: _clearAndRetake,
              style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
              child: const Text('Chụp lại ảnh', style: TextStyle(color: Colors.white)),
            ),
          ],
        ),
      );
    }

    // Dieu chinh childAspectRatio tu 0.62 xuong 0.55 de loai bo hoan toan loi overflow pixel o duoi the
    return GridView.builder(
      padding: const EdgeInsets.all(10),
      itemCount: _results!.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: 10,
        mainAxisSpacing: 10,
        childAspectRatio: 0.55,
      ),
      itemBuilder: (context, idx) {
        final item = _results![idx];
        return _buildBeautyShopProductCard(item);
      },
    );
  }

  // The san pham phong cach BeautyShop Shop
  Widget _buildBeautyShopProductCard(ImageSearchResultEntry item) {
    final p = item.product;
    final similarity = item.similarity;
    final simScore = similarity != null ? (similarity * 100).toStringAsFixed(1) : null;
    final isHighMatch = similarity != null && similarity >= 0.85;
    final isMediumMatch = similarity != null && similarity >= 0.70;

    final displayPrice = p.salePrice ?? p.basePrice;
    final hasDiscount = p.salePrice != null && p.salePrice! < p.basePrice;

    return InkWell(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => ProductDetailScreen(productId: p.id),
          ),
        );
      },
      borderRadius: BorderRadius.circular(10),
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.getSurface(context),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppColors.getBorder(context)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 6,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Anh san pham + Badge tuong dong
            Stack(
              children: [
                ClipRRect(
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(9)),
                  child: AspectRatio(
                    aspectRatio: 1.0,
                    child: () {
                      final url = ImageUtils.resolveUrl(p.thumbnailUrl);
                      if (url != null && url.isNotEmpty) {
                        return CachedNetworkImage(
                          imageUrl: url,
                          fit: BoxFit.cover,
                          placeholder: (context, url) => Container(color: AppColors.canvas),
                          errorWidget: (context, url, error) => Container(
                            color: AppColors.canvas,
                            child: const Icon(Icons.spa_outlined, color: Colors.grey, size: 28),
                          ),
                        );
                      }
                      return Container(
                        color: AppColors.canvas,
                        child: const Icon(Icons.spa_outlined, color: Colors.grey, size: 28),
                      );
                    }(),
                  ),
                ),

                // Similarity Match Badge
                if (simScore != null)
                  Positioned(
                    top: 6,
                    left: 6,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                      decoration: BoxDecoration(
                        color: isHighMatch
                            ? const Color(0xFF15803D)
                            : (isMediumMatch ? const Color(0xFF8C2A47) : const Color(0xFF1E3A8A)),
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
                          const Icon(Icons.auto_awesome, size: 9, color: Colors.white),
                          const SizedBox(width: 3),
                          Text(
                            '$simScore% ${isHighMatch ? "Khớp cao" : "Tương đồng"}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 9,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),

            // Thong tin san pham BeautyShop
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (p.brandName != null && p.brandName!.isNotEmpty)
                          Text(
                            p.brandName!.toUpperCase(),
                            style: TextStyle(
                              fontSize: 9,
                              fontWeight: FontWeight.w600,
                              color: AppColors.primary.withValues(alpha: 0.9),
                              letterSpacing: 0.3,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        const SizedBox(height: 2),
                        Text(
                          p.name,
                          style: AppTypography.bodySmall.copyWith(
                            fontWeight: FontWeight.w600,
                            fontSize: 11.5,
                            height: 1.2,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),

                    // Gia ca va nut mua BeautyShop
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                AppFormatters.formatCurrency(displayPrice),
                                style: const TextStyle(
                                  color: AppColors.dealRed,
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.bold,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              if (hasDiscount)
                                Text(
                                  AppFormatters.formatCurrency(p.basePrice),
                                  style: const TextStyle(
                                    color: Colors.grey,
                                    fontSize: 9.5,
                                    decoration: TextDecoration.lineThrough,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 4),
                        // Nut gio hang nhanh BeautyShop
                        InkWell(
                          onTap: () {
                            addProductToCart(
                              context,
                              productId: p.id,
                              productName: p.name,
                              price: displayPrice,
                              thumbnailUrl: p.thumbnailUrl,
                            );
                          },
                          borderRadius: BorderRadius.circular(14),
                          child: Container(
                            padding: const EdgeInsets.all(5),
                            decoration: BoxDecoration(
                              color: AppColors.primaryLight,
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: AppColors.primary.withValues(alpha: 0.3),
                                width: 0.8,
                              ),
                            ),
                            child: const Icon(
                              Icons.shopping_bag_outlined,
                              size: 14,
                              color: AppColors.primary,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBeautyShopSideButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: Colors.white.withValues(alpha: 0.2),
                width: 1,
              ),
            ),
            child: Icon(icon, color: Colors.white, size: 22),
          ),
          const SizedBox(height: 6),
          Text(
            label,
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.75),
              fontSize: 11,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

  void _showTipsDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E26),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text(
          'Mẹo tìm kiếm chính xác',
          style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
        ),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _TipRow(
              icon: Icons.light_mode_outlined,
              text: 'Đảm bảo đủ ánh sáng, tránh bóng đổ tối che khuất nhãn chai.',
            ),
            SizedBox(height: 10),
            _TipRow(
              icon: Icons.crop_free,
              text: 'Căn góc chính diện để nhìn rõ tên thương hiệu và công dụng sản phẩm.',
            ),
            SizedBox(height: 10),
            _TipRow(
              icon: Icons.filter_center_focus,
              text: 'Chỉ chụp 1 sản phẩm chính trong mỗi lần tìm kiếm.',
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Đã hiểu', style: TextStyle(color: AppColors.primary)),
          ),
        ],
      ),
    );
  }
}

class _TipRow extends StatelessWidget {
  final IconData icon;
  final String text;

  const _TipRow({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: AppColors.primary, size: 18),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            text,
            style: TextStyle(color: Colors.white.withValues(alpha: 0.8), fontSize: 12),
          ),
        ),
      ],
    );
  }
}

// 4 goc BeautyShop Scanner
class _BeautyShopCornerBrackets extends StatelessWidget {
  final double length;
  final double strokeWidth;
  final Color color;

  const _BeautyShopCornerBrackets({
    this.length = 24.0,
    this.strokeWidth = 3.5,
    this.color = const Color(0xFFDB5A77),
  });

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _BeautyShopCornerPainter(
        length: length,
        strokeWidth: strokeWidth,
        color: color,
      ),
      size: Size.infinite,
    );
  }
}

class _BeautyShopCornerPainter extends CustomPainter {
  final double length;
  final double strokeWidth;
  final Color color;

  _BeautyShopCornerPainter({
    required this.length,
    required this.strokeWidth,
    required this.color,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = strokeWidth
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final w = size.width;
    final h = size.height;
    final l = length;

    // Top-Left
    canvas.drawLine(const Offset(0, 0), Offset(l, 0), paint);
    canvas.drawLine(const Offset(0, 0), Offset(0, l), paint);

    // Top-Right
    canvas.drawLine(Offset(w, 0), Offset(w - l, 0), paint);
    canvas.drawLine(Offset(w, 0), Offset(w, l), paint);

    // Bottom-Left
    canvas.drawLine(Offset(0, h), Offset(l, h), paint);
    canvas.drawLine(Offset(0, h), Offset(0, h - l), paint);

    // Bottom-Right
    canvas.drawLine(Offset(w, h), Offset(w - l, h), paint);
    canvas.drawLine(Offset(w, h), Offset(w, h - l), paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

// Tia laser quet BeautyShop Scan Beam
class _BeautyShopScanBeam extends StatelessWidget {
  final Animation<double> animation;

  const _BeautyShopScanBeam({required this.animation});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      builder: (context, child) {
        return Align(
          alignment: Alignment(0, (animation.value * 2) - 1),
          child: Container(
            height: 2.5,
            width: double.infinity,
            margin: const EdgeInsets.symmetric(horizontal: 8),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [
                  Colors.transparent,
                  Color(0xFFDB5A77),
                  Colors.white,
                  Color(0xFFDB5A77),
                  Colors.transparent,
                ],
                stops: [0.0, 0.25, 0.5, 0.75, 1.0],
              ),
              boxShadow: [
                BoxShadow(
                  color: const Color(0xFFDB5A77).withValues(alpha: 0.8),
                  blurRadius: 8,
                  spreadRadius: 1.5,
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
