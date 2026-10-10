import 'dart:async';
import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:beautyshop_mobile/core/theme/app_colors.dart';

class HasakiBannerCarousel extends StatefulWidget {
  final List<String> banners;
  final Function(int index)? onBannerTap;

  const HasakiBannerCarousel({
    Key? key,
    required this.banners,
    this.onBannerTap,
  }) : super(key: key);

  @override
  State<HasakiBannerCarousel> createState() => _HasakiBannerCarouselState();
}

class _HasakiBannerCarouselState extends State<HasakiBannerCarousel> {
  late final PageController _pageController;
  int _currentIndex = 0;
  Timer? _timer;

  final List<String> _defaultBanners = const [
    'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=1000&q=80',
    'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=1000&q=80',
    'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=1000&q=80',
  ];

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
    _startTimer();
  }

  void _startTimer() {
    _timer = Timer.periodic(const Duration(seconds: 4), (timer) {
      final list = widget.banners.isNotEmpty ? widget.banners : _defaultBanners;
      if (_pageController.hasClients && list.isNotEmpty) {
        final nextPage = (_currentIndex + 1) % list.length;
        _pageController.animateToPage(
          nextPage,
          duration: const Duration(milliseconds: 400),
          curve: Curves.easeInOut,
        );
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final list = widget.banners.isNotEmpty ? widget.banners : _defaultBanners;

    return AspectRatio(
      aspectRatio: 2.3,
      child: Stack(
        children: [
          PageView.builder(
            controller: _pageController,
            onPageChanged: (index) {
              setState(() => _currentIndex = index);
            },
            itemCount: list.length,
            itemBuilder: (context, index) {
              return GestureDetector(
                onTap: () => widget.onBannerTap?.call(index),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: CachedNetworkImage(
                      imageUrl: list[index],
                      fit: BoxFit.cover,
                      placeholder: (_, __) => Container(color: HasakiColors.primaryLight),
                      errorWidget: (_, __, ___) => Container(
                        color: HasakiColors.primary,
                        child: const Center(
                          child: Icon(Icons.spa, color: Colors.white, size: 40),
                        ),
                      ),
                    ),
                  ),
                ),
              );
            },
          ),

          // Indicator Dots
          Positioned(
            bottom: 8,
            left: 0,
            right: 0,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                list.length,
                (index) => Container(
                  width: _currentIndex == index ? 16 : 6,
                  height: 5,
                  margin: const EdgeInsets.symmetric(horizontal: 2.5),
                  decoration: BoxDecoration(
                    color: _currentIndex == index
                        ? HasakiColors.primary
                        : Colors.white.withOpacity(0.65),
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
