class BrandItem {
  final int id;
  final String name;
  final String slug;
  final String? logoUrl;
  final String? bannerUrl;
  final String? description;
  final bool isFeatured;

  BrandItem({
    required this.id,
    required this.name,
    required this.slug,
    this.logoUrl,
    this.bannerUrl,
    this.description,
    this.isFeatured = false,
  });

  factory BrandItem.fromJson(Map<String, dynamic> json) {
    return BrandItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '',
      slug: json['slug']?.toString() ?? '',
      logoUrl: json['logoUrl']?.toString() ??
          json['logo_url']?.toString() ??
          json['logo']?.toString() ??
          json['imageUrl']?.toString(),
      bannerUrl: json['bannerUrl']?.toString() ?? json['banner_url']?.toString(),
      description: json['description']?.toString(),
      isFeatured: json['isFeatured'] == true || json['is_official'] == true,
    );
  }
}
