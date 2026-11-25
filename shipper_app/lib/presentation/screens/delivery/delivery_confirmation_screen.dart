import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import 'package:beautyshop_shipper/core/theme/app_colors.dart';
import 'package:beautyshop_shipper/core/utils/shipper_formatters.dart';
import 'package:beautyshop_shipper/data/models/shipper_order_model.dart';
import 'package:beautyshop_shipper/providers/shipper_provider.dart';
import 'shipper_order_detail_screen.dart';

class DeliveryConfirmationScreen extends StatefulWidget {
  final ShipperOrderModel order;

  const DeliveryConfirmationScreen({super.key, required this.order});

  @override
  State<DeliveryConfirmationScreen> createState() =>
      _DeliveryConfirmationScreenState();
}

class _DeliveryConfirmationScreenState
    extends State<DeliveryConfirmationScreen> {
  final _notesController = TextEditingController();
  final ImagePicker _picker = ImagePicker();
  XFile? _selectedImage;
  bool _isSubmitting = false;
  bool _codConfirmed = false;

  bool get _requiresCodConfirmation => widget.order.codAmount > 0;
  bool get _canSubmit =>
      !_isSubmitting &&
      _selectedImage != null &&
      (!_requiresCodConfirmation || _codConfirmed);

  String? get _pendingRequirementHint {
    if (_isSubmitting) return null;
    final missingCod = _requiresCodConfirmation && !_codConfirmed;
    final missingPhoto = _selectedImage == null;
    if (missingCod && missingPhoto) {
      return 'Cần tích xác nhận thu COD và chụp ảnh bằng chứng';
    }
    if (missingCod) {
      return 'Vui lòng tích xác nhận đã thu đủ tiền mặt (COD)';
    }
    if (missingPhoto) {
      return 'Vui lòng chụp hoặc chọn ảnh bằng chứng giao hàng';
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
        setState(() {
          _selectedImage = picked;
        });
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
    if (image == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Chụp hoặc chọn ảnh bằng chứng trước khi xác nhận.'),
        ),
      );
      return;
    }

    setState(() => _isSubmitting = true);

    final shipperProvider = context.read<ShipperProvider>();
    final success = await shipperProvider.completeDelivery(
      orderId: widget.order.id,
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
                  'Đơn #${widget.order.orderNumber} đã giao thành công!',
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
            shipperProvider.errorMessage ?? 'Xác nhận giao hàng thất bại',
          ),
          backgroundColor: AppColors.danger,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final statusStyle = ShipperStatusStyle.fromOrder(widget.order);
    final hint = _pendingRequirementHint;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text(
          'Xác nhận giao hàng',
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
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
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
                              'Xác nhận giao thành công',
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
              // Order Summary Card
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
                            'Đơn hàng #${widget.order.orderNumber}',
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
                              color: statusStyle.background,
                              borderRadius: BorderRadius.circular(20),
                              border: Border.all(
                                color: statusStyle.foreground.withValues(
                                  alpha: 0.4,
                                ),
                              ),
                            ),
                            child: Text(
                              statusStyle.label,
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                                color: statusStyle.foreground,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const Divider(height: 22),
                      Row(
                        children: [
                          const Icon(
                            Icons.person_outline_rounded,
                            size: 18,
                            color: AppColors.textMuted,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              widget.order.customerName,
                              style: const TextStyle(
                                fontWeight: FontWeight.w600,
                                fontSize: 14,
                                color: AppColors.textMain,
                              ),
                            ),
                          ),
                        ],
                      ),
                      if (widget.order.customerPhone.isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Row(
                          children: [
                            const Icon(
                              Icons.phone_outlined,
                              size: 18,
                              color: AppColors.textMuted,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                widget.order.customerPhone,
                                style: const TextStyle(
                                  color: AppColors.textMuted,
                                  fontSize: 14,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                      const SizedBox(height: 6),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(
                            Icons.location_on_outlined,
                            size: 18,
                            color: AppColors.textMuted,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              widget.order.displayShippingAddress,
                              style: const TextStyle(
                                color: AppColors.textMuted,
                                fontSize: 13,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: widget.order.isCod
                              ? AppColors.warningLight
                              : AppColors.successLight,
                          borderRadius: BorderRadius.circular(
                            ShipperLayout.innerRadius,
                          ),
                        ),
                        child: Wrap(
                          alignment: WrapAlignment.spaceBetween,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          spacing: 12,
                          runSpacing: 6,
                          children: [
                            Text(
                              widget.order.isCod
                                  ? 'Thu tiền mặt (COD):'
                                  : 'Đã thanh toán Online:',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: widget.order.isCod
                                    ? AppColors.warning
                                    : AppColors.success,
                              ),
                            ),
                            Text(
                              formatCurrency(
                                widget.order.isCod
                                    ? widget.order.codAmount
                                    : widget.order.totalAmount,
                              ),
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                color: widget.order.isCod
                                    ? AppColors.warning
                                    : AppColors.success,
                              ),
                            ),
                          ],
                        ),
                      ),
                      if (_requiresCodConfirmation) ...[
                        const SizedBox(height: 10),
                        Material(
                          color: _codConfirmed
                              ? AppColors.successLight
                              : AppColors.warningLight,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(
                              ShipperLayout.innerRadius,
                            ),
                            side: BorderSide(
                              color: _codConfirmed
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
                            title: Text(
                              'Đã thu đủ ${formatCurrency(widget.order.codAmount)} tiền mặt',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 14,
                                color: _codConfirmed
                                    ? AppColors.success
                                    : AppColors.textMain,
                              ),
                            ),
                            subtitle: const Text(
                              'Bắt buộc xác nhận đối với đơn thu hộ COD',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.textMuted,
                              ),
                            ),
                            value: _codConfirmed,
                            onChanged: _isSubmitting
                                ? null
                                : (value) => setState(
                                    () => _codConfirmed = value ?? false,
                                  ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),

              if (widget.order.items.isNotEmpty ||
                  widget.order.itemsSummary.isNotEmpty)
                Card(
                  color: AppColors.card,
                  elevation: 0,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(
                      ShipperLayout.cardRadius,
                    ),
                    side: const BorderSide(color: AppColors.border),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Kiểm tra sản phẩm',
                          style: Theme.of(context).textTheme.titleMedium
                              ?.copyWith(fontWeight: FontWeight.bold),
                        ),
                        OrderItemsSection(order: widget.order),
                      ],
                    ),
                  ),
                ),

              // Proof of Delivery Photo Card
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
                              'Ảnh bằng chứng giao hàng *',
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
                        'Chụp kiện hàng khi trao cho khách hoặc tại vị trí giao.',
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
                                    'Nhấn để chụp ảnh giao hàng',
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
                      LayoutBuilder(
                        builder: (context, constraints) {
                          final twoCols = constraints.maxWidth >= 280;
                          final cameraBtn = OutlinedButton.icon(
                            onPressed: () => _pickImage(ImageSource.camera),
                            icon: const Icon(Icons.photo_camera, size: 18),
                            label: const Text('Chụp ảnh'),
                            style: OutlinedButton.styleFrom(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 14,
                                vertical: 12,
                              ),
                              foregroundColor: AppColors.primary,
                              side: const BorderSide(color: AppColors.primary),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(10),
                              ),
                            ),
                          );
                          final galleryBtn = OutlinedButton.icon(
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
                          );
                          if (twoCols) {
                            return Row(
                              children: [
                                Expanded(child: cameraBtn),
                                const SizedBox(width: 10),
                                Expanded(child: galleryBtn),
                              ],
                            );
                          }
                          return Wrap(
                            spacing: 10,
                            runSpacing: 8,
                            children: [cameraBtn, galleryBtn],
                          );
                        },
                      ),
                    ],
                  ),
                ),
              ),

              // Shipper Notes Card
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
                            Icons.edit_note_rounded,
                            color: AppColors.primary,
                            size: 20,
                          ),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Ghi chú giao hàng',
                              style: TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _notesController,
                        maxLength: 500,
                        maxLines: 3,
                        decoration: InputDecoration(
                          hintText:
                              'Ví dụ: Đã giao tận tay khách, người nhận đã ký tên, gửi phòng lễ tân…',
                          hintStyle: const TextStyle(
                            fontSize: 13,
                            color: AppColors.textMuted,
                          ),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide: const BorderSide(
                              color: AppColors.border,
                            ),
                          ),
                          filled: true,
                          fillColor: AppColors.background,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 12),
            ],
          ),
        ),
      ),
    );
  }
}
