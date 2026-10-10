class BannerItem {
  final int id;
  final String title;
  final String imageUrl;
  final String? targetUrl;
  final String? position;
  final int displayOrder;
  final bool isActive;

  BannerItem({
    required this.id,
    required this.title,
    required this.imageUrl,
    this.targetUrl,
    this.position,
    this.displayOrder = 0,
    this.isActive = true,
  });

  factory BannerItem.fromJson(Map<String, dynamic> json) {
    return BannerItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      title: json['title']?.toString() ?? '',
      imageUrl: json['imageUrl']?.toString() ?? json['image']?.toString() ?? '',
      targetUrl: json['targetUrl']?.toString(),
      position: json['position']?.toString(),
      displayOrder: (json['displayOrder'] as num?)?.toInt() ?? 0,
      isActive: json['isActive'] != false,
    );
  }
}
