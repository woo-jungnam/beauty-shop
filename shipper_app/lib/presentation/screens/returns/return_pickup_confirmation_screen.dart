import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_shipper/core/theme/app_colors.dart';
import 'package:beautyshop_shipper/core/utils/shipper_formatters.dart';
import 'package:beautyshop_shipper/data/models/shipper_return_model.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';

class ReturnPickupConfirmationScreen extends StatefulWidget {
  final ShipperReturnModel returnRequest;

  const ReturnPickupConfirmationScreen({
    super.key,
    required this.returnRequest,
  });

  @override
  State<ReturnPickupConfirmationScreen> createState() =>
      _ReturnPickupConfirmationScreenState();
}

class _ReturnPickupConfirmationScreenState
    extends State<ReturnPickupConfirmationScreen> {
  final _notesController = TextEditingController();
  final ImagePicker _picker = ImagePicker();
  XFile? _selectedImage;
  bool _itemsVerified = false;
  bool _isSubmitting = false;

  bool get _canSubmit =>
      !_isSubmitting && _itemsVerified && _selectedImage != null;

  String? get _pendingRequirementHint {
    if (_isSubmitting) return null;
    if (!_itemsVerified && _selectedImage == null) {
      return 'Cần xác nhận đủ sản phẩm thu hồi và chụp ảnh bằng chứng';
    }
    if (!_itemsVerified) {
      return 'Vui lòng tích xác nhận đã kiểm đủ sản phẩm và tình trạng hàng hoàn';
    }
    if (_selectedImage == null) {
      return 'Vui lòng chụp hoặc chọn ảnh xác nhận lấy hàng hoàn';
    }
    return null;
  }

  @override
  void dispose() {
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _pickImage(ImageSource source) async {
    try {
      final picked = await _picker.pickImage(
        source: source,
        maxWidth: 1600,
        maxHeight: 1600,
        imageQuality: 85,
      );
      if (mounted && picked != null) {
        setState(() => _selectedImage = picked);
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Không thể chụp hoặc chọn ảnh: $e'),
          backgroundColor: AppColors.danger,
        ),
      );
    }
  }

  Future<void> _submitConfirmation() async {
    final image = _selectedImage;
    if (image == null || !_itemsVerified) return;

    setState(() => _isSubmitting = true);
    final provider = context.read<ShipperProvider>();
    final success = await provider.completeReturnPickup(
      returnId: widget.returnRequest.id,
      imageFile: image,
      notes: _notesController.text,
    );
    if (!mounted) return;
    setState(() => _isSubmitting = false);

    if (success) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: [
              const Icon(Icons.check_circle, color: Colors.white),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Đã thu hồi hàng hoàn cho đơn #${widget.returnRequest.orderNumber}!',
                ),
              ),
            ],
          ),
          backgroundColor: AppColors.success,
          behavior: SnackBarBehavior.floating,
        ),
      );
      Navigator.pop(context, true);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            provider.errorMessage ?? 'Xác nhận lấy hàng hoàn thất bại',
          ),
          backgroundColor: AppColors.danger,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final ret = widget.returnRequest;
    final style = ShipperStatusStyle.fromReturn(ret);
    final hint = _pendingRequirementHint;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text(
          'Xác nhận lấy hàng hoàn',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        elevation: 0,
      ),
      bottomNavigationBar: ColoredBox(
        color: AppColors.card,
        child: SafeArea(
          top: false,
          minimum: const EdgeInsets.fromLTRB(16, 10, 16, 12),
          child: Center(
            heightFactor: 1,
            child: ConstrainedBox(
              constraints: const BoxConstraints(
                maxWidth: ShipperLayout.maxContentWidth,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (hint != null) ...[
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(
                          Icons.info_outline_rounded,
                          size: 15,
                          color: AppColors.warning,
                        ),
                        const SizedBox(width: 6),
                        Flexible(
                          child: Text(
                            hint,
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: AppColors.warning,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                  ],
                  ElevatedButton.icon(
                    onPressed: _canSubmit ? _submitConfirmation : null,
                    icon: _isSubmitting
                        ? const SizedBox.shrink()
                        : const Icon(Icons.verified_rounded),
                    label: _isSubmitting
                        ? const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  color: Colors.white,
                                  strokeWidth: 2,
                                ),
                              ),
                              SizedBox(width: 12),
                              Flexible(child: Text('Đang tải ảnh lên…')),
                            ],
                          )
                        : const Text(
                            'Xác nhận đã lấy hàng hoàn',
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.success,
                      foregroundColor: Colors.white,
                      disabledBackgroundColor: AppColors.surfaceMuted,
                      disabledForegroundColor: AppColors.textMuted,
                      minimumSize: const Size(0, 48),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 12,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(
                          ShipperLayout.innerRadius,
                        ),
                      ),
                      elevation: 0,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      body: SingleChildScrollView(
        padding: ShipperLayout.pagePadding,
        child: ShipperPageContainer(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                color: AppColors.card,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
                  side: const BorderSide(color: AppColors.border),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Wrap(
                        alignment: WrapAlignment.spaceBetween,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        spacing: 8,
                        runSpacing: 6,
                        children: [
                          Text(
                            'Thu hồi #${ret.id} · #${ret.orderNumber}',
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                              color: AppColors.primary,
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: style.background,
                              borderRadius: BorderRadius.circular(20),
                            ),
                            child: Text(
                              style.label,
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                                color: style.foreground,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const Divider(height: 20),
                      Text(
                        '${ret.customerName}${ret.customerPhone.isNotEmpty ? ' · ${ret.customerPhone}' : ''}',
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          color: AppColors.textMain,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        ret.displayPickupAddress,
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.textMuted,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppColors.infoLight,
                          borderRadius: BorderRadius.circular(
                            ShipperLayout.innerRadius,
                          ),
                        ),
                        child: const Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(
                              Icons.info_outline_rounded,
                              size: 18,
                              color: AppColors.info,
                            ),
                            SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Không chi hoàn tiền mặt cho khách tại cửa. Tiền hoàn do BeautyShop chuyển khoản hoặc cấp Voucher sau khi kho kiểm nhận.',
                                style: TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w500,
                                  color: AppColors.info,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              Card(
                color: AppColors.card,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
                  side: const BorderSide(color: AppColors.border),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Sản phẩm cần thu hồi',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 8),
                      if (ret.items.isNotEmpty)
                        for (final item in ret.items)
                          Padding(
                            padding: const EdgeInsets.only(top: 6),
                            child: Text(
                              '• ${item.productName}${item.variantName?.isNotEmpty == true ? ' (${item.variantName})' : ''} — Thu hồi SL: ${item.returnQuantity}/${item.purchasedQuantity}',
                              style: const TextStyle(
                                fontSize: 13.5,
                                color: AppColors.textMain,
                              ),
                            ),
                          )
                      else
                        for (final summary in ret.itemsSummary)
                          Padding(
                            padding: const EdgeInsets.only(top: 6),
                            child: Text('• $summary'),
                          ),
                      const SizedBox(height: 12),
                      Material(
                        color: _itemsVerified
                            ? AppColors.successLight
                            : AppColors.warningLight,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(
                            ShipperLayout.innerRadius,
                          ),
                          side: BorderSide(
                            color: _itemsVerified
                                ? AppColors.success
                                : AppColors.warning,
                            width: 1.2,
                          ),
                        ),
                        clipBehavior: Clip.antiAlias,
                        child: CheckboxListTile(
                          controlAffinity: ListTileControlAffinity.leading,
                          activeColor: AppColors.success,
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 2,
                          ),
                          title: const Text(
                            'Đã kiểm đủ sản phẩm, số lượng & đóng gói niêm phong',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 13.5,
                            ),
                          ),
                          subtitle: const Text(
                            'Bắt buộc xác nhận trước khi nhận hàng hoàn về kho',
                            style: TextStyle(
                              fontSize: 12,
                              color: AppColors.textMuted,
                            ),
                          ),
                          value: _itemsVerified,
                          onChanged: _isSubmitting
                              ? null
                              : (value) => setState(
                                  () => _itemsVerified = value ?? false,
                                ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              Card(
                color: AppColors.card,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
                  side: const BorderSide(color: AppColors.border),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Row(
                        children: [
                          Icon(
                            Icons.camera_alt_outlined,
                            color: AppColors.primary,
                            size: 20,
                          ),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Ảnh xác nhận lấy hàng hoàn *',
                              style: TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        'Chụp rõ tình trạng sản phẩm / hộp hàng khi nhận từ khách để đối soát với kho.',
                        style: TextStyle(
                          fontSize: 13,
                          color: AppColors.textMuted,
                        ),
                      ),
                      const SizedBox(height: 14),
                      if (_selectedImage != null) ...[
                        Stack(
                          children: [
                            ClipRRect(
                              borderRadius: BorderRadius.circular(
                                ShipperLayout.innerRadius,
                              ),
                              child: Container(
                                color: AppColors.surfaceMuted,
                                child: kIsWeb
                                    ? Image.network(
                                        _selectedImage!.path,
                                        height: 220,
                                        width: double.infinity,
                                        fit: BoxFit.contain,
                                      )
                                    : Image.file(
                                        File(_selectedImage!.path),
                                        height: 220,
                                        width: double.infinity,
                                        fit: BoxFit.contain,
                                      ),
                              ),
                            ),
                            Positioned(
                              top: 8,
                              right: 8,
                              child: IconButton.filled(
                                tooltip: 'Xóa ảnh',
                                style: IconButton.styleFrom(
                                  backgroundColor: Colors.black54,
                                  foregroundColor: Colors.white,
                                ),
                                onPressed: () =>
                                    setState(() => _selectedImage = null),
                                icon: const Icon(Icons.close, size: 18),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                      ] else ...[
                        Material(
                          color: AppColors.primarySurface.withValues(
                            alpha: 0.35,
                          ),
                          borderRadius: BorderRadius.circular(
                            ShipperLayout.innerRadius,
                          ),
                          child: InkWell(
                            borderRadius: BorderRadius.circular(
                              ShipperLayout.innerRadius,
                            ),
                            onTap: () => _pickImage(ImageSource.camera),
                            child: Container(
                              width: double.infinity,
                              padding: const EdgeInsets.symmetric(
                                vertical: 24,
                                horizontal: 16,
                              ),
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(
                                  ShipperLayout.innerRadius,
                                ),
                                border: Border.all(
                                  color: AppColors.primary.withValues(
                                    alpha: 0.35,
                                  ),
                                ),
                              ),
                              child: const Column(
                                children: [
                                  Icon(
                                    Icons.add_a_photo_outlined,
                                    size: 32,
                                    color: AppColors.primary,
                                  ),
                                  SizedBox(height: 8),
                                  Text(
                                    'Nhấn để chụp ảnh hàng hoàn',
                                    textAlign: TextAlign.center,
                                    style: TextStyle(
                                      fontWeight: FontWeight.w600,
                                      color: AppColors.primaryDark,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                      ],
                      Row(
                        children: [
                          Expanded(
                            child: OutlinedButton.icon(
                              onPressed: () => _pickImage(ImageSource.camera),
                              icon: const Icon(Icons.photo_camera, size: 18),
                              label: const Text('Chụp ảnh'),
                              style: OutlinedButton.styleFrom(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 14,
                                  vertical: 12,
                                ),
                                foregroundColor: AppColors.primary,
                                side: const BorderSide(
                                  color: AppColors.primary,
                                ),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(10),
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: OutlinedButton.icon(
                              onPressed: () => _pickImage(ImageSource.gallery),
                              icon: const Icon(Icons.photo_library, size: 18),
                              label: const Text('Chọn thư viện'),
                              style: OutlinedButton.styleFrom(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 14,
                                  vertical: 12,
                                ),
                                foregroundColor: AppColors.textMain,
                                side: const BorderSide(color: AppColors.border),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(10),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              Card(
                color: AppColors.card,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(ShipperLayout.cardRadius),
                  side: const BorderSide(color: AppColors.border),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: TextField(
                    controller: _notesController,
                    maxLength: 500,
                    maxLines: 3,
                    decoration: InputDecoration(
                      labelText: 'Ghi chú tình trạng hàng lúc thu hồi',
                      hintText:
                          'Ví dụ: Còn nguyên seal hộp ngoài, chai serum hơi trầy nắp…',
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                      filled: true,
                      fillColor: AppColors.background,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

