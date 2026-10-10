import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';
import 'package:beautyshop_mobile/core/theme/app_typography.dart';
import 'package:beautyshop_mobile/core/utils/formatters.dart';
import 'package:beautyshop_mobile/providers/spa_provider.dart';
import 'package:beautyshop_mobile/data/models/spa_model.dart';
import 'package:beautyshop_mobile/presentation/screens/spa/spa_booking_screen.dart';

class SpaScreen extends StatelessWidget {
  const SpaScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: HasakiColors.canvas,
      appBar: AppBar(
        title: const Text('BeautyShop Spa & Thư Giãn'),
        backgroundColor: HasakiColors.primary,
        elevation: 0,
      ),
      body: Consumer<SpaProvider>(
        builder: (context, provider, _) {
          return Column(
            children: [
              // 1. Spa Guarantee Header Banner
              Container(
                color: HasakiColors.primaryDark,
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                child: const Row(
                  children: [
                    Icon(Icons.spa_outlined, color: HasakiColors.gold, size: 20),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Chuyên viên thẩm mỹ tận tâm & tư vấn chăm sóc da chuyên sâu',
                        style: TextStyle(color: Colors.white, fontSize: 11.5, fontWeight: FontWeight.w600),
                      ),
                    ),
                  ],
                ),
              ),

              // 2. Filter Category Chips
              Container(
                color: Colors.white,
                height: 48,
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  scrollDirection: Axis.horizontal,
                  itemCount: provider.categories.length,
                  separatorBuilder: (_, __) => const SizedBox(width: 8),
                  itemBuilder: (context, index) {
                    final cat = provider.categories[index];
                    final isSelected = provider.selectedCategory == cat;

                    return ChoiceChip(
                      label: Text(cat),
                      selected: isSelected,
                      onSelected: (_) => provider.setCategory(cat),
                      selectedColor: HasakiColors.primaryLight,
                      backgroundColor: HasakiColors.canvas,
                      labelStyle: TextStyle(
                        fontSize: 12,
                        fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                        color: isSelected ? HasakiColors.primary : HasakiColors.textMain,
                      ),
                      side: BorderSide(
                        color: isSelected ? HasakiColors.primary : HasakiColors.border,
                      ),
                    );
                  },
                ),
              ),

              // 3. Spa Services List
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: provider.filteredServices.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final service = provider.filteredServices[index];
                    return _buildServiceCard(context, service);
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildServiceCard(BuildContext context, SpaService service) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: HasakiColors.borderSubtle),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.03),
            blurRadius: 4,
            offset: const Offset(0, 1),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Service Image & Badges
          Stack(
            children: [
              ClipRRect(
                borderRadius: const BorderRadius.vertical(top: Radius.circular(10)),
                child: AspectRatio(
                  aspectRatio: 2.2,
                  child: service.thumbnailUrl != null
                      ? CachedNetworkImage(
                          imageUrl: service.thumbnailUrl!,
                          fit: BoxFit.cover,
                          errorWidget: (_, __, ___) => Container(color: HasakiColors.primaryLight),
                        )
                      : Container(color: HasakiColors.primaryLight),
                ),
              ),
              Positioned(
                top: 8,
                left: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.black.withOpacity(0.75),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.schedule, color: Colors.white, size: 12),
                      const SizedBox(width: 4),
                      Text(
                        '${service.durationMinutes} Phút',
                        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),

          Padding(
            padding: const EdgeInsets.all(12.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  service.name,
                  style: HasakiTypography.titleMedium.copyWith(
                    fontWeight: FontWeight.w800,
                    color: HasakiColors.textMain,
                  ),
                ),
                if (service.description != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    service.description!,
                    style: HasakiTypography.bodySmall,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
                const SizedBox(height: 10),

                // Price & Booking Action
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          HasakiFormatters.formatCurrency(service.price),
                          style: HasakiTypography.priceLarge.copyWith(
                            color: HasakiColors.dealRed,
                            fontSize: 16,
                          ),
                        ),
                        if (service.originalPrice != null)
                          Text(
                            HasakiFormatters.formatCurrency(service.originalPrice),
                            style: HasakiTypography.priceStrikethrough,
                          ),
                      ],
                    ),
                    ElevatedButton.icon(
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => SpaBookingScreen(service: service),
                          ),
                        );
                      },
                      icon: const Icon(Icons.calendar_month, size: 16),
                      label: const Text('Đặt Lịch Hẹn'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: HasakiColors.primary,
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
